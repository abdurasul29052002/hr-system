'use client';

import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { api, uploadDirect } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { MentionMember, TaskComment, CommentRequest } from '@/lib/types';
import MarkdownRenderer from './MarkdownRenderer';
import ImageLightbox, { type LightboxMedia } from './ImageLightbox';
import { Avatar } from './ui';
import '@/lib/i18n';

// Mirror the server's multipart limits (application.yml: max-file-size 100MB, max-request-size 220MB) so
// an oversized batch is caught here with a clear message instead of failing the whole upload with a 413.
const MAX_ATTACHMENT_BYTES = 100 * 1024 * 1024;
const MAX_TOTAL_BYTES = 200 * 1024 * 1024;

/**
 * A not-yet-sent attachment. Telegram-style: the upload starts the moment the file is attached, so by the
 * time you finish typing a 100MB screen recording is usually already in S3 and sending is instant.
 * `key` is filled in once the upload completes; `progress` drives the ring over the thumbnail.
 */
type PendingAttachment = {
  id: string;
  file: File;
  preview: string;
  progress: number;
  key?: string;
  error?: string;
  /** Lets removing a row cancel its still-running upload instead of leaving it to finish unseen. */
  abort?: AbortController;
};

interface TaskCommentSectionProps {
  taskId: number;
  members: MentionMember[];
}

type MentionState = {
  open: boolean;
  query: string;
  start: number;
  end: number;
  activeIndex: number;
};

const CLOSED_MENTION: MentionState = { open: false, query: '', start: -1, end: -1, activeIndex: 0 };

