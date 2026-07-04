'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import type { MonthlyStats } from '@/lib/types';
import '@/lib/i18n';

export default function StatsPage() {
  const { t } = useTranslation();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [stats, setStats] = useState<MonthlyStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadStats();
  }, [year, month]);

  const loadStats = async () => {
    setLoading(true);
    try {
      const data = await api.monthlyStats(year, month);
      setStats(data);
    } catch (error) {
      console.error('Failed to load stats:', error);
      setStats(null);
    } finally {
      setLoading(false);
    }
  };

  const years = Array.from({ length: 5 }, (_, i) => now.getFullYear() - i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{t('stats.title')}</h2>

        <div className="flex gap-4">
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            {years.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>

          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-md"
          >
            {months.map((m) => (
              <option key={m} value={m}>
                {new Date(2000, m - 1).toLocaleDateString('en', { month: 'long' })}
              </option>
            ))}
          </select>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-8">Loading...</div>
      ) : !stats ? (
        <div className="text-center py-8 text-gray-500">{t('stats.empty')}</div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4 mb-6">
            <div className="bg-white p-4 rounded-lg shadow">
              <div className="text-sm text-gray-600">{t('stats.created')}</div>
              <div className="text-2xl font-bold text-gray-900">{stats.totalCreated}</div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow">
              <div className="text-sm text-gray-600">{t('stats.completed')}</div>
              <div className="text-2xl font-bold text-green-600">{stats.totalCompleted}</div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow">
              <div className="text-sm text-gray-600">{t('stats.inProgress')}</div>
              <div className="text-2xl font-bold text-blue-600">{stats.totalInProgress}</div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow">
              <div className="text-sm text-gray-600">{t('stats.testing')}</div>
              <div className="text-2xl font-bold text-yellow-600">{stats.totalTesting}</div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow">
              <div className="text-sm text-gray-600">{t('stats.open')}</div>
              <div className="text-2xl font-bold text-gray-600">{stats.totalOpen}</div>
            </div>
          </div>

          {/* Per Employee Stats */}
          {stats.perEmployee.length > 0 && (
            <div className="bg-white rounded-lg shadow overflow-hidden">
              <table className="min-w-full divide-y divide-gray-200">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.employee')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.taken')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.completed')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.inProgress')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.overdue')}
                    </th>
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                      {t('stats.avgHours')}
                    </th>
                  </tr>
                </thead>
                <tbody className="bg-white divide-y divide-gray-200">
                  {stats.perEmployee.map((emp) => (
                    <tr key={emp.employeeId} className="hover:bg-gray-50">
                      <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                        {emp.fullName}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {emp.taken}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-green-600 font-semibold">
                        {emp.completed}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-blue-600">
                        {emp.inProgress}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-red-600">
                        {emp.overdue}
                      </td>
                      <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                        {emp.avgCompletionHours != null
                          ? emp.avgCompletionHours.toFixed(1)
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </DashboardLayout>
  );
}
