'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Tag } from '@/lib/types';
import '@/lib/i18n';

export default function TagsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingTag, setEditingTag] = useState<Tag | null>(null);
  const [formData, setFormData] = useState({ name: '', color: '#3b82f6' });

  const employee = getStoredEmployee();
  const membership = getCurrentMembership(employee);
  const isManager = isManagerRole(membership?.role);

  useEffect(() => {
    if (!isManager) {
      router.push('/');
      return;
    }

    loadTags();
  }, [isManager, router]);

  const loadTags = async () => {
    try {
      const data = await api.tags();
      setTags(data);
    } catch (error) {
      console.error('Failed to load tags:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    try {
      if (editingTag) {
        await api.updateTag(editingTag.id, formData);
      } else {
        await api.createTag(formData);
      }
      setShowForm(false);
      setEditingTag(null);
      setFormData({ name: '', color: '#3b82f6' });
      loadTags();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to save tag');
    }
  };

  const handleEdit = (tag: Tag) => {
    setEditingTag(tag);
    setFormData({ name: tag.name, color: tag.color || '#3b82f6' });
    setShowForm(true);
  };

  const handleDelete = async (id: number) => {
    if (!confirm(t('tags.delete') + '?')) return;

    try {
      await api.deleteTag(id);
      loadTags();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to delete tag');
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingTag(null);
    setFormData({ name: '', color: '#3b82f6' });
  };

  if (loading) {
    return (
      <DashboardLayout>
        <div className="text-center py-8">Loading...</div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{t('tags.title')}</h2>
        {!showForm && (
          <button
            onClick={() => setShowForm(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            {t('tags.add')}
          </button>
        )}
      </div>

      {/* Form */}
      {showForm && (
        <div className="bg-white p-6 rounded-lg shadow mb-6">
          <h3 className="text-lg font-semibold mb-4">
            {editingTag ? t('tags.edit') : t('tags.add')}
          </h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('tags.name')}
              </label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                required
                autoFocus
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('tags.color')}
              </label>
              <input
                type="color"
                value={formData.color}
                onChange={(e) => setFormData({ ...formData, color: e.target.value })}
                className="w-20 h-10 border border-gray-300 rounded cursor-pointer"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="submit"
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                {t('tasks.save')}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                className="px-4 py-2 bg-gray-300 text-gray-700 rounded hover:bg-gray-400"
              >
                {t('tasks.close')}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Tags List */}
      {tags.length === 0 ? (
        <div className="text-center py-8 text-gray-500">{t('tags.empty')}</div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {tags.map((tag) => (
            <div
              key={tag.id}
              className="bg-white p-4 rounded-lg shadow border border-gray-200 flex items-center justify-between"
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-8 h-8 rounded"
                  style={{ backgroundColor: tag.color || '#e5e7eb' }}
                />
                <span className="font-medium text-gray-900">{tag.name}</span>
              </div>

              <div className="flex gap-2">
                <button
                  onClick={() => handleEdit(tag)}
                  className="text-blue-600 hover:text-blue-700 text-sm"
                >
                  {t('tags.edit')}
                </button>
                <button
                  onClick={() => handleDelete(tag.id)}
                  className="text-red-600 hover:text-red-700 text-sm"
                >
                  {t('tags.delete')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </DashboardLayout>
  );
}
