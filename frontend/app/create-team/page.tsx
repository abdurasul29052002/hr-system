'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { clearAuth, getStoredEmployee, setCurrentTeamId, storeEmployee } from '@/lib/auth-client';
import AuthShell from '@/components/AuthShell';
import { Button, Field, Input } from '@/components/ui';
import '@/lib/i18n';

export default function CreateTeamPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [teamName, setTeamName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const employee = getStoredEmployee();
  const hasTeams = (employee?.memberships.length ?? 0) > 0;

  useEffect(() => {
    if (employee?.admin) router.push('/admin');
  }, [employee?.admin, router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const team = await api.createTeam(teamName);
      const updated = await api.me();
      storeEmployee(updated);
      setCurrentTeamId(team.teamId);
      window.location.href = '/';
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create team');
      setLoading(false);
    }
  };

  const logout = () => { clearAuth(); router.push('/login'); };

  return (
    <AuthShell title={t('team.createTitle')} subtitle={t('team.createHint', { name: employee?.fullName || '' })}>
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('team.name')}><Input value={teamName} onChange={(e) => setTeamName(e.target.value)} required autoFocus /></Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={loading} className="w-full">{loading ? '…' : t('team.createSubmit')}</Button>
        <Button type="button" variant="ghost" className="w-full" onClick={hasTeams ? () => router.push('/') : logout}>
          {hasTeams ? t('common.back') : t('nav.logout')}
        </Button>
      </form>
    </AuthShell>
  );
}
