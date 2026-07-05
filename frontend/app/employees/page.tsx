'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Invite, Language, Member, MemberLabel, Role } from '@/lib/types';
import { Avatar, Badge, Button, Card, Field, Input, Modal, PageHeader, PageLoader, Select } from '@/components/ui';
import '@/lib/i18n';

const ROLE_COLOR: Record<Role, 'violet' | 'blue' | 'slate'> = { LEADER: 'violet', MANAGER: 'blue', MEMBER: 'slate' };
const LABEL_PALETTE = ['#4f46e5', '#0891b2', '#059669', '#d97706', '#dc2626', '#7c3aed', '#db2777', '#475569'];

function LabelChips({ labels }: { labels: MemberLabel[] }) {
  if (labels.length === 0) return null;
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {labels.map((l) => (
        <span key={l.id} className="rounded-full px-2 py-0.5 text-[11px] font-medium text-white" style={{ background: l.color || '#64748b' }}>
          {l.name}
        </span>
      ))}
    </div>
  );
}

export default function EmployeesPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [members, setMembers] = useState<Member[]>([]);
  const [labels, setLabels] = useState<MemberLabel[]>([]);
  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  const [invitesOpen, setInvitesOpen] = useState(false);
  const [labelsOpen, setLabelsOpen] = useState(false);
  const [editing, setEditing] = useState<Member | null>(null);

  const me = getStoredEmployee();
  const membership = getCurrentMembership(me);
  const isManager = isManagerRole(membership?.role);
  const isLeader = membership?.role === 'LEADER';

  const load = useCallback(async () => {
    try {
      const [m, l] = await Promise.all([api.members(), api.memberLabels()]);
      setMembers(m);
      setLabels(l);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!isManager) { router.push('/'); return; }
    load();
  }, [isManager, router, load]);

  const run = async (fn: () => Promise<unknown>) => {
    try { await fn(); await load(); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); }
  };

  if (loading) return <DashboardLayout><PageLoader /></DashboardLayout>;

  return (
    <DashboardLayout>
      <PageHeader
        title={t('employees.title')}
        subtitle={`${members.length}`}
        actions={
          <>
            <Button variant="secondary" onClick={() => setLabelsOpen(true)}>🏷 {t('employees.labels')}</Button>
            <Button variant="secondary" onClick={() => setInvitesOpen(true)}>🔗 {t('invite.button')}</Button>
            <Button onClick={() => setAddOpen(true)}>+ {t('employees.add')}</Button>
          </>
        }
      />

      <Card className="overflow-hidden">
        <table className="min-w-full divide-y divide-slate-100">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-5 py-3">{t('employees.fullName')}</th>
              <th className="px-5 py-3">{t('employees.position')}</th>
              <th className="px-5 py-3">{t('employees.role')}</th>
              <th className="px-5 py-3">{t('employees.telegram')}</th>
              <th className="px-5 py-3"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 text-sm">
            {members.map((m) => {
              const isSelf = m.employeeId === me?.id;
              return (
                <tr key={m.employeeId} className="hover:bg-slate-50/60">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-2.5">
                      <Avatar name={m.fullName} size={8} />
                      <div>
                        <p className="font-medium text-slate-900">{m.fullName}{isSelf && <span className="ml-1 text-xs text-slate-400">({t('employees.you')})</span>}</p>
                        <p className="text-xs text-slate-400">@{m.username}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3 align-top text-slate-600">
                    {m.position || t('common.none')}
                    <LabelChips labels={m.labels} />
                  </td>
                  <td className="px-5 py-3 align-top"><Badge color={ROLE_COLOR[m.role]}>{t(`employees.roles.${m.role}`)}</Badge></td>
                  <td className="px-5 py-3 align-top">
                    {m.telegramLinked ? (
                      <Badge color="green">✓ {t('employees.linked')}</Badge>
                    ) : (
                      <span className="text-xs text-slate-500">{t('employees.code')}: <span className="font-mono font-semibold text-slate-700">{m.telegramLinkCode}</span></span>
                    )}
                  </td>
                  <td className="px-5 py-3 align-top">
                    <div className="flex items-center justify-end gap-1">
                      <Button size="sm" variant="ghost" onClick={() => setEditing(m)}>{t('common.edit')}</Button>
                      {m.telegramLinked && (
                        <Button size="sm" variant="ghost" onClick={() => run(() => api.resetTelegram(m.employeeId))}>{t('employees.resetTelegram')}</Button>
                      )}
                      {!isSelf && (
                        <Button size="sm" variant="danger" onClick={() => { if (confirm(t('employees.removeConfirm'))) run(() => api.removeMember(m.employeeId)); }}>{t('employees.remove')}</Button>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </Card>

      {addOpen && <AddMemberModal isLeader={isLeader} labels={labels} onClose={() => setAddOpen(false)} onSaved={() => { setAddOpen(false); load(); }} />}
      {editing && <EditMemberModal member={editing} isLeader={isLeader} isSelf={editing.employeeId === me?.id} labels={labels} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
      {invitesOpen && <InvitesModal isLeader={isLeader} onClose={() => setInvitesOpen(false)} />}
      {labelsOpen && <LabelsModal onClose={() => { setLabelsOpen(false); load(); }} />}
    </DashboardLayout>
  );
}

function LabelPicker({ labels, selected, onToggle }: { labels: MemberLabel[]; selected: number[]; onToggle: (id: number) => void }) {
  const { t } = useTranslation();
  if (labels.length === 0) return <p className="text-xs text-slate-400">{t('employees.noLabels')}</p>;
  return (
    <div className="flex flex-wrap gap-2">
      {labels.map((l) => {
        const on = selected.includes(l.id);
        return (
          <button key={l.id} type="button" onClick={() => onToggle(l.id)}
            className={`rounded-full px-2.5 py-1 text-xs font-medium text-white transition-opacity ${on ? 'opacity-100 ring-2 ring-offset-1 ring-slate-400' : 'opacity-50'}`}
            style={{ background: l.color || '#64748b' }}>
            {on ? '✓ ' : ''}{l.name}
          </button>
        );
      })}
    </div>
  );
}

function RoleSelect({ value, onChange, disabled }: { value: Role; onChange: (r: Role) => void; disabled?: boolean }) {
  const { t } = useTranslation();
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value as Role)} disabled={disabled}>
      <option value="MEMBER">{t('employees.roles.MEMBER')}</option>
      <option value="MANAGER">{t('employees.roles.MANAGER')}</option>
      <option value="LEADER">{t('employees.roles.LEADER')}</option>
    </Select>
  );
}

function AddMemberModal({ isLeader, labels, onClose, onSaved }: { isLeader: boolean; labels: MemberLabel[]; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<'new' | 'existing'>('new');
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [position, setPosition] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [language, setLanguage] = useState<Language>('EN');
  const [labelIds, setLabelIds] = useState<number[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const toggle = (id: number) => setLabelIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      if (mode === 'new') await api.createMember({ fullName, username, password, phone, position, role, language, labelIds });
      else await api.addExistingMember({ username, role, position, labelIds });
      onSaved();
    } catch (err) { setError(err instanceof Error ? err.message : 'Error'); setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={t('employees.add')} size="md">
      <div className="mb-4 flex gap-1 rounded-lg bg-slate-100 p-0.5">
        <button onClick={() => setMode('new')} className={`flex-1 rounded-md py-1.5 text-sm font-medium ${mode === 'new' ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500'}`}>{t('employees.addNew')}</button>
        <button onClick={() => setMode('existing')} className={`flex-1 rounded-md py-1.5 text-sm font-medium ${mode === 'existing' ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500'}`}>{t('employees.addExisting')}</button>
      </div>
      <form onSubmit={submit} className="space-y-3">
        <p className="text-xs text-slate-400">{mode === 'new' ? t('employees.addNewHint') : t('employees.addExistingHint')}</p>
        {mode === 'new' && <Field label={t('employees.fullName')}><Input value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus /></Field>}
        <Field label={t('employees.username')}><Input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus={mode === 'existing'} /></Field>
        {mode === 'new' && (
          <div className="grid grid-cols-2 gap-3">
            <Field label={t('employees.password')}><Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
            <Field label={t('employees.phone')}><Input value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
          </div>
        )}
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('employees.position')}><Input value={position} onChange={(e) => setPosition(e.target.value)} /></Field>
          <Field label={t('employees.role')}><RoleSelect value={role} onChange={setRole} disabled={!isLeader} /></Field>
        </div>
        <Field label={t('employees.labels')}><LabelPicker labels={labels} selected={labelIds} onToggle={toggle} /></Field>
        {mode === 'new' && (
          <Field label={t('employees.language')}>
            <Select value={language} onChange={(e) => setLanguage(e.target.value as Language)}>
              <option value="EN">English</option><option value="RU">Русский</option><option value="UZ">O&apos;zbekcha</option>
            </Select>
          </Field>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy}>{busy ? t('common.saving') : t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function EditMemberModal({ member, isLeader, isSelf, labels, onClose, onSaved }: { member: Member; isLeader: boolean; isSelf: boolean; labels: MemberLabel[]; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const [role, setRole] = useState<Role>(member.role);
  const [position, setPosition] = useState(member.position ?? '');
  const [labelIds, setLabelIds] = useState<number[]>(member.labels.map((l) => l.id));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const toggle = (id: number) => setLabelIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      await api.updateMember(member.employeeId, { role, position, labelIds });
      onSaved();
    } catch (err) { setError(err instanceof Error ? err.message : 'Error'); setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={`${t('common.edit')} — ${member.fullName}`} size="sm">
      <form onSubmit={submit} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('employees.role')}><RoleSelect value={role} onChange={setRole} disabled={!isLeader || isSelf} /></Field>
          <Field label={t('employees.position')}><Input value={position} onChange={(e) => setPosition(e.target.value)} /></Field>
        </div>
        <Field label={t('employees.labels')}><LabelPicker labels={labels} selected={labelIds} onToggle={toggle} /></Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}

function LabelsModal({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const [labels, setLabels] = useState<MemberLabel[]>([]);
  const [name, setName] = useState('');
  const [color, setColor] = useState(LABEL_PALETTE[0]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => { try { setLabels(await api.memberLabels()); } catch (e) { setError(e instanceof Error ? e.message : 'Error'); } }, []);
  useEffect(() => { load(); }, [load]);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setError('');
    try {
      if (editingId) await api.updateMemberLabel(editingId, { name, color });
      else await api.createMemberLabel({ name, color });
      setName(''); setColor(LABEL_PALETTE[0]); setEditingId(null);
      await load();
    } catch (err) { setError(err instanceof Error ? err.message : 'Error'); }
  };
  const edit = (l: MemberLabel) => { setEditingId(l.id); setName(l.name); setColor(l.color || LABEL_PALETTE[0]); };
  const remove = async (id: number) => { if (!confirm(t('common.confirmDelete'))) return; try { await api.deleteMemberLabel(id); await load(); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); } };

  return (
    <Modal open onClose={onClose} title={t('employees.manageLabels')} size="md">
      <p className="mb-3 text-sm text-slate-500">{t('employees.labelsHint')}</p>
      <form onSubmit={save} className="mb-4 space-y-3 rounded-lg bg-slate-50 p-3">
        <Field label={t('tags.name')}><Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Backend developer" /></Field>
        <Field label={t('tags.color')}>
          <div className="flex flex-wrap gap-2">
            {LABEL_PALETTE.map((c) => (
              <button key={c} type="button" onClick={() => setColor(c)} className={`h-7 w-7 rounded-full ${color === c ? 'ring-2 ring-slate-800 ring-offset-2' : ''}`} style={{ background: c }} />
            ))}
          </div>
        </Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2">
          {editingId && <Button type="button" variant="ghost" onClick={() => { setEditingId(null); setName(''); }}>{t('common.cancel')}</Button>}
          <Button type="submit" size="sm">{editingId ? t('common.save') : '+ ' + t('tags.add')}</Button>
        </div>
      </form>
      {labels.length === 0 ? (
        <p className="text-center text-sm text-slate-400">{t('employees.noLabels')}</p>
      ) : (
        <div className="flex flex-wrap gap-2">
          {labels.map((l) => (
            <div key={l.id} className="flex items-center gap-1.5 rounded-full py-1 pl-3 pr-1.5 text-xs font-medium text-white" style={{ background: l.color || '#64748b' }}>
              {l.name}
              <button onClick={() => edit(l)} className="rounded-full bg-white/20 px-1.5 hover:bg-white/30">✎</button>
              <button onClick={() => remove(l.id)} className="rounded-full bg-white/20 px-1.5 hover:bg-white/30">×</button>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}

function InvitesModal({ isLeader, onClose }: { isLeader: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [invites, setInvites] = useState<Invite[]>([]);
  const [role, setRole] = useState<Role>('MEMBER');
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try { setInvites(await api.invites()); } catch (e) { setError(e instanceof Error ? e.message : 'Error'); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const url = (token: string) => `${window.location.origin}/join/${token}`;
  const create = async () => { setError(''); try { await api.createInvite(role); await load(); } catch (e) { setError(e instanceof Error ? e.message : 'Error'); } };
  const revoke = async (id: number) => { try { await api.revokeInvite(id); await load(); } catch (e) { setError(e instanceof Error ? e.message : 'Error'); } };
  const copy = async (inv: Invite) => { try { await navigator.clipboard.writeText(url(inv.token)); setCopiedId(inv.id); setTimeout(() => setCopiedId(null), 1500); } catch { /* */ } };

  return (
    <Modal open onClose={onClose} title={t('invite.title')} size="lg">
      <p className="mb-3 text-sm text-slate-500">{t('invite.hint')}</p>
      <div className="mb-4 flex items-end gap-2">
        <div className="w-40"><RoleSelect value={role} onChange={setRole} disabled={!isLeader} /></div>
        <Button onClick={create}>+ {t('invite.create')}</Button>
      </div>
      {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
      {invites.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-400">{t('invite.empty')}</p>
      ) : (
        <div className="space-y-2">
          {invites.map((inv) => (
            <div key={inv.id} className="flex items-center gap-2 rounded-lg border border-slate-200 p-2.5">
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono text-xs text-slate-600">{url(inv.token)}</p>
                <p className="text-xs text-slate-400">
                  <Badge color={ROLE_COLOR[inv.role]}>{t(`employees.roles.${inv.role}`)}</Badge>
                  {inv.expiresAt && <span className="ml-2">{t('invite.expires')} {new Date(inv.expiresAt).toLocaleDateString()}</span>}
                </p>
              </div>
              <Button size="sm" variant="subtle" onClick={() => copy(inv)}>{copiedId === inv.id ? '✓ ' + t('common.copied') : t('invite.copy')}</Button>
              <Button size="sm" variant="danger" onClick={() => revoke(inv.id)}>{t('invite.revoke')}</Button>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
