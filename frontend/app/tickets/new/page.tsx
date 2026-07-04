'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import type { TicketType, TicketPriority, CreateTicketRequest } from '@/lib/types';
import '@/lib/i18n';

export default function CreateTicketPage() {
  const { t } = useTranslation();
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [formData, setFormData] = useState<CreateTicketRequest>({
    subject: '',
    description: '',
    type: 'ISSUE',
    priority: 'MEDIUM',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.subject.trim() || !formData.description.trim()) return;

    setSubmitting(true);
    try {
      const ticket = await api.createTicket(formData);
      router.push(`/tickets/${ticket.id}`);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to create ticket');
      setSubmitting(false);
    }
  };

  return (
    <DashboardLayout>
      <div className="max-w-3xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">{t('tickets.createTicket')}</h1>

        <form onSubmit={handleSubmit} className="bg-white shadow rounded-lg p-6 space-y-6">
          {/* Subject */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('tickets.subject')} *
            </label>
            <input
              type="text"
              value={formData.subject}
              onChange={(e) => setFormData({ ...formData, subject: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Brief description of the issue"
              required
            />
          </div>

          {/* Type */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('tickets.type')} *
            </label>
            <select
              value={formData.type}
              onChange={(e) => setFormData({ ...formData, type: e.target.value as TicketType })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="SUGGESTION">{t('tickets.typesSuggestion')}</option>
              <option value="BUG">{t('tickets.typesBug')}</option>
              <option value="ISSUE">{t('tickets.typesIssue')}</option>
              <option value="PERMISSION_REQUEST">{t('tickets.typesPermissionRequest')}</option>
              <option value="OTHER">{t('tickets.typesOther')}</option>
            </select>
          </div>

          {/* Priority */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('tickets.priority')} *
            </label>
            <select
              value={formData.priority}
              onChange={(e) => setFormData({ ...formData, priority: e.target.value as TicketPriority })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="LOW">{t('tickets.priorityLow')}</option>
              <option value="MEDIUM">{t('tickets.priorityMedium')}</option>
              <option value="HIGH">{t('tickets.priorityHigh')}</option>
              <option value="URGENT">{t('tickets.priorityUrgent')}</option>
            </select>
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              {t('tickets.description')} *
            </label>
            <textarea
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              rows={8}
              placeholder="Detailed description (Markdown supported)"
              required
            />
            <p className="mt-1 text-sm text-gray-500">
              Markdown supported: **bold**, *italic*, `code`, [link](url)
            </p>
          </div>

          {/* Actions */}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={submitting}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? 'Creating...' : t('tickets.createTicket')}
            </button>
            <button
              type="button"
              onClick={() => router.back()}
              className="px-4 py-2 bg-gray-300 text-gray-700 rounded hover:bg-gray-400"
            >
              {t('comments.cancel')}
            </button>
          </div>
        </form>
      </div>
    </DashboardLayout>
  );
}
