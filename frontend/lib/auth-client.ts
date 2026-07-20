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

/**
 * When the auth cookies should die: exactly when the JWT inside them does.
 *
 * These used to be hardcoded to 7 days while the backend issued 72-hour tokens. In the gap the cookies
 * still looked like a live session — middleware.ts only checks that the token cookie EXISTS — so the app
 * rendered normally while every API call failed auth. Deriving the lifetime from the token's own `exp`
 * means the two can never drift again, whatever app.jwt.expiration-hours is set to.
 */
function cookieExpiry(token: string | null): Date | number {
  if (!token) return 1;
  try {
    const [, payload] = token.split('.');
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const exp = (JSON.parse(json) as { exp?: number }).exp;
    if (typeof exp === 'number') return new Date(exp * 1000);
  } catch {
    /* unreadable token — fall through */
  }
  return 1; // keep an unreadable token short-lived rather than stale for a week
}

export function storeAuth(token: string, employee: Employee) {
  Cookies.set('token', token, { expires: cookieExpiry(token) });
  storeEmployee(employee);
}

export function storeEmployee(employee: Employee) {
  const expires = cookieExpiry(getToken());
  Cookies.set('employee', JSON.stringify(employee), { expires });
  // Keep the selected team valid: repoint it if the cached one is gone, and drop it entirely when the
  // user is no longer in any team — otherwise a dead X-Team-Id keeps getting sent and every call 403s.
  const current = getCurrentTeamId();
  if (employee.memberships.length === 0) {
    clearCurrentTeamId();
  } else if (!employee.memberships.some((m) => m.teamId === current)) {
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
  Cookies.set('teamId', String(teamId), { expires: cookieExpiry(getToken()) });
}

/** Drops the selected team — used when the server says we are no longer a member of it. */
export function clearCurrentTeamId() {
  Cookies.remove('teamId');
}

export function getCurrentMembership(employee: Employee | null): MyTeam | null {
  if (!employee || employee.memberships.length === 0) {
    return null;
  }
  const current = getCurrentTeamId();
  return employee.memberships.find((m) => m.teamId === current) ?? employee.memberships[0];
}
