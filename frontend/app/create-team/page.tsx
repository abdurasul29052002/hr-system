'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { getStoredEmployee, setCurrentTeamId, storeEmployee } from '@/lib/auth-client';
import '@/lib/i18n';

export default function CreateTeamPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [teamName, setTeamName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [employee, setEmployee] = useState(getStoredEmployee());

  useEffect(() => {
    const emp = getStoredEmployee();
    if (emp && emp.memberships.length > 0) {
      router.push('/');
    }
    setEmployee(emp);
  }, [router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const team = await api.createTeam(teamName);
      setCurrentTeamId(team.teamId);

      // Refresh employee data
      const updatedEmployee = await api.me();
      storeEmployee(updatedEmployee);

      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create team');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100">
      <div className="max-w-md w-full bg-white rounded-lg shadow-md p-8">
        <h1 className="text-2xl font-bold text-center mb-2">{t('team.createTitle')}</h1>
        <p className="text-gray-600 text-sm text-center mb-6">
          {t('team.createHint', { name: employee?.fullName || '' })}
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('team.name')}
            </label>
            <input
              type="text"
              value={teamName}
              onChange={(e) => setTeamName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
              autoFocus
            />
          </div>

          {error && (
            <div className="text-red-600 text-sm">{error}</div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 px-4 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? '...' : t('team.createSubmit')}
          </button>
        </form>
      </div>
    </div>
  );
}
