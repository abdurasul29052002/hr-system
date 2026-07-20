'use client';

import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { TaskAttachment } from '@/lib/types';
import '@/lib/i18n';

interface TaskAttachmentSectionProps {
  taskId: number;
}

export default function TaskAttachmentSection({ taskId }: TaskAttachmentSectionProps) {
  const { t } = useTranslation();
  const [attachments, setAttachments] = useState<TaskAttachment[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  const currentEmployee = getStoredEmployee();

  useEffect(() => {
    loadAttachments();
  }, [taskId]);

  const loadAttachments = async () => {
    try {
      const data = await api.listAttachments(taskId);
      setAttachments(data);
    } catch (error) {
      console.error('Failed to load attachments:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    // Validate file type — images and screen recordings (the reason for the 100MB limit).
    if (!file.type.startsWith('image/') && !file.type.startsWith('video/')) {
      alert('Only image and video files are allowed');
      return;
    }

    // Validate file size — mirrors spring.servlet.multipart.max-file-size (100MB).
    if (file.size > 100 * 1024 * 1024) {
      alert('File size must be less than 100MB');
      return;
    }

    setUploading(true);
    setProgress(0);
    try {
      const attachment = await api.uploadAttachment(taskId, file, setProgress);
      setAttachments([attachment, ...attachments]);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to upload file');
    } finally {
      setUploading(false);
      // Reset input
      e.target.value = '';
    }
  };

  const handleDelete = async (attachmentId: number) => {
    if (!confirm('Delete this attachment?')) return;

    try {
      await api.deleteAttachment(attachmentId);
      setAttachments(attachments.filter((a) => a.id !== attachmentId));
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete attachment');
    }
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  };

  const formatTimestamp = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  if (loading) {
    return <div className="text-center py-4">Loading attachments...</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold text-gray-900">
          Attachments ({attachments.length})
        </h3>

        {/* Upload Button */}
        <label className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed">
          {uploading ? `Uploading… ${progress}%` : '📎 Upload file'}
          <input
            type="file"
            accept="image/*,video/*"
            onChange={handleFileSelect}
            disabled={uploading}
            className="hidden"
          />
        </label>
      </div>

      {/* Attachments Grid */}
      {attachments.length === 0 ? (
        <p className="text-gray-500 text-sm">No attachments yet</p>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {attachments.map((attachment) => (
            <div key={attachment.id} className="relative group border border-gray-200 rounded-lg overflow-hidden hover:shadow-lg transition-shadow">
              {/* Preview — a <video> for recordings, which would otherwise render as a broken image */}
              {attachment.mimeType?.startsWith('video/') ? (
                <video
                  src={attachment.downloadUrl}
                  controls
                  preload="metadata"
                  playsInline
                  className="block aspect-square w-full bg-black object-contain"
                />
              ) : (
                <a
                  href={attachment.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block aspect-square bg-gray-100"
                >
                  <img
                    src={attachment.downloadUrl}
                    alt={attachment.fileName}
                    className="w-full h-full object-cover"
                  />
                </a>
              )}

              {/* Info Overlay */}
              <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-2 text-white text-xs">
                <p className="font-medium truncate">{attachment.fileName}</p>
                <p className="text-gray-300">
                  {formatFileSize(attachment.fileSize)} • {attachment.uploadedByName}
                </p>
                <p className="text-gray-400 text-xs">{formatTimestamp(attachment.uploadedAt)}</p>
              </div>

              {/* Delete Button (only for uploader) */}
              {currentEmployee?.fullName === attachment.uploadedByName && (
                <button
                  onClick={() => handleDelete(attachment.id)}
                  className="absolute top-2 right-2 bg-red-600 text-white rounded-full w-6 h-6 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-red-700"
                  title="Delete"
                >
                  ×
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Upload Info */}
      <p className="text-xs text-gray-500">
        💡 Images (JPG, PNG, GIF, WebP) and video (MP4, WebM, MOV, MKV) • Max size: 100MB
      </p>
    </div>
  );
}
