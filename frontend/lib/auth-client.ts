'use client';

import Cookies from 'js-cookie';
import type { Employee, MyTeam } from './types';

// Client-side auth utilities
export function getToken(): string | null {
  return Cookies.get('token') ?? null;
}

export function getStoredEmployee(): Employee | null {
  const raw = Cookies.get('employee');
  return raw ? (JSON.parse(raw) as Employee) : null;
}

export function storeAuth(token: string, employee: Employee) {
  Cookies.set('token', token, { expires: 7 }); // 7 days
  storeEmployee(employee);
}

export function storeEmployee(employee: Employee) {
  Cookies.set('employee', JSON.stringify(employee), { expires: 7 });
  // keep the selected team valid
  const current = getCurrentTeamId();
  if (employee.memberships.length > 0 && !employee.memberships.some((m) => m.teamId === current)) {
    setCurrentTeamId(employee.memberships[0].teamId);
  }
}

export function clearAuth() {
  Cookies.remove('token');
  Cookies.remove('employee');
  Cookies.remove('teamId');
}

export function getCurrentTeamId(): number | null {
  const raw = Cookies.get('teamId');
  return raw ? Number(raw) : null;
}

export function setCurrentTeamId(teamId: number) {
  Cookies.set('teamId', String(teamId), { expires: 7 });
}

export function getCurrentMembership(employee: Employee | null): MyTeam | null {
  if (!employee || employee.memberships.length === 0) {
    return null;
  }
  const current = getCurrentTeamId();
  return employee.memberships.find((m) => m.teamId === current) ?? employee.memberships[0];
}
