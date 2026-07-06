'use client';

import { ReactNode } from 'react';

export default function AuthShell({ title, subtitle, children, footer }: { title: string; subtitle?: string; children: ReactNode; footer?: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-100 via-slate-100 to-brand-100 p-4">
      <div className="w-full max-w-md">
        <div className="mb-6 flex flex-col items-center">
          <span className="mb-3 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-600 text-lg font-bold text-white shadow-lg shadow-brand-600/30">HR</span>
          <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-center text-sm text-slate-500">{subtitle}</p>}
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xl shadow-slate-200/50">{children}</div>
        {footer && <div className="mt-4 text-center text-sm text-slate-500">{footer}</div>}
      </div>
    </div>
  );
}

/** Decide where a freshly-authenticated employee should land. */
export function postAuthDestination(employee: { admin: boolean; memberships: unknown[] }): string {
  const pending = typeof window !== 'undefined' ? sessionStorage.getItem('pendingInvite') : null;
  if (pending && !employee.admin) {
    sessionStorage.removeItem('pendingInvite');
    return `/join/${pending}`;
  }
  if (employee.admin) return '/admin';
  return employee.memberships.length > 0 ? '/tasks' : '/create-team';
}
