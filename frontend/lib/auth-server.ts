import { cookies } from 'next/headers';
import type { Employee, MyTeam } from './types';

// Server-side auth utilities (for SSR)
export async function getServerToken(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get('token')?.value ?? null;
}

export async function getServerEmployee(): Promise<Employee | null> {
  const cookieStore = await cookies();
  const employeeData = cookieStore.get('employee')?.value;
  return employeeData ? JSON.parse(employeeData) : null;
}

export async function getServerTeamId(): Promise<number | null> {
  const cookieStore = await cookies();
  const teamId = cookieStore.get('teamId')?.value;
  return teamId ? Number(teamId) : null;
}

export async function getCurrentMembership(employee: Employee | null): Promise<MyTeam | null> {
  if (!employee || employee.memberships.length === 0) {
    return null;
  }
  const currentTeamId = await getServerTeamId();
  return employee.memberships.find((m) => m.teamId === currentTeamId) ?? employee.memberships[0];
}
