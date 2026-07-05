'use client';

import { useTranslation } from 'react-i18next';
import type { TicketDetail } from '@/lib/types';
import '@/lib/i18n';

export default function TicketConversation({ ticket }: { ticket: TicketDetail }) {
  const { t } = useTranslation();
  return (
    <div className="space-y-3">
      <div className="rounded-lg bg-slate-50 p-3">
        <p className="mb-1 text-xs text-slate-400">{ticket.creatorName} · {new Date(ticket.createdAt).toLocaleString()}</p>
        <p className="whitespace-pre-wrap text-sm text-slate-700">{ticket.description}</p>
      </div>
      {ticket.messages.map((m) => (
        <div key={m.id} className={`flex ${m.isAdminResponse ? 'justify-end' : 'justify-start'}`}>
          <div className={`max-w-[80%] rounded-2xl px-3 py-2 text-sm ${m.isAdminResponse ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-800'}`}>
            <p className={`mb-0.5 text-[11px] ${m.isAdminResponse ? 'text-brand-100' : 'text-slate-400'}`}>
              {m.senderName} · {new Date(m.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </p>
            <p className="whitespace-pre-wrap">{m.message}</p>
          </div>
        </div>
      ))}
    </div>
  );
}
