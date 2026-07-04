'use client';

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { TaskComment, CommentRequest } from '@/lib/types';
import MarkdownRenderer from './MarkdownRenderer';
import '@/lib/i18n';

interface TaskCommentSectionProps {
  taskId: number;
}

export default function TaskCommentSection({ taskId }: TaskCommentSectionProps) {
  const { t } = useTranslation();
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [loading, setLoading] = useState(true);
  const [newComment, setNewComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editContent, setEditContent] = useState('');

  // Tab state for new comment
  const [newCommentTab, setNewCommentTab] = useState<'write' | 'preview'>('write');
  // Tab state for edit (per comment)
  const [editTab, setEditTab] = useState<'write' | 'preview'>('write');

  const currentEmployee = getStoredEmployee();

  useEffect(() => {
    loadComments();
  }, [taskId]);

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
      const request: CommentRequest = { content: newComment.trim() };
      const comment = await api.addComment(taskId, request);
      setComments([...comments, comment]);
      setNewComment('');
      setNewCommentTab('write');
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to add comment');
    } finally {
      setSubmitting(false);
    }
  };

  const handleEdit = (comment: TaskComment) => {
    setEditingId(comment.id);
    setEditContent(comment.content);
    setEditTab('write');
  };

  const handleSaveEdit = async (commentId: number) => {
    if (!editContent.trim()) return;

    try {
      const request: CommentRequest = { content: editContent.trim() };
      const updated = await api.updateComment(commentId, request);
      setComments(comments.map((c) => (c.id === commentId ? updated : c)));
      setEditingId(null);
      setEditContent('');
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to update comment');
    }
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setEditContent('');
    setEditTab('write');
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

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffMins < 1440) return `${Math.floor(diffMins / 60)}h ago`;
    return date.toLocaleDateString();
  };

  if (loading) {
    return <div className="text-center py-4">Loading comments...</div>;
  }

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-gray-900">
        {t('comments.title')} ({comments.length})
      </h3>

      {/* Comment List */}
      <div className="space-y-3 max-h-96 overflow-y-auto">
        {comments.length === 0 ? (
          <p className="text-gray-500 text-sm">{t('comments.empty')}</p>
        ) : (
          comments.map((comment) => (
            <div key={comment.id} className="bg-gray-50 rounded-lg p-3">
              <div className="flex items-start justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-gray-900">{comment.authorName}</span>
                  <span className="text-xs text-gray-500">
                    {formatTimestamp(comment.createdAt)}
                    {comment.updatedAt && (
                      <span className="ml-1">({t('comments.updated')})</span>
                    )}
                  </span>
                </div>

                {currentEmployee?.id === comment.authorId && (
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleEdit(comment)}
                      className="text-blue-600 hover:text-blue-700 text-sm"
                    >
                      {t('comments.edit')}
                    </button>
                    <button
                      onClick={() => handleDelete(comment.id)}
                      className="text-red-600 hover:text-red-700 text-sm"
                    >
                      {t('comments.delete')}
                    </button>
                  </div>
                )}
              </div>

              {editingId === comment.id ? (
                <div className="space-y-2">
                  {/* Edit Tabs */}
                  <div className="flex border-b border-gray-300">
                    <button
                      onClick={() => setEditTab('write')}
                      className={`px-4 py-2 text-sm font-medium ${
                        editTab === 'write'
                          ? 'border-b-2 border-blue-600 text-blue-600'
                          : 'text-gray-600 hover:text-gray-900'
                      }`}
                    >
                      Write
                    </button>
                    <button
                      onClick={() => setEditTab('preview')}
                      className={`px-4 py-2 text-sm font-medium ${
                        editTab === 'preview'
                          ? 'border-b-2 border-blue-600 text-blue-600'
                          : 'text-gray-600 hover:text-gray-900'
                      }`}
                    >
                      Preview
                    </button>
                  </div>

                  {editTab === 'write' ? (
                    <textarea
                      value={editContent}
                      onChange={(e) => setEditContent(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-md resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
                      rows={5}
                      placeholder="**Bold** *italic* `code` [link](url) @username"
                    />
                  ) : (
                    <div className="min-h-[120px] p-3 border border-gray-300 rounded-md bg-white">
                      {editContent.trim() ? (
                        <MarkdownRenderer content={editContent} />
                      ) : (
                        <p className="text-gray-400 text-sm">Nothing to preview</p>
                      )}
                    </div>
                  )}

                  <div className="flex gap-2">
                    <button
                      onClick={() => handleSaveEdit(comment.id)}
                      className="px-3 py-1 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
                    >
                      {t('comments.save')}
                    </button>
                    <button
                      onClick={handleCancelEdit}
                      className="px-3 py-1 bg-gray-300 text-gray-700 rounded text-sm hover:bg-gray-400"
                    >
                      {t('comments.cancel')}
                    </button>
                  </div>
                </div>
              ) : (
                <div className="prose prose-sm max-w-none">
                  <MarkdownRenderer content={comment.content} />
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* Add Comment Form */}
      <form onSubmit={handleSubmit} className="space-y-2">
        {/* Tabs */}
        <div className="flex border-b border-gray-300">
          <button
            type="button"
            onClick={() => setNewCommentTab('write')}
            className={`px-4 py-2 text-sm font-medium ${
              newCommentTab === 'write'
                ? 'border-b-2 border-blue-600 text-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Write
          </button>
          <button
            type="button"
            onClick={() => setNewCommentTab('preview')}
            className={`px-4 py-2 text-sm font-medium ${
              newCommentTab === 'preview'
                ? 'border-b-2 border-blue-600 text-blue-600'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            Preview
          </button>
        </div>

        {newCommentTab === 'write' ? (
          <textarea
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            placeholder={t('comments.addPlaceholder') + '\n\nMarkdown supported: **bold** *italic* `code` [link](url) @username'}
            className="w-full px-3 py-2 border border-gray-300 rounded-md resize-none focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
            rows={5}
            disabled={submitting}
          />
        ) : (
          <div className="min-h-[120px] p-3 border border-gray-300 rounded-md bg-gray-50">
            {newComment.trim() ? (
              <MarkdownRenderer content={newComment} />
            ) : (
              <p className="text-gray-400 text-sm">Nothing to preview</p>
            )}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting || !newComment.trim()}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? '...' : t('comments.submit')}
        </button>
      </form>

      {/* Markdown Help */}
      <details className="text-xs text-gray-600">
        <summary className="cursor-pointer hover:text-gray-900">Markdown help</summary>
        <div className="mt-2 space-y-1 pl-4">
          <p><code>**bold**</code> → <strong>bold</strong></p>
          <p><code>*italic*</code> → <em>italic</em></p>
          <p><code>`code`</code> → <code className="bg-gray-100 px-1">code</code></p>
          <p><code>[link](url)</code> → link</p>
          <p><code>- list item</code> → bullet list</p>
          <p><code>1. numbered</code> → numbered list</p>
          <p><code>@username</code> → mention user</p>
          <p><code>&gt; quote</code> → blockquote</p>
        </div>
      </details>
    </div>
  );
}