export default function TaskCommentSection({ taskId, members }: TaskCommentSectionProps) {
  const { t } = useTranslation();
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState('');
  const [pending, setPending] = useState<PendingAttachment[]>([]);
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pendingRef = useRef<PendingAttachment[]>([]);
  const endRef = useRef<HTMLDivElement>(null);
  const newTextareaRef = useRef<HTMLTextAreaElement>(null);
  const editTextareaRef = useRef<HTMLTextAreaElement>(null);

  const [newCommentTab, setNewCommentTab] = useState<'write' | 'preview'>('write');
  const [editTab, setEditTab] = useState<'write' | 'preview'>('write');
  const [newMention, setNewMention] = useState<MentionState>(CLOSED_MENTION);
  const [editMention, setEditMention] = useState<MentionState>(CLOSED_MENTION);

  const currentEmployee = getStoredEmployee();

  useEffect(() => {
    loadComments();
  }, [taskId]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'nearest' });
  }, [comments.length]);

  // Object URLs are created/revoked alongside the files themselves (not in a follow-up effect) so a
  // thumbnail is never one render out of step with its file. This ref only backs the unmount sweep.
  useEffect(() => {
    pendingRef.current = pending;
  }, [pending]);
  useEffect(() => () => pendingRef.current.forEach((p) => p.preview && URL.revokeObjectURL(p.preview)), []);

  // Every image and video in the thread, in reading order — the lightbox arrows walk this whole gallery.
  const threadImages = useMemo<LightboxMedia[]>(
    () => comments.flatMap((comment) =>
      (comment.attachments ?? [])
        .filter((a) => a.mimeType.startsWith('image/') || a.mimeType.startsWith('video/'))
        .map((attachment) => ({
          id: attachment.id,
          url: attachment.downloadUrl,
          fileName: attachment.fileName,
          isVideo: attachment.mimeType.startsWith('video/'),
        }))),
    [comments],
  );

  const mentionableMembers = useMemo(
    () => members.slice().sort((a, b) => a.fullName.localeCompare(b.fullName)),
    [members],
  );

  const filteredNewMembers = useMemo(
    () => filterMentionable(mentionableMembers, newMention.query),
    [mentionableMembers, newMention.query],
  );
  const filteredEditMembers = useMemo(
    () => filterMentionable(mentionableMembers, editMention.query),
    [mentionableMembers, editMention.query],
  );

  const loadComments = async () => {
    try {
      const data = await api.listComments(taskId);
      setComments(data);
    } catch (error) {
      console.error('Failed to load comments:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // A screenshot on its own is a valid comment — only require text when nothing is attached.
    if (submitting || (!newComment.trim() && pending.length === 0)) return;

    setSubmitting(true);
    const sent = pending;
    try {
      // Upload attachments NOW, on send (Telegram-style) — each one drives its own progress ring, and the
      // comment is only posted once every file is in S3. Uploads run in parallel.
      const uploaded = await Promise.all(
        sent.map(async (item) => {
          const patch = (changes: Partial<PendingAttachment>) =>
            setPending((prev) => prev.map((p) => (p.id === item.id ? { ...p, ...changes } : p)));
          patch({ progress: 0, error: undefined });
          const key = await uploadDirect(item.file, (percent) => patch({ progress: percent }), item.abort?.signal);
          patch({ key, progress: 100 });
          return { key, fileName: item.file.name };
        }),
      );
      const comment = await api.addComment(taskId, newComment.trim(), uploaded);
      setComments((prev) => [...prev, comment]);
      setNewComment('');
      sent.forEach((p) => p.preview && URL.revokeObjectURL(p.preview));
      setPending((prev) => prev.filter((p) => !sent.includes(p)));
      setNewCommentTab('write');
      setNewMention(CLOSED_MENTION);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (error) {
      // An upload or the post itself failed — keep the composer and its attachments so the user can retry.
      alert(error instanceof Error ? error.message : t('comments.uploadFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const addFiles = (files: File[]) => {
    // Validation, alerts and object-URL creation stay OUT of the state updater — updaters must be pure
    // (React re-invokes them, which would double the alerts and leak a duplicate URL per file).
    let total = pending.reduce((sum, p) => sum + p.file.size, 0);
    const accepted: PendingAttachment[] = [];
    for (const file of files) {
      if (file.size > MAX_ATTACHMENT_BYTES) {
        alert(t('comments.fileTooLarge', { name: file.name }));
        continue;
      }
      if (total + file.size > MAX_TOTAL_BYTES) {
        alert(t('comments.totalTooLarge'));
        break;
      }
      total += file.size;
      accepted.push({
        id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        file,
        preview: file.type.startsWith('image/') ? URL.createObjectURL(file) : '',
        progress: 0,
        abort: new AbortController(),
      });
    }
    // Attachments are NOT uploaded here — the upload starts when the user presses Send (see handleSubmit),
    // Telegram-style, so nothing is transferred until they actually commit to the message.
    if (accepted.length === 0) return;
    setPending((prev) => [...prev, ...accepted]);
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    addFiles(Array.from(e.target.files || []));
    // Clear the input so re-picking the SAME file (e.g. after removing it) still fires a change event.
    e.target.value = '';
  };

  /**
   * Ctrl/Cmd+V a screenshot straight into the composer — the clipboard carries it as an image file.
   * We never preventDefault: rich copies (Excel/Word/Outlook) put BOTH text and a bitmap on the clipboard,
   * and swallowing the paste would throw the user's text away. When text is present we let the normal paste
   * happen and ignore the bitmap; a screenshot carries no text, which is exactly the case we attach.
   */
  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    if (e.clipboardData.getData('text/plain').trim().length > 0) return;
    const images: File[] = [];
    for (const item of Array.from(e.clipboardData.items)) {
      if (item.kind === 'file' && item.type.startsWith('image/')) {
        const file = item.getAsFile();
        if (file) images.push(file);
      }
    }
    if (images.length === 0) return;
    const stamp = Date.now();
    addFiles(images.map((file, i) => {
      const ext = file.type.split('/')[1]?.replace(/[^a-z0-9]/gi, '') || 'png';
      // Clipboard images all arrive named "image.png" — stamp them so repeated pastes stay distinguishable.
      const suffix = images.length > 1 ? `-${i + 1}` : '';
      return new File([file], `pasted-${stamp}${suffix}.${ext}`, { type: file.type });
    }));
  };

  const removeFile = (index: number) => {
    const target = pending[index];
    target?.abort?.abort();
    if (target?.preview) URL.revokeObjectURL(target.preview);
    setPending((prev) => prev.filter((_, i) => i !== index));
  };

  const handleEdit = (comment: TaskComment) => {
    setEditingId(comment.id);
    setEditContent(comment.content);
    setEditTab('write');
    setEditMention(CLOSED_MENTION);
  };

  const handleSaveEdit = async (commentId: number) => {
    if (!editContent.trim()) return;
    try {
      const request: CommentRequest = { content: editContent.trim() };
      const updated = await api.updateComment(commentId, request);
      setComments(comments.map((c) => (c.id === commentId ? updated : c)));
      setEditingId(null);
      setEditContent('');
      setEditMention(CLOSED_MENTION);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to update comment');
    }
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setEditContent('');
    setEditTab('write');
    setEditMention(CLOSED_MENTION);
  };

  const handleDelete = async (commentId: number) => {
    if (!confirm(t('comments.delete') + '?')) return;
    try {
      await api.deleteComment(commentId);
      setComments(comments.filter((c) => c.id !== commentId));
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete comment');
    }
  };

  const formatTime = (timestamp: string) => {
    const d = new Date(timestamp);
    const sameDay = d.toDateString() === new Date().toDateString();
    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return sameDay ? time : `${d.toLocaleDateString([], { day: '2-digit', month: 'short' })} · ${time}`;
  };

  const onTextareaChange = (
    value: string,
    setValue: (v: string) => void,
    setMention: (m: MentionState) => void,
    textarea: HTMLTextAreaElement,
  ) => {
    setValue(value);
    setMention(computeMentionState(value, textarea.selectionStart));
  };

  const handleTextareaKeyDown = (
    e: React.KeyboardEvent<HTMLTextAreaElement>,
    mention: MentionState,
    filtered: MentionMember[],
    applyMention: (member: MentionMember) => void,
    submitShortcut: () => void,
    setMention: (m: MentionState) => void,
  ) => {
    if (mention.open && filtered.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setMention({ ...mention, activeIndex: (mention.activeIndex + 1) % filtered.length });
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setMention({ ...mention, activeIndex: (mention.activeIndex - 1 + filtered.length) % filtered.length });
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        applyMention(filtered[Math.min(mention.activeIndex, filtered.length - 1)]);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setMention(CLOSED_MENTION);
        return;
      }
    }
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') submitShortcut();
  };

  const applyMention = (
    member: MentionMember,
    content: string,
    setContent: (v: string) => void,
    mention: MentionState,
    setMention: (m: MentionState) => void,
    textareaRef: React.RefObject<HTMLTextAreaElement | null>,
  ) => {
    const next = `${content.slice(0, mention.start)}@${member.username} ${content.slice(mention.end)}`;
    const caret = mention.start + member.username.length + 2;
    setContent(next);
    setMention(CLOSED_MENTION);
    requestAnimationFrame(() => {
      const el = textareaRef.current;
      if (!el) return;
      el.focus();
      el.setSelectionRange(caret, caret);
    });
  };

  return (
    <div className="space-y-3">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
        <svg className="h-4 w-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 10h8M8 14h5m-9 6l3-3h9a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v13z" />
        </svg>
        {t('comments.title')}
        {comments.length > 0 && (
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">{comments.length}</span>
        )}
      </h3>

      <div className="max-h-[24rem] space-y-3 overflow-y-auto rounded-xl bg-slate-50 p-3">
        {loading ? (
          <p className="py-6 text-center text-sm text-slate-400">{t('common.loading')}</p>
        ) : comments.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <svg className="h-8 w-8 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 10h8M8 14h5m-9 6l3-3h9a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v13z" />
            </svg>
            <p className="text-sm text-slate-400">{t('comments.empty')}</p>
          </div>
        ) : (
          comments.map((comment) => {
            const mine = currentEmployee?.id === comment.authorId;
            const editing = editingId === comment.id;

            if (editing) {
              return (
                <div key={comment.id} className="rounded-2xl border border-brand-200 bg-white p-2 shadow-sm">
                  <TabToggle tab={editTab} onChange={setEditTab} t={t} />
                  {editTab === 'write' ? (
                    <div className="relative mt-2">
                      <textarea
                        ref={editTextareaRef}
                        value={editContent}
                        onChange={(e) => onTextareaChange(e.target.value, setEditContent, setEditMention, e.target)}
                        onKeyDown={(e) => handleTextareaKeyDown(
                          e,
                          editMention,
                          filteredEditMembers,
                          (member) => applyMention(member, editContent, setEditContent, editMention, setEditMention, editTextareaRef),
                          () => handleSaveEdit(comment.id),
                          setEditMention,
                        )}
 className="w-full resize-none rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
                        rows={4}
                        placeholder="**Bold** *italic* `code` [link](url) @username"
                      />
                      {editMention.open && filteredEditMembers.length > 0 && (
                        <MentionDropdown members={filteredEditMembers} activeIndex={editMention.activeIndex} onPick={(member) => applyMention(member, editContent, setEditContent, editMention, setEditMention, editTextareaRef)} />
                      )}
                    </div>
                  ) : (
                    <div className="mt-2 min-h-[80px] rounded-lg border border-slate-200 bg-slate-50 p-3">
                      {editContent.trim() ? <MarkdownRenderer content={editContent} /> : <p className="text-sm text-slate-400">—</p>}
                    </div>
                  )}
                  <div className="mt-2 flex justify-end gap-2">
                    <button onClick={handleCancelEdit} className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-100">
                      {t('comments.cancel')}
                    </button>
                    <button
                      onClick={() => handleSaveEdit(comment.id)}
                      disabled={!editContent.trim()}
 className="rounded-lg bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
                    >
                      {t('comments.save')}
                    </button>
                  </div>
                </div>
              );
            }

            return (
              <div key={comment.id} className={`group flex items-end gap-2 ${mine ? 'flex-row-reverse' : ''}`}>
                {!mine && <Avatar name={comment.authorName} size={8} />}
                <div className={`flex min-w-0 max-w-[78%] flex-col ${mine ? 'items-end' : 'items-start'}`}>
                  {!mine && <span className="mb-0.5 px-1 text-xs font-semibold text-slate-600">{comment.authorName}</span>}
                  <div className={`relative w-fit rounded-2xl px-3.5 py-2 text-sm shadow-sm ${mine ? 'rounded-br-md bg-brand-100 text-slate-800' : 'rounded-bl-md border border-slate-200 bg-white text-slate-800'}`}>
                    <MarkdownRenderer content={comment.content} />
                    {comment.attachments && comment.attachments.length > 0 && (
                      <div className="mt-2 grid grid-cols-2 gap-2">
                        {comment.attachments.map((attachment) => {
                          const border = mine ? 'border-brand-200 hover:border-brand-300' : 'border-slate-200 hover:border-slate-300';
                          const isImage = attachment.mimeType.startsWith('image/');
                          const isVideo = attachment.mimeType.startsWith('video/');
                          if (isImage || isVideo) {
                            // Open in the in-page viewer instead of a new tab — messenger style.
                            const galleryIndex = threadImages.findIndex((media) => media.id === attachment.id);
                            return (
                              <button
                                key={attachment.id}
                                type="button"
                                onClick={() => setLightboxIndex(galleryIndex >= 0 ? galleryIndex : 0)}
                                title={attachment.fileName}
 className={`relative block cursor-zoom-in overflow-hidden rounded-lg border ${border}`}
                              >
                                {isVideo ? (
                                  <>
                                    {/* preload metadata so the browser paints a first frame as the poster */}
                                    <video src={attachment.downloadUrl} preload="metadata" muted playsInline className="h-28 w-full bg-slate-900 object-cover" />
                                    <span className="pointer-events-none absolute inset-0 flex items-center justify-center">
                                      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-black/55 text-white backdrop-blur-sm">
                                        <svg className="ml-0.5 h-4 w-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden><path d="M8 5v14l11-7z" /></svg>
                                      </span>
                                    </span>
                                  </>
                                ) : (
                                  <img src={attachment.downloadUrl} alt={attachment.fileName} className="h-28 w-full object-cover" />
                                )}
                              </button>
                            );
                          }
                          return (
                            <a
                              key={attachment.id}
                              href={attachment.downloadUrl}
                              target="_blank"
                              rel="noopener noreferrer"
 className={`block overflow-hidden rounded-lg border ${border}`}
                            >
                              <div className="flex items-center gap-2 p-2">
                                <svg className="h-7 w-7 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                </svg>
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-medium text-slate-700">{attachment.fileName}</p>
                                  <p className="text-[11px] text-slate-500">{(attachment.fileSize / 1024).toFixed(1)} KB</p>
                                </div>
                              </div>
                            </a>
                          );
                        })}
                      </div>
                    )}
                  </div>
                  <div className={`mt-0.5 flex items-center gap-2 px-1 ${mine ? 'flex-row-reverse' : ''}`}>
                    <span className="text-[11px] text-slate-400" title={new Date(comment.createdAt).toLocaleString()}>
                      {formatTime(comment.createdAt)}
                      {comment.updatedAt && <span className="ml-1 italic">({t('comments.updated')})</span>}
                    </span>
                    {comment.viaTelegram && (
                      <span className="inline-flex items-center gap-0.5 rounded-full bg-sky-50 px-1.5 py-0.5 text-[10px] font-medium text-sky-600" title={t('comments.viaTelegram')}>
                        <svg className="h-3 w-3" viewBox="0 0 24 24" fill="currentColor" aria-hidden><path d="M21.94 4.36 18.9 19.1c-.23 1.02-.84 1.27-1.7.79l-4.7-3.47-2.27 2.18c-.25.25-.46.46-.94.46l.33-4.78L18.6 5.9c.38-.34-.08-.53-.6-.19L6.55 12.9l-4.66-1.46c-1.01-.32-1.03-1.01.21-1.5l18.22-7.02c.84-.31 1.58.2 1.32 1.44Z" /></svg>
                        {t('comments.viaTelegram')}
                      </span>
                    )}
                    {mine && (
                      <span className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                        <button onClick={() => handleEdit(comment)} title={t('comments.edit')} className="rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700">
                          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                        </button>
                        <button onClick={() => handleDelete(comment.id)} title={t('comments.delete')} className="rounded p-0.5 text-slate-400 hover:bg-red-50 hover:text-red-600">
                          <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                        </button>
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
        <div ref={endRef} />
      </div>

      <form onSubmit={handleSubmit}>
        <div className="rounded-xl border border-slate-200 bg-white focus-within:border-brand-400 focus-within:ring-2 focus-within:ring-brand-500/20">
          <div className="flex items-center gap-1 border-b border-slate-100 px-2 py-1.5">
            <TabToggle tab={newCommentTab} onChange={setNewCommentTab} t={t} />
            <div className="flex-1" />
            <input ref={fileInputRef} type="file" onChange={handleFileSelect} multiple className="hidden" id="comment-file-input" />
            <label htmlFor="comment-file-input" title={t('tasks.attachImages')} className="flex cursor-pointer items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-medium text-slate-500 hover:bg-slate-100 hover:text-slate-700">
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13" />
              </svg>
              <span className="hidden sm:inline">{t('tasks.attachImages')}</span>
            </label>
          </div>

          {newCommentTab === 'write' ? (
            <div className="relative">
              <textarea
                ref={newTextareaRef}
                value={newComment}
                onChange={(e) => onTextareaChange(e.target.value, setNewComment, setNewMention, e.target)}
                onKeyDown={(e) => handleTextareaKeyDown(
                  e,
                  newMention,
                  filteredNewMembers,
                  (member) => applyMention(member, newComment, setNewComment, newMention, setNewMention, newTextareaRef),
                  () => handleSubmit(e),
                  setNewMention,
                )}
                onPaste={handlePaste}
                placeholder={t('comments.addPlaceholder')}
 className="max-h-40 min-h-[64px] w-full resize-y border-0 px-3 py-2.5 text-sm placeholder:text-slate-400 focus:outline-none focus:ring-0"
                rows={2}
                disabled={submitting}
              />
              {newMention.open && filteredNewMembers.length > 0 && (
                <MentionDropdown members={filteredNewMembers} activeIndex={newMention.activeIndex} onPick={(member) => applyMention(member, newComment, setNewComment, newMention, setNewMention, newTextareaRef)} />
              )}
            </div>
          ) : (
            <div className="min-h-[64px] px-3 py-2.5">
              {newComment.trim() ? <MarkdownRenderer content={newComment} /> : <p className="text-sm text-slate-400">—</p>}
            </div>
          )}

          {pending.length > 0 && (
            <div className="flex flex-wrap gap-2 px-3 pb-2">
              {pending.map((item, index) => {
                const { id, file, preview, progress, error } = item;
                // The ring shows only while sending; before that a file just sits there, removable.
                const uploadingThis = submitting && !error;
                const remove = !submitting && (
                  <button
                    type="button"
                    onClick={() => removeFile(index)}
                    aria-label={t('comments.removeAttachment')}
                    className="absolute -right-1.5 -top-1.5 z-10 flex h-5 w-5 items-center justify-center rounded-full bg-slate-700 text-xs leading-none text-white shadow transition-colors hover:bg-red-600"
                  >
                    ×
                  </button>
                );
                const overlay = submitting && (
                  <span className={`pointer-events-none absolute inset-0 flex items-center justify-center rounded-lg ${error ? 'bg-red-600/70' : 'bg-slate-900/45'}`}>
                    {error ? <span className="text-sm font-bold text-white">!</span> : <ProgressRing percent={progress} />}
                  </span>
                );
                return preview ? (
                  <span key={id} className="relative" title={error || file.name}>
                    <img src={preview} alt={file.name} className={`h-16 w-16 rounded-lg border border-slate-200 object-cover ${uploadingThis ? 'opacity-70' : ''}`} />
                    {overlay}
                    {remove}
                  </span>
                ) : (
                  <span key={id} className="relative flex items-center gap-1.5 rounded-lg bg-brand-50 px-2 py-1 text-xs text-brand-700" title={error || file.name}>
                    <span className="max-w-[150px] truncate">{file.name}</span>
                    {submitting ? (
                      error ? <span className="font-bold text-red-600">!</span> : <span className="tabular-nums text-brand-500">{progress}%</span>
                    ) : (
                      <button type="button" onClick={() => removeFile(index)} aria-label={t('comments.removeAttachment')} className="text-brand-400 hover:text-red-600">×</button>
                    )}
                  </span>
                );
              })}
            </div>
          )}

          <div className="flex items-center justify-between border-t border-slate-100 px-3 py-1.5">
            <span className="text-[11px] text-slate-400">{t('comments.markdownHint')}</span>
            <button
              type="submit"
              disabled={submitting || (!newComment.trim() && pending.length === 0)}
 className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 px-3.5 py-1.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
                <path d="M3.4 20.4l17.45-7.48a1 1 0 000-1.84L3.4 3.6a.993.993 0 00-1.39.91L2 9.12c0 .5.37.93.87.99L17 12 2.87 13.88c-.5.07-.87.5-.87 1l.01 4.61c0 .71.73 1.2 1.39.91z" />
              </svg>
              {submitting ? '…' : t('comments.submit')}
            </button>
          </div>
        </div>
      </form>

      {lightboxIndex !== null && lightboxIndex < threadImages.length && (
        <ImageLightbox
          images={threadImages}
          index={lightboxIndex}
          onIndexChange={setLightboxIndex}
          onClose={() => setLightboxIndex(null)}
        />
      )}
    </div>
  );
}

/**
 * Telegram-style upload indicator: a ring that fills clockwise as the percentage climbs, with a slowly
 * rotating track for a sense of activity while it works, and the number in the middle.
 */
function ProgressRing({ percent }: { percent: number }) {
  const R = 15;
  const CIRC = 2 * Math.PI * R;
  const pct = Math.max(0, Math.min(100, percent));
  return (
    <span className="relative flex h-10 w-10 items-center justify-center">
      <svg viewBox="0 0 36 36" className="absolute inset-0 h-10 w-10 animate-spin [animation-duration:2.2s]">
        <circle cx="18" cy="18" r={R} fill="none" stroke="rgba(255,255,255,0.25)" strokeWidth="3" />
      </svg>
      <svg viewBox="0 0 36 36" className="absolute inset-0 h-10 w-10 -rotate-90">
        <circle
          cx="18" cy="18" r={R} fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round"
          strokeDasharray={CIRC} strokeDashoffset={CIRC * (1 - pct / 100)}
          style={{ transition: 'stroke-dashoffset 0.2s linear' }}
        />
      </svg>
      <span className="relative text-[9px] font-bold tabular-nums text-white">{pct}</span>
    </span>
  );
}

function filterMentionable(members: MentionMember[], query: string) {
  const q = query.trim().toLowerCase();
  return members
    .filter((m) => !q || m.username.toLowerCase().includes(q) || m.fullName.toLowerCase().includes(q))
    .slice(0, 6);
}

function computeMentionState(content: string, caret: number): MentionState {
  const before = content.slice(0, caret);
  const at = before.lastIndexOf('@');
  if (at === -1) return CLOSED_MENTION;
  const chunk = before.slice(at + 1);
  if (chunk.length > 24 || /\s/.test(chunk) || /[^a-zA-Z0-9_]/.test(chunk)) return CLOSED_MENTION;
  return { open: true, query: chunk, start: at, end: caret, activeIndex: 0 };
}

function MentionDropdown({ members, activeIndex, onPick }: { members: MentionMember[]; activeIndex: number; onPick: (member: MentionMember) => void }) {
  const activeRef = useRef<HTMLButtonElement>(null);
  // Keep the keyboard-highlighted row in view when navigating past the visible area.
  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);
  return (
    <div className="absolute bottom-full left-3 right-3 z-30 mb-1 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl">
      {members.map((m, idx) => (
        <button
          key={m.employeeId}
          ref={idx === activeIndex ? activeRef : null}
          type="button"
          onClick={() => onPick(m)}
 className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm ${idx === activeIndex ? 'bg-brand-50' : 'hover:bg-slate-50'}`}
        >
          <Avatar name={m.fullName} size={6} />
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-900">{m.fullName}</p>
            <p className="truncate text-xs text-slate-400">@{m.username}</p>
          </div>
        </button>
      ))}
    </div>
  );
}

function TabToggle({
  tab,
  onChange,
  t,
}: {
  tab: 'write' | 'preview';
  onChange: (tab: 'write' | 'preview') => void;
  t: (key: string) => string;
}) {
  return (
    <div className="flex gap-0.5 rounded-lg bg-slate-100 p-0.5">
      {(['write', 'preview'] as const).map((v) => (
        <button
          key={v}
          type="button"
          onClick={() => onChange(v)}
 className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
            tab === v ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-500 hover:text-slate-700'
          }`}
        >
          {t(`comments.${v}`)}
        </button>
      ))}
    </div>
  );
}
