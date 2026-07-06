'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getToken, setCurrentTeamId, storeEmployee } from '@/lib/auth-client';
import type { InviteInfo } from '@/lib/types';
import AuthShell from '@/components/AuthShell';
import { Button, PageLoader } from '@/components/ui';
import '@/lib/i18n';

export default function JoinPage() {
  const router = useRouter();
  const params = useParams();
  const { t } = useTranslation();
  const token = params.token as string;

  const [info, setInfo] = useState<InviteInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [joining, setJoining] = useState(false);
  const authed = !!getToken();

  useEffect(() => {
    if (!authed) { sessionStorage.setItem('pendingInvite', token); setLoading(false); return; }
    api.inviteInfo(token).then(setInfo).catch((e) => setError(e instanceof Error ? e.message : 'Invalid or expired link')).finally(() => setLoading(false));
  }, [token, authed]);

  const join = async () => {
    setJoining(true); setError('');
    try {
      const team = await api.acceptInvite(token);
      const updated = await api.me();
      storeEmployee(updated);
      setCurrentTeamId(team.teamId);
      window.location.href = '/tasks';
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to join');
      setJoining(false);
    }
  };

  return (
    <AuthShell title={t('join.title')}>
      {loading ? (
        <PageLoader />
      ) : !authed ? (
        <div className="space-y-3">
          <p className="text-center text-sm text-slate-500">{t('join.authHint')}</p>
          <Button className="w-full" onClick={() => router.push('/login')}>{t('login.title')}</Button>
          <Button variant="secondary" className="w-full" onClick={() => router.push('/register')}>{t('register.title')}</Button>
        </div>
      ) : error ? (
        <div className="space-y-3 text-center">
          <p className="text-sm text-red-600">{error}</p>
          <Button className="w-full" onClick={() => router.push('/tasks')}>{t('common.back')}</Button>
        </div>
      ) : info?.alreadyMember ? (
        <div className="space-y-3 text-center">
          <p className="text-sm text-slate-600">{t('join.alreadyMember', { team: info.teamName })}</p>
          <Button className="w-full" onClick={() => router.push('/tasks')}>{t('common.back')}</Button>
        </div>
      ) : info ? (
        <div className="space-y-4 text-center">
          <p className="text-sm text-slate-600">{t('join.hint', { team: info.teamName, role: t(`employees.roles.${info.role}`) })}</p>
          <Button className="w-full" onClick={join} disabled={joining}>{joining ? '…' : t('join.submit')}</Button>
          <Button variant="ghost" className="w-full" onClick={() => router.push('/tasks')}>{t('common.back')}</Button>
        </div>
      ) : null}
    </AuthShell>
  );
}
