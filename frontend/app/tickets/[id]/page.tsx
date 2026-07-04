'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import MarkdownRenderer from '@/components/MarkdownRenderer';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { TicketDetail, TicketMessage } from '@/lib/types';
import '@/lib/i18n';

export default function TicketDetailPage() {
  const { t } = useTranslation();
  const params = useParams();
  const ticketId = Number(params.id);
  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [newMessage, setNewMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const currentEmployee = getStoredEmployee();

  useEffect(() => {
    loadTicket();
  }, [ticketId]);

  const loadTicket = async () => {
    try {
      const data = await api.getTicket(ticketId);
      setTicket(data);
    } catch (error) {
      console.error('Failed to load ticket:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMessage.trim()) return;

    setSubmitting(true);
    try {
      const message = await api.addTicketMessage(ticketId, newMessage.trim());
      setTicket(prev => prev ? { ...prev, messages: [...prev.messages, message] } : null);
      setNewMessage('');
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to send message');
    } finally {
      setSubmitting(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'OPEN': return 'bg-blue-100 text-blue-800';
      case 'IN_PROGRESS': return 'bg-yellow-100 text-yellow-800';
      case 'RESOLVED': return 'bg-green-100 text-green-800';
      case 'CLOSED': return 'bg-gray-100 text-gray-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  if (loading) {
    return (
      <DashboardLayout>
        <div className="text-center py-8">Loading...</div>
      </DashboardLayout>
    );
  }

  if (!ticket) {
    return (
      <DashboardLayout>
        <div className="text-center py-8 text-red-600">Ticket not found</div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="max-w-4xl mx-auto px-4 py-8">
        {/* Header */}
        <div className="bg-white shadow rounded-lg p-6 mb-6">
          <div className="flex items-start justify-between mb-4">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">#{ticket.id} {ticket.subject}</h1>
              <p className="text-sm text-gray-500 mt-1">
                Created by {ticket.creatorName} on {new Date(ticket.createdAt).toLocaleDateString()}
              </p>
            </div>
            <span className={`px-3 py-1 text-sm font-medium rounded ${getStatusColor(ticket.status)}`}>
              {t(`tickets.status${ticket.status.charAt(0) + ticket.status.slice(1).toLowerCase().replace('_', '')}`)}
            </span>
          </div>

          <div className="flex gap-4 text-sm">
            <span className="text-gray-600">
              <strong>{t('tickets.type')}:</strong> {t(`tickets.types${ticket.type.charAt(0) + ticket.type.slice(1).toLowerCase().replace('_', '')}`)}
            </span>
            <span className="text-gray-600">
              <strong>{t('tickets.priority')}:</strong> {t(`tickets.priority${ticket.priority.charAt(0) + ticket.priority.slice(1).toLowerCase()}`)}
            </span>
          </div>

          <div className="mt-4 prose prose-sm max-w-none">
            <MarkdownRenderer content={ticket.description} />
          </div>
        </div>

        {/* Messages */}
        <div className="bg-white shadow rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">
            {t('tickets.messages')} ({ticket.messages.length})
          </h2>

          <div className="space-y-4 mb-6">
            {ticket.messages.map((message) => (
              <div
                key={message.id}
                className={`p-4 rounded-lg ${
                  message.isAdminResponse
                    ? 'bg-blue-50 border-l-4 border-blue-500'
                    : 'bg-gray-50'
                }`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-gray-900">
                    {message.senderName}
                    {message.isAdminResponse && (
                      <span className="ml-2 text-xs bg-blue-600 text-white px-2 py-0.5 rounded">
                        Admin
                      </span>
                    )}
                  </span>
                  <span className="text-xs text-gray-500">
                    {new Date(message.createdAt).toLocaleString()}
                  </span>
                </div>
                <div className="prose prose-sm max-w-none">
                  <MarkdownRenderer content={message.message} />
                </div>
              </div>
            ))}
          </div>

          {/* Add Message Form */}
          {ticket.status !== 'CLOSED' && (
            <form onSubmit={handleSendMessage} className="space-y-3">
              <textarea
                value={newMessage}
                onChange={(e) => setNewMessage(e.target.value)}
                placeholder="Add a message... (Markdown supported)"
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                rows={4}
                disabled={submitting}
              />
              <button
                type="submit"
                disabled={submitting || !newMessage.trim()}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
              >
                {submitting ? 'Sending...' : t('comments.submit')}
              </button>
            </form>
          )}
        </div>
      </div>
    </DashboardLayout>
  );
}
