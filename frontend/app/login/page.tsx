'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { storeAuth, getToken } from '@/lib/auth-client';
import AuthShell, { postAuthDestination } from '@/components/AuthShell';
import { Button, Field, Input } from '@/components/ui';
import '@/lib/i18n';

export default function LoginPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => { if (getToken()) router.push('/tasks'); }, [router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const result = await api.login(username, password);
      storeAuth(result.token, result.employee);
      router.push(postAuthDestination(result.employee));
    } catch (err) {
      setError(err instanceof Error && err.message !== 'HTTP 401' ? err.message : t('login.error'));
      setLoading(false);
    }
  };

  return (
    <AuthShell
      title={t('login.title')}
      footer={<>{t('login.noAccount')} <Link href="/register" className="font-medium text-brand-600 hover:underline">{t('login.registerLink')}</Link></>}
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('login.username')}><Input value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus /></Field>
        <Field label={t('login.password')}><Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={loading} className="w-full">{loading ? '…' : t('login.submit')}</Button>
      </form>
    </AuthShell>
  );
}
