'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import AuthShell from '@/components/AuthShell';
import { Button, Field, Input, PageLoader } from '@/components/ui';
import { api } from '@/lib/api';
import { getStoredEmployee } from '@/lib/auth-client';
import type { TeamSearchResult } from '@/lib/types';
import '@/lib/i18n';

export default function TeamAccessPage() {
  const { t } = useTranslation();
  const router = useRouter();
  const employee = getStoredEmployee();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<TeamSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [submittingId, setSubmittingId] = useState<number | null>(null);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (employee?.admin) router.push('/admin');
    if (employee && employee.memberships.length > 0) router.push('/tasks');
  }, [employee, router]);

  const canSearch = query.trim().length >= 2;

  useEffect(() => {
    let cancelled = false;
    if (!canSearch) { setResults([]); return; }
    setLoading(true);
    api.teamJoinSearch(query.trim())
      .then((data) => { if (!cancelled) setResults(data); })
      .catch(() => { if (!cancelled) setResults([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [query, canSearch]);

  const requestJoin = async (teamId: number) => {
    setSubmittingId(teamId); setMessage('');
    try {
      await api.createTeamJoinRequest(teamId);
      setMessage(t('join.requestSent'));
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Error');
    } finally {
      setSubmittingId(null);
    }
  };

  const emptyHint = useMemo(() => {
    if (!canSearch) return t('join.searchHint');
    if (loading) return t('common.loading');
    return t('join.noTeamsFound');
  }, [canSearch, loading, t]);

  return (
    <AuthShell title={t('join.chooseTitle')} subtitle={t('join.chooseHint')}>
      <div className="space-y-5">
        <section className="rounded-xl border border-slate-200 bg-slate-50 p-4">
          <h2 className="text-sm font-semibold text-slate-900">{t('join.joinTeam')}</h2>
          <p className="mt-1 text-xs text-slate-500">{t('join.requestHint')}</p>
          <div className="mt-3 space-y-3">
            <Field label={t('common.search')}>
              <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('join.searchPlaceholder')} autoFocus />
            </Field>
            <div className="max-h-72 space-y-2 overflow-y-auto">
              {results.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-200 px-3 py-4 text-center text-sm text-slate-400">{emptyHint}</p>
              ) : (
                results.map((team) => (
                  <div key={team.id} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white px-3 py-3">
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-slate-900">{team.name}</p>
                      <p className="text-xs text-slate-500">{team.memberCount} {t('admin.memberCount').toLowerCase()}</p>
                    </div>
                    <Button size="sm" onClick={() => requestJoin(team.id)} disabled={submittingId === team.id}>
                      {submittingId === team.id ? '…' : t('join.requestAccess')}
                    </Button>
                  </div>
                ))
              )}
            </div>
            {message && <p className="text-sm text-emerald-600">{message}</p>}
          </div>
        </section>

        <section className="rounded-xl border border-brand-200 bg-brand-50 p-4">
          <h2 className="text-sm font-semibold text-slate-900">{t('join.goOwnTeam')}</h2>
          <p className="mt-1 text-xs text-slate-500">{t('join.goOwnTeamHint')}</p>
          <Button className="mt-3 w-full" onClick={() => router.push('/create-team')}>{t('join.goOwnTeam')}</Button>
        </section>
      </div>
    </AuthShell>
  );
}
