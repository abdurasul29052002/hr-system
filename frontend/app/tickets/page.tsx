'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { Ticket, TicketDetail, TicketPriority, TicketStatus, TicketType } from '@/lib/types';
import { Badge, Button, EmptyState, Field, Input, Modal, PageHeader, PageLoader, Select } from '@/components/ui';
import TicketConversation from '@/components/TicketConversation';
import '@/lib/i18n';

const STATUS_COLOR: Record<TicketStatus, 'blue' | 'amber' | 'green' | 'slate'> = { OPEN: 'blue', IN_PROGRESS: 'amber', RESOLVED: 'green', CLOSED: 'slate' };
const PRIO_COLOR: Record<TicketPriority, 'slate' | 'blue' | 'amber' | 'red'> = { LOW: 'slate', MEDIUM: 'blue', HIGH: 'amber', URGENT: 'red' };
const sKey = (s: TicketStatus) => ({ OPEN: 'Open', IN_PROGRESS: 'InProgress', RESOLVED: 'Resolved', CLOSED: 'Closed' }[s]);
const pKey = (p: TicketPriority) => ({ LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' }[p]);

export default function TicketsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);

  const employee = getStoredEmployee();

  const load = useCallback(async () => {
    try { setTickets(await api.getMyTickets()); } catch (e) { console.error(e); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (employee?.admin) { router.push('/admin'); return; }
    load();
  }, [load, employee?.admin, router]);

  if (loading) return <DashboardLayout><PageLoader /></DashboardLayout>;

  return (
    <DashboardLayout>
      <PageHeader title={t('tickets.myTickets')} actions={<Button onClick={() => setCreating(true)}>+ {t('tickets.newTicket')}</Button>} />
      {tickets.length === 0 ? (
        <EmptyState title={t('tickets.empty')} hint={t('tickets.createTicket')} />
      ) : (
        <div className="space-y-2">
          {tickets.map((tk) => (
            <button key={tk.id} onClick={() => setOpenId(tk.id)} className="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white p-3.5 text-left hover:shadow-md">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="truncate font-semibold text-slate-900">{tk.subject}</span>
                  <Badge color={STATUS_COLOR[tk.status]}>{t(`tickets.status${sKey(tk.status)}`)}</Badge>
                  <Badge color={PRIO_COLOR[tk.priority]}>{t(`tickets.priority${pKey(tk.priority)}`)}</Badge>
                </div>
                <p className="mt-0.5 truncate text-xs text-slate-400">{new Date(tk.createdAt).toLocaleDateString()} · 💬 {tk.messageCount}</p>
              </div>
              <svg className="h-5 w-5 shrink-0 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg>
            </button>
          ))}
        </div>
      )}
      {creating && <CreateTicketModal onClose={() => setCreating(false)} onCreated={(id) => { setCreating(false); load(); setOpenId(id); }} />}
      {openId != null && <TicketModal ticketId={openId} onClose={() => setOpenId(null)} onChanged={load} />}
    </DashboardLayout>
  );
}

function CreateTicketModal({ onClose, onCreated }: { onClose: () => void; onCreated: (id: number) => void }) {
  const { t } = useTranslation();
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<TicketType>('SUGGESTION');
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      const tk = await api.createTicket({ subject, description, type, priority });
      onCreated(tk.id);
    } catch (err) { setError(err instanceof Error ? err.message : 'Error'); setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={t('tickets.newTicket')} size="md">
      <form onSubmit={submit} className="space-y-3">
        <Field label={t('tickets.subject')}><Input value={subject} onChange={(e) => setSubject(e.target.value)} required autoFocus /></Field>
        <Field label={t('tickets.description')}>
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} required rows={4}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('tickets.type')}>
            <Select value={type} onChange={(e) => setType(e.target.value as TicketType)}>
              <option value="SUGGESTION">{t('tickets.typesSuggestion')}</option>
              <option value="BUG">{t('tickets.typesBug')}</option>
              <option value="ISSUE">{t('tickets.typesIssue')}</option>
              <option value="PERMISSION_REQUEST">{t('tickets.typesPermissionRequest')}</option>
              <option value="OTHER">{t('tickets.typesOther')}</option>
            </Select>
          </Field>
          <Field label={t('tickets.priority')}>
            <Select value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
              <option value="LOW">{t('tickets.priorityLow')}</option>
              <option value="MEDIUM">{t('tickets.priorityMedium')}</option>
              <option value="HIGH">{t('tickets.priorityHigh')}</option>
              <option value="URGENT">{t('tickets.priorityUrgent')}</option>
            </Select>
          </Field>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy}>{busy ? t('common.saving') : t('common.create')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function TicketModal({ ticketId, onClose, onChanged }: { ticketId: number; onClose: () => void; onChanged: () => void }) {
  const { t } = useTranslation();
  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [reply, setReply] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => { setTicket(await api.getTicket(ticketId)); }, [ticketId]);
  useEffect(() => { load(); }, [load]);

  const send = async () => {
    if (!reply.trim()) return;
    setBusy(true);
    try { await api.addTicketMessage(ticketId, reply.trim()); setReply(''); await load(); onChanged(); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); } finally { setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={ticket?.subject || '…'} size="lg">
      {!ticket ? <PageLoader /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Badge color={STATUS_COLOR[ticket.status]}>{t(`tickets.status${sKey(ticket.status)}`)}</Badge>
            <Badge color={PRIO_COLOR[ticket.priority]}>{t(`tickets.priority${pKey(ticket.priority)}`)}</Badge>
          </div>
          <TicketConversation ticket={ticket} />
          {ticket.status !== 'CLOSED' && (
            <div className="flex gap-2">
              <textarea value={reply} onChange={(e) => setReply(e.target.value)} placeholder={t('tickets.reply')} rows={2}
                className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
              <Button onClick={send} disabled={busy || !reply.trim()}>{t('tickets.send')}</Button>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
