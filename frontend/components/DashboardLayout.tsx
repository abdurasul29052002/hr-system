'use client';

import { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import {
  getStoredEmployee,
  getCurrentMembership,
  clearAuth,
  setCurrentTeamId,
} from '@/lib/auth-client';
import { setLanguage } from '@/lib/i18n';
import { isManagerRole } from '@/lib/types';
import type { Employee } from '@/lib/types';
import '@/lib/i18n';

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useTranslation();
  const [employee, setEmployee] = useState<Employee | null>(null);

  useEffect(() => {
    const emp = getStoredEmployee();
    if (!emp) {
      router.push('/login');
      return;
    }

    // Redirect to create-team if no teams
    if (!emp.admin && emp.memberships.length === 0) {
      router.push('/create-team');
      return;
    }

    setEmployee(emp);
    setLanguage(emp.language);
  }, [router]);

  const handleLogout = () => {
    clearAuth();
    router.push('/login');
  };

  const handleTeamChange = (teamId: number) => {
    setCurrentTeamId(teamId);
    window.location.reload();
  };

  if (!employee) {
    return null;
  }

  const currentMembership = getCurrentMembership(employee);
  const isManager = isManagerRole(currentMembership?.role);
  const isAdmin = employee.admin;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-900">{t('appName')}</h1>

          <div className="flex items-center gap-4">
            {/* Team Switcher */}
            {!isAdmin && employee.memberships.length > 1 && (
              <select
                value={currentMembership?.teamId || ''}
                onChange={(e) => handleTeamChange(Number(e.target.value))}
                className="px-3 py-1 border border-gray-300 rounded-md text-sm"
              >
                {employee.memberships.map((m) => (
                  <option key={m.teamId} value={m.teamId}>
                    {m.teamName}
                  </option>
                ))}
              </select>
            )}

            <span className="text-sm text-gray-600">{employee.fullName}</span>

            <button
              onClick={handleLogout}
              className="text-sm text-red-600 hover:text-red-700"
            >
              {t('nav.logout')}
            </button>
          </div>
        </div>
      </header>

      {/* Navigation */}
      <nav className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4">
          <div className="flex gap-6">
            <Link
              href="/"
              className={`py-3 border-b-2 ${
                pathname === '/'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {t('nav.tasks')}
            </Link>

            {isManager && (
              <>
                <Link
                  href="/employees"
                  className={`py-3 border-b-2 ${
                    pathname === '/employees'
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-600 hover:text-gray-900'
                  }`}
                >
                  {t('nav.employees')}
                </Link>

                <Link
                  href="/tags"
                  className={`py-3 border-b-2 ${
                    pathname === '/tags'
                      ? 'border-blue-600 text-blue-600'
                      : 'border-transparent text-gray-600 hover:text-gray-900'
                  }`}
                >
                  {t('nav.tags')}
                </Link>
              </>
            )}

            <Link
              href="/stats"
              className={`py-3 border-b-2 ${
                pathname === '/stats'
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-600 hover:text-gray-900'
              }`}
            >
              {t('nav.stats')}
            </Link>

            {isAdmin && (
              <Link
                href="/admin"
                className={`py-3 border-b-2 ${
                  pathname === '/admin'
                    ? 'border-blue-600 text-blue-600'
                    : 'border-transparent text-gray-600 hover:text-gray-900'
                }`}
              >
                {t('nav.admin')}
              </Link>
            )}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6">{children}</main>
    </div>
  );
}
