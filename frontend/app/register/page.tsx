'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { storeAuth, getToken } from '@/lib/auth-client';
import type { Language } from '@/lib/types';
import AuthShell, { postAuthDestination } from '@/components/AuthShell';
import { Button, Field, Input, Select } from '@/components/ui';
import '@/lib/i18n';

export default function RegisterPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [language, setLanguage] = useState<Language>('EN');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => { if (getToken()) router.push('/tasks'); }, [router]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setLoading(true);
    try {
      const result = await api.register({ fullName, username, password, phone: phone || undefined, language });
      storeAuth(result.token, result.employee);
      router.push(postAuthDestination(result.employee));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed');
      setLoading(false);
    }
  };

  return (
    <AuthShell
      title={t('register.title')}
      footer={<>{t('register.haveAccount')} <Link href="/login" className="font-medium text-brand-600 hover:underline">{t('register.loginLink')}</Link></>}
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label={t('register.fullName')}><Input value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus /></Field>
        <Field label={t('login.username')}><Input value={username} onChange={(e) => setUsername(e.target.value)} required /></Field>
        <Field label={t('login.password')}><Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={4} /></Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('register.phone')}><Input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
          <Field label={t('settings.language')}>
            <Select value={language} onChange={(e) => setLanguage(e.target.value as Language)}>
              <option value="EN">English</option><option value="RU">Русский</option><option value="UZ">O&apos;zbekcha</option>
            </Select>
          </Field>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <Button type="submit" disabled={loading} className="w-full">{loading ? '…' : t('register.submit')}</Button>
      </form>
    </AuthShell>
  );
}
