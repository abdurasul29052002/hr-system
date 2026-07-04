'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Member } from '@/lib/types';
import '@/lib/i18n';

export default function EmployeesPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);

  const employee = getStoredEmployee();
  const membership = getCurrentMembership(employee);
  const isManager = isManagerRole(membership?.role);

  useEffect(() => {
    if (!isManager) {
      router.push('/');
      return;
    }

    loadMembers();
  }, [isManager, router]);

  const loadMembers = async () => {
    try {
      const data = await api.members();
      setMembers(data);
    } catch (error) {
      console.error('Failed to load members:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleResetTelegram = async (employeeId: number) => {
    if (!confirm(t('employees.resetTelegram') + '?')) return;

    try {
      await api.resetTelegram(employeeId);
      loadMembers();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Failed to reset Telegram');
    }
  };

  if (loading) {
    return (
      <DashboardLayout>
        <div className="text-center py-8">Loading...</div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{t('employees.title')}</h2>
        <button className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
          {t('employees.add')}
        </button>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.fullName')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.username')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.position')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.role')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.telegram')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('employees.edit')}
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {members.map((member) => (
              <tr key={member.employeeId} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                  {member.fullName}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {member.username}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {member.position || '—'}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <span
                    className={`px-2 py-1 rounded text-xs ${
                      member.role === 'LEADER'
                        ? 'bg-purple-100 text-purple-700'
                        : member.role === 'MANAGER'
                        ? 'bg-blue-100 text-blue-700'
                        : 'bg-gray-100 text-gray-700'
                    }`}
                  >
                    {t(`employees.roles.${member.role}`)}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  {member.telegramLinked ? (
                    <span className="text-green-600">{t('employees.linked')}</span>
                  ) : member.telegramLinkCode ? (
                    <span className="text-gray-600">
                      {t('employees.code')}: {member.telegramLinkCode}
                    </span>
                  ) : (
                    <span className="text-gray-400">—</span>
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm">
                  <div className="flex gap-2">
                    <button className="text-blue-600 hover:text-blue-700">
                      {t('employees.edit')}
                    </button>
                    {member.telegramLinked && (
                      <button
                        onClick={() => handleResetTelegram(member.employeeId)}
                        className="text-red-600 hover:text-red-700"
                      >
                        {t('employees.resetTelegram')}
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </DashboardLayout>
  );
}
