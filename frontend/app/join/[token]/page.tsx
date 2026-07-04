'use client';

import { useEffect, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getToken, setCurrentTeamId, storeEmployee } from '@/lib/auth-client';
import type { InviteInfo } from '@/lib/types';
import '@/lib/i18n';

export default function JoinPage() {
  const router = useRouter();
  const params = useParams();
  const { t } = useTranslation();
  const token = params.token as string;

  const [inviteInfo, setInviteInfo] = useState<InviteInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [joining, setJoining] = useState(false);

  const isAuthenticated = !!getToken();

  useEffect(() => {
    loadInviteInfo();
  }, [token]);

  const loadInviteInfo = async () => {
    try {
      const info = await api.inviteInfo(token);
      setInviteInfo(info);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid or expired invite link');
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async () => {
    if (!isAuthenticated) {
      // Store token in sessionStorage to continue after login
      sessionStorage.setItem('pendingInvite', token);
      router.push('/login');
      return;
    }

    setJoining(true);
    setError('');

    try {
      const team = await api.acceptInvite(token);
      setCurrentTeamId(team.teamId);

      // Refresh employee data
      const updatedEmployee = await api.me();
      storeEmployee(updatedEmployee);

      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to join team');
    } finally {
      setJoining(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="text-center">Loading...</div>
      </div>
    );
  }

  if (error || !inviteInfo) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8">
          <h1 className="text-2xl font-bold text-center mb-4 text-red-600">Error</h1>
          <p className="text-center text-gray-600 mb-6">{error}</p>
          <Link
            href="/"
            className="block w-full text-center px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Go to Home
          </Link>
        </div>
      </div>
    );
  }

  if (inviteInfo.alreadyMember) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8">
          <h1 className="text-2xl font-bold text-center mb-4">{t('join.title')}</h1>
          <p className="text-center text-gray-600 mb-6">
            {t('join.alreadyMember', { team: inviteInfo.teamName })}
          </p>
          <Link
            href="/"
            className="block w-full text-center px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Go to Dashboard
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-center mb-4">{t('join.title')}</h1>

        {!isAuthenticated ? (
          <div className="mb-6">
            <p className="text-center text-gray-600 mb-4">{t('join.authHint')}</p>
            <div className="flex gap-2">
              <Link
                href="/login"
                className="flex-1 text-center px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                {t('login.title')}
              </Link>
              <Link
                href="/register"
                className="flex-1 text-center px-4 py-2 bg-gray-300 text-gray-700 rounded hover:bg-gray-400"
              >
                {t('register.title')}
              </Link>
            </div>
          </div>
        ) : (
          <>
            <p className="text-center text-gray-600 mb-6">
              {t('join.hint', {
                team: inviteInfo.teamName,
                role: t(`employees.roles.${inviteInfo.role}`),
              })}
            </p>

            <button
              onClick={handleJoin}
              disabled={joining}
              className="w-full px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {joining ? '...' : t('join.submit')}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
