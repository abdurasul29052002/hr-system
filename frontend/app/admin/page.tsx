'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { Employee, TeamAdmin, Ticket, TicketDetail, TicketPriority, TicketStatus, TicketStats } from '@/lib/types';
import { Avatar, Badge, Button, Card, EmptyState, Modal, PageHeader, PageLoader, Select } from '@/components/ui';
import TicketConversation from '@/components/TicketConversation';
import '@/lib/i18n';

const STATUS_COLOR: Record<TicketStatus, 'blue' | 'amber' | 'green' | 'slate'> = { OPEN: 'blue', IN_PROGRESS: 'amber', RESOLVED: 'green', CLOSED: 'slate' };
const PRIO_COLOR: Record<TicketPriority, 'slate' | 'blue' | 'amber' | 'red'> = { LOW: 'slate', MEDIUM: 'blue', HIGH: 'amber', URGENT: 'red' };

export default function AdminPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [teams, setTeams] = useState<TeamAdmin[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [tab, setTab] = useState<'teams' | 'users' | 'tickets'>('teams');
  const [loading, setLoading] = useState(true);
  const employee = getStoredEmployee();

  useEffect(() => {
    if (!employee?.admin) { router.push('/tasks'); return; }
    Promise.all([api.adminTeams(), api.adminEmployees()])
      .then(([tm, emp]) => { setTeams(tm); setEmployees(emp); })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, [employee?.admin, router]);

  const deleteTeam = async (id: number, name: string) => {
    if (!confirm(t('team.deleteConfirm', { team: name }))) return;
    try {
      await api.adminDeleteTeam(id);
      setTeams((cur) => cur.filter((x) => x.id !== id));
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to delete team');
    }
  };

  if (loading) return <DashboardLayout><PageLoader /></DashboardLayout>;

  const tabs = [
    { id: 'teams' as const, label: `${t('admin.teams')} (${teams.length})` },
    { id: 'users' as const, label: `${t('admin.users')} (${employees.length})` },
    { id: 'tickets' as const, label: t('tickets.title') },
  ];

  return (
    <DashboardLayout>
      <PageHeader title={t('admin.title')} />
      <div className="mb-5 flex gap-1 border-b border-slate-200">
        {tabs.map((tb) => (
          <button key={tb.id} onClick={() => setTab(tb.id)}
            className={`border-b-2 px-4 py-2 text-sm font-medium ${tab === tb.id ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
            {tb.label}
          </button>
        ))}
      </div>

      {tab === 'teams' && (
        <Card className="overflow-hidden">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr><th className="px-5 py-3">ID</th><th className="px-5 py-3">{t('admin.teamName')}</th><th className="px-5 py-3">{t('admin.memberCount')}</th><th className="px-5 py-3">{t('admin.createdAt')}</th><th className="px-5 py-3" /></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {teams.map((tm) => (
                <tr key={tm.id} className="hover:bg-slate-50/60">
                  <td className="px-5 py-3 text-slate-400">{tm.id}</td>
                  <td className="px-5 py-3 font-medium text-slate-900">{tm.name}</td>
                  <td className="px-5 py-3 text-slate-600">{tm.memberCount}</td>
                  <td className="px-5 py-3 text-slate-500">{new Date(tm.createdAt).toLocaleDateString()}</td>
                  <td className="px-5 py-3 text-right">
                    <button onClick={() => deleteTeam(tm.id, tm.name)} className="rounded-md px-2 py-1 text-xs font-medium text-red-600 hover:bg-red-50">
                      {t('common.delete')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {tab === 'users' && (
        <Card className="overflow-hidden">
          <table className="min-w-full divide-y divide-slate-100 text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr><th className="px-5 py-3">{t('employees.fullName')}</th><th className="px-5 py-3">{t('admin.team')}</th><th className="px-5 py-3">{t('employees.telegram')}</th></tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {employees.map((emp) => (
                <tr key={emp.id} className="hover:bg-slate-50/60">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={emp.fullName} size={8} />
                      <div>
                        <p className="font-medium text-slate-900">{emp.fullName} {emp.admin && <Badge color="violet">ADMIN</Badge>}</p>
                        <p className="text-xs text-slate-400">@{emp.username}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3 text-slate-600">{emp.memberships.map((m) => `${m.teamName} (${t(`employees.roles.${m.role}`)})`).join(', ') || t('common.none')}</td>
                  <td className="px-5 py-3">{emp.telegramLinked ? <Badge color="green">✓</Badge> : <span className="text-slate-400">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {tab === 'tickets' && <AdminTickets />}
    </DashboardLayout>
  );
}

function AdminTickets() {
  const { t } = useTranslation();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [stats, setStats] = useState<TicketStats | null>(null);
  const [filter, setFilter] = useState<TicketStatus | ''>('');
  const [open, setOpen] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, s] = await Promise.all([api.getAllTickets(filter || undefined), api.getTicketStats()]);
      setTickets(list); setStats(s);
    } catch (e) { console.error(e); }
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  return (
    <div className="space-y-4">
      {stats && (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {[
            { label: t('tickets.openCount'), value: stats.openCount, color: 'text-blue-600' },
            { label: t('tickets.inProgressCount'), value: stats.inProgressCount, color: 'text-amber-600' },
            { label: t('tickets.resolvedCount'), value: stats.resolvedCount, color: 'text-emerald-600' },
            { label: t('tickets.totalCount'), value: stats.totalCount, color: 'text-slate-900' },
          ].map((c) => (
            <Card key={c.label} className="p-4">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{c.label}</p>
              <p className={`mt-1 text-2xl font-bold ${c.color}`}>{c.value}</p>
            </Card>
          ))}
        </div>
      )}
      <div className="flex items-center gap-2">
        <Select className="w-48" value={filter} onChange={(e) => setFilter(e.target.value as TicketStatus | '')}>
          <option value="">{t('tickets.allTickets')}</option>
          <option value="OPEN">{t('tickets.statusOpen')}</option>
          <option value="IN_PROGRESS">{t('tickets.statusInProgress')}</option>
          <option value="RESOLVED">{t('tickets.statusResolved')}</option>
          <option value="CLOSED">{t('tickets.statusClosed')}</option>
        </Select>
      </div>
      {tickets.length === 0 ? (
        <EmptyState title={t('tickets.empty')} />
      ) : (
        <div className="space-y-2">
          {tickets.map((tk) => (
            <button key={tk.id} onClick={() => setOpen(tk.id)} className="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white p-3 text-left hover:shadow-md">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="truncate font-semibold text-slate-900">{tk.subject}</span>
                  <Badge color={STATUS_COLOR[tk.status]}>{t(`tickets.status${statusKey(tk.status)}`)}</Badge>
                  <Badge color={PRIO_COLOR[tk.priority]}>{t(`tickets.priority${prioKey(tk.priority)}`)}</Badge>
                </div>
                <p className="mt-0.5 truncate text-xs text-slate-400">{t('tickets.startedBy')} {tk.creatorName} · {new Date(tk.createdAt).toLocaleDateString()} · 💬 {tk.messageCount}</p>
              </div>
            </button>
          ))}
        </div>
      )}
      {open != null && <AdminTicketModal ticketId={open} onClose={() => setOpen(null)} onChanged={load} />}
    </div>
  );
}

function statusKey(s: TicketStatus) { return { OPEN: 'Open', IN_PROGRESS: 'InProgress', RESOLVED: 'Resolved', CLOSED: 'Closed' }[s]; }
function prioKey(p: TicketPriority) { return { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High', URGENT: 'Urgent' }[p]; }

function AdminTicketModal({ ticketId, onClose, onChanged }: { ticketId: number; onClose: () => void; onChanged: () => void }) {
  const { t } = useTranslation();
  const [ticket, setTicket] = useState<TicketDetail | null>(null);
  const [reply, setReply] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => { setTicket(await api.getAdminTicket(ticketId)); }, [ticketId]);
  useEffect(() => { load(); }, [load]);

  const send = async () => {
    if (!reply.trim()) return;
    setBusy(true);
    try { await api.respondToTicket(ticketId, reply.trim()); setReply(''); await load(); onChanged(); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); } finally { setBusy(false); }
  };
  const setStatus = async (s: TicketStatus) => { await api.updateTicketStatus(ticketId, s); await load(); onChanged(); };
  const setPriority = async (p: TicketPriority) => { await api.updateTicketPriority(ticketId, p); await load(); onChanged(); };

  return (
    <Modal open onClose={onClose} title={ticket?.subject || '…'} size="lg">
      {!ticket ? <PageLoader /> : (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Select className="w-40" value={ticket.status} onChange={(e) => setStatus(e.target.value as TicketStatus)}>
              <option value="OPEN">{t('tickets.statusOpen')}</option>
              <option value="IN_PROGRESS">{t('tickets.statusInProgress')}</option>
              <option value="RESOLVED">{t('tickets.statusResolved')}</option>
              <option value="CLOSED">{t('tickets.statusClosed')}</option>
            </Select>
            <Select className="w-40" value={ticket.priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
              <option value="LOW">{t('tickets.priorityLow')}</option>
              <option value="MEDIUM">{t('tickets.priorityMedium')}</option>
              <option value="HIGH">{t('tickets.priorityHigh')}</option>
              <option value="URGENT">{t('tickets.priorityUrgent')}</option>
            </Select>
          </div>
          <TicketConversation ticket={ticket} />
          <div className="flex gap-2">
            <textarea value={reply} onChange={(e) => setReply(e.target.value)} placeholder={t('tickets.reply')} rows={2}
              className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/30" />
            <Button onClick={send} disabled={busy || !reply.trim()}>{t('tickets.send')}</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

