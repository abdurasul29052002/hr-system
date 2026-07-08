'use client';

import { useState, useEffect, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { Member, TaskComment, CommentRequest } from '@/lib/types';
import MarkdownRenderer from './MarkdownRenderer';
import { Avatar } from './ui';
import '@/lib/i18n';

interface TaskCommentSectionProps {
  taskId: number;
  members: Member[];
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
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
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
    if (!newComment.trim()) return;

    setSubmitting(true);
    try {
      const comment = await api.addComment(taskId, newComment.trim(), selectedFiles);
      setComments([...comments, comment]);
      setNewComment('');
      setSelectedFiles([]);
      setNewCommentTab('write');
      setNewMention(CLOSED_MENTION);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to add comment');
    } finally {
      setSubmitting(false);
    }
  };

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || []);
    const validFiles = files.filter((file) => {
      if (file.size > 5 * 1024 * 1024) {
        alert(`${file.name}: File too large (max 5MB)`);
        return false;
      }
      return true;
    });
    setSelectedFiles((prev) => [...prev, ...validFiles]);
  };

  const removeFile = (index: number) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
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
    filtered: Member[],
    applyMention: (member: Member) => void,
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
    member: Member,
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
                        {comment.attachments.map((attachment) => (
                          <a
                            key={attachment.id}
                            href={attachment.downloadUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className={`block overflow-hidden rounded-lg border ${mine ? 'border-brand-200 hover:border-brand-300' : 'border-slate-200 hover:border-slate-300'}`}
                          >
                            {attachment.mimeType.startsWith('image/') ? (
                              <img src={attachment.downloadUrl} alt={attachment.fileName} className="h-28 w-full object-cover" />
                            ) : (
                              <div className="flex items-center gap-2 p-2">
                                <svg className="h-7 w-7 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                </svg>
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-medium text-slate-700">{attachment.fileName}</p>
                                  <p className="text-[11px] text-slate-500">{(attachment.fileSize / 1024).toFixed(1)} KB</p>
                                </div>
                              </div>
                            )}
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className={`mt-0.5 flex items-center gap-2 px-1 ${mine ? 'flex-row-reverse' : ''}`}>
                    <span className="text-[11px] text-slate-400" title={new Date(comment.createdAt).toLocaleString()}>
                      {formatTime(comment.createdAt)}
                      {comment.updatedAt && <span className="ml-1 italic">({t('comments.updated')})</span>}
                    </span>
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
            <input ref={fileInputRef} type="file" onChange={handleFileSelect} multiple accept="image/*" className="hidden" id="comment-file-input" />
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

          {selectedFiles.length > 0 && (
            <div className="flex flex-wrap gap-2 px-3 pb-2">
              {selectedFiles.map((file, index) => (
                <span key={index} className="flex items-center gap-1.5 rounded-lg bg-brand-50 px-2 py-1 text-xs text-brand-700">
                  <span className="max-w-[150px] truncate">{file.name}</span>
                  <button type="button" onClick={() => removeFile(index)} className="text-brand-400 hover:text-red-600">×</button>
                </span>
              ))}
            </div>
          )}

          <div className="flex items-center justify-between border-t border-slate-100 px-3 py-1.5">
            <span className="text-[11px] text-slate-400">{t('comments.markdownHint')}</span>
            <button
              type="submit"
              disabled={submitting || !newComment.trim()}
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
    </div>
  );
}

function filterMentionable(members: Member[], query: string) {
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

function MentionDropdown({ members, activeIndex, onPick }: { members: Member[]; activeIndex: number; onPick: (member: Member) => void }) {
  return (
    <div className="absolute bottom-full left-3 right-3 z-30 mb-1 max-h-56 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-xl">
      {members.map((m, idx) => (
        <button
          key={m.employeeId}
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
