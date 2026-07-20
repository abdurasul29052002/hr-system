'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import {
  getStoredEmployee,
  getCurrentMembership,
  getCurrentTeamId,
  clearAuth,
  setCurrentTeamId,
  storeEmployee,
} from '@/lib/auth-client';
import { api } from '@/lib/api';
import { setLanguage } from '@/lib/i18n';
import { isManagerRole } from '@/lib/types';
import type { Employee, Language, Role } from '@/lib/types';
import NotificationPanel from './NotificationPanel';
import TelegramConnect from './TelegramConnect';
import { Avatar, Badge, Button, Field, Input, Modal } from './ui';
import '@/lib/i18n';

const ROLE_COLOR: Record<Role, 'violet' | 'blue' | 'slate'> = {
  LEADER: 'violet',
  MANAGER: 'blue',
  MEMBER: 'slate',
};

function useClickOutside(onOutside: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onOutside();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onOutside]);
  return ref;
}

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t, i18n } = useTranslation();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [teamOpen, setTeamOpen] = useState(false);
  const [userOpen, setUserOpen] = useState(false);
  const [pwOpen, setPwOpen] = useState(false);
  const [delOpen, setDelOpen] = useState(false);
  const [tgOpen, setTgOpen] = useState(false);

  const teamRef = useClickOutside(() => setTeamOpen(false));
  const userRef = useClickOutside(() => setUserOpen(false));

  useEffect(() => {
    const emp = getStoredEmployee();
    if (!emp) {
      router.push('/login');
      return;
    }
    if (!emp.admin && emp.memberships.length === 0) {
      router.push('/team-access');
      return;
    }
    // Render immediately from the cookie…
    setEmployee(emp);
    setLanguage(emp.language);

    // …then re-sync memberships from the server. The cookie is written at login and lives for 7 days, so
    // without this a server-side change (removed from a team, team deleted, added to another) left the
    // client pinned to a dead team: every request kept sending X-Team-Id for a team the user is no longer
    // in, the backend answered 403, and the board silently rendered empty until the user logged out and
    // back in. storeEmployee() repoints teamId at a team the user actually belongs to.
    if (emp.admin) return;
    let cancelled = false;
    (async () => {
      try {
        const fresh = await api.me();
        if (cancelled) return;
        const before = getCurrentTeamId();
        storeEmployee(fresh);
        if (fresh.memberships.length === 0) {
          router.push('/team-access');
          return;
        }
        // The stale team was dropped — data already on screen was fetched for it, so start over cleanly.
        if (getCurrentTeamId() !== before) {
          window.location.reload();
          return;
        }
        setEmployee(fresh);
        setLanguage(fresh.language);
      } catch {
        // Offline or a transient failure: keep using the cached employee. A 401 is already handled in
        // lib/api.ts (clears auth and redirects to /login).
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [router]);

  if (!employee) return null;

  const currentMembership = getCurrentMembership(employee);
  const isManager = isManagerRole(currentMembership?.role);
  const isAdmin = employee.admin;

  const handleLogout = () => {
    clearAuth();
    router.push('/login');
  };
  const switchTeam = (teamId: number) => {
    setCurrentTeamId(teamId);
    window.location.href = '/tasks';
  };
  const handleDeleteTeam = async () => {
    if (!currentMembership) return;
    if (!confirm(t('team.deleteConfirm', { team: currentMembership.teamName }))) return;
    try {
      await api.deleteTeam(currentMembership.teamId);
      const me = await api.me();
      storeEmployee(me);
      if (me.memberships.length === 0) {
        window.location.href = '/team-access';
      } else {
        setCurrentTeamId(me.memberships[0].teamId);
        window.location.href = '/tasks';
      }
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to delete team');
    }
  };
  const changeLanguage = (lang: Language) => {
    setLanguage(lang);
    api.updateLanguage(lang).catch(() => undefined);
    const emp = { ...employee, language: lang };
    setEmployee(emp);
    // persist cookie
    import('@/lib/auth-client').then((m) => m.storeEmployee(emp));
  };

  const navLinks = isAdmin
    ? [{ href: '/admin', label: t('nav.admin') }]
    : [
        { href: '/tasks', label: t('nav.tasks') },
        ...(isManager
          ? [
              { href: '/employees', label: t('nav.employees') },
              { href: '/tags', label: t('nav.tags') },
            ]
          : []),
        { href: '/stats', label: t('nav.stats') },
        { href: '/tickets', label: t('tickets.title') },
      ];

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3">
          {/* Brand */}
          <Link href={isAdmin ? '/admin' : '/tasks'} className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">HR</span>
            <span className="hidden text-base font-bold text-slate-900 sm:block">{t('appName')}</span>
          </Link>

          {/* Team switcher */}
          {!isAdmin && currentMembership && (
            <div className="relative" ref={teamRef}>
              <button
                onClick={() => setTeamOpen((o) => !o)}
                className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm hover:bg-slate-50"
              >
                <span className="max-w-[10rem] truncate font-medium text-slate-800">{currentMembership.teamName}</span>
                <Badge color={ROLE_COLOR[currentMembership.role]}>{t(`employees.roles.${currentMembership.role}`)}</Badge>
                <svg className="h-4 w-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" /></svg>
              </button>
              {teamOpen && (
                <div className="animate-pop absolute left-0 mt-2 w-64 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
                  <div className="px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{t('team.switcher')}</div>
                  {employee.memberships.map((m) => (
                    <button
                      key={m.teamId}
                      onClick={() => switchTeam(m.teamId)}
                      className={`flex w-full items-center justify-between px-3 py-2 text-sm hover:bg-slate-50 ${
                        m.teamId === currentMembership.teamId ? 'bg-brand-50' : ''
                      }`}
                    >
                      <span className="truncate text-slate-800">{m.teamName}</span>
                      <Badge color={ROLE_COLOR[m.role]}>{t(`employees.roles.${m.role}`)}</Badge>
                    </button>
                  ))}
                  <Link
                    href="/create-team"
                    className="flex items-center gap-2 border-t border-slate-100 px-3 py-2 text-sm font-medium text-brand-600 hover:bg-slate-50"
                  >
                    <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
                    {t('team.new')}
                  </Link>
                  {currentMembership.role === 'LEADER' && (
                    <button
                      onClick={handleDeleteTeam}
                      className="flex w-full items-center gap-2 border-t border-slate-100 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
                    >
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                      {t('team.delete')}
                    </button>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="flex-1" />

          {/* Language */}
          <div className="flex items-center gap-0.5 rounded-lg bg-slate-100 p-0.5">
            {(['EN', 'RU', 'UZ'] as Language[]).map((l) => (
              <button
                key={l}
                onClick={() => changeLanguage(l)}
                className={`rounded-md px-2 py-1 text-xs font-semibold transition-colors ${
                  i18n.language === l ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {l}
              </button>
            ))}
          </div>

          {!isAdmin && <NotificationPanel />}

          {/* User menu */}
          <div className="relative" ref={userRef}>
            <button onClick={() => setUserOpen((o) => !o)} className="flex items-center gap-2 rounded-lg py-1 pl-1 pr-2 hover:bg-slate-100">
              <Avatar name={employee.fullName} size={8} />
              <span className="hidden text-sm font-medium text-slate-700 md:block">{employee.fullName}</span>
            </button>
            {userOpen && (
              <div className="animate-pop absolute right-0 mt-2 w-52 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
                <div className="border-b border-slate-100 px-4 py-3">
                  <p className="truncate text-sm font-semibold text-slate-900">{employee.fullName}</p>
                  <p className="truncate text-xs text-slate-500">@{employee.username}</p>
                </div>
                {!isAdmin && (
                  <button onClick={() => { setTgOpen(true); setUserOpen(false); }} className="flex w-full items-center gap-2 px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50">
                    <svg className="h-4 w-4 text-sky-500" viewBox="0 0 24 24" fill="currentColor" aria-hidden><path d="M21.94 4.36 18.9 19.1c-.23 1.02-.84 1.27-1.7.79l-4.7-3.47-2.27 2.18c-.25.25-.46.46-.94.46l.33-4.78L18.6 5.9c.38-.34-.08-.53-.6-.19L6.55 12.9l-4.66-1.46c-1.01-.32-1.03-1.01.21-1.5l18.22-7.02c.84-.31 1.58.2 1.32 1.44Z" /></svg>
                    {t('telegram.menuItem')}
                    {!employee.telegramLinked && <span className="ml-auto h-2 w-2 rounded-full bg-sky-500" />}
                  </button>
                )}
                <button onClick={() => { setPwOpen(true); setUserOpen(false); }} className="block w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50">
                  {t('account.changePassword')}
                </button>
                <button onClick={handleLogout} className="block w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50">
                  {t('nav.logout')}
                </button>
                {!isAdmin && (
                  <button onClick={() => { setDelOpen(true); setUserOpen(false); }} className="block w-full border-t border-slate-100 px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50">
                    {t('account.deleteAccount')}
                  </button>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Nav tabs */}
        <nav className="mx-auto max-w-7xl px-4">
          <div className="flex gap-1 overflow-x-auto">
            {navLinks.map((link) => {
              const active = pathname === link.href || (link.href !== '/' && pathname.startsWith(link.href));
              return (
                <Link
                  key={link.href}
                  href={link.href}
                  className={`whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-medium transition-colors ${
                    active ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'
                  }`}
                >
                  {link.label}
                </Link>
              );
            })}
          </div>
        </nav>
      </header>

      {!isAdmin && (
        <TelegramConnect employee={employee} open={tgOpen} setOpen={setTgOpen} onEmployeeUpdate={setEmployee} />
      )}

      <main className="mx-auto max-w-7xl px-4 py-6">{children}</main>

      {pwOpen && <ChangePasswordModal onClose={() => setPwOpen(false)} />}
      {delOpen && <DeleteAccountModal onClose={() => setDelOpen(false)} onDeleted={handleLogout} />}
    </div>
  );
}

function ChangePasswordModal({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const [oldPassword, setOld] = useState('');
  const [newPassword, setNew] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await api.changePassword(oldPassword, newPassword);
      setOk(true);
      setTimeout(onClose, 1000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t('account.changePassword')} size="sm">
      {ok ? (
        <p className="py-4 text-center text-sm font-medium text-emerald-600">✓ {t('account.passwordChanged')}</p>
      ) : (
        <form onSubmit={submit} className="space-y-3">
          <Field label={t('account.oldPassword')}>
            <Input type="password" value={oldPassword} onChange={(e) => setOld(e.target.value)} required />
          </Field>
          <Field label={t('account.newPassword')}>
            <Input type="password" value={newPassword} onChange={(e) => setNew(e.target.value)} required minLength={4} />
          </Field>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="secondary" onClick={onClose}>{t('tasks.close')}</Button>
            <Button type="submit" disabled={busy}>{t('account.changePassword')}</Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

function DeleteAccountModal({ onClose, onDeleted }: { onClose: () => void; onDeleted: () => void }) {
  const { t } = useTranslation();
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await api.deleteAccount(password);
      onDeleted(); // clears auth + redirects; the account no longer exists
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed');
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t('account.deleteAccount')} size="sm">
      <form onSubmit={submit} className="space-y-3">
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {t('account.deleteWarning')}
        </div>
        <Field label={t('account.confirmPassword')}>
          <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoFocus />
        </Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('tasks.close')}</Button>
          <Button type="submit" variant="danger" disabled={busy || !password}>
            {busy ? '…' : t('account.deleteConfirm')}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
