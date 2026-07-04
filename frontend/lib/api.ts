'use client';

import { getToken, getCurrentTeamId, clearAuth } from './auth-client';
import type {
  Employee,
  MyTeam,
  Tag,
  Task,
  TaskBody,
  Member,
  MonthlyStats,
  Invite,
  InviteInfo,
  TeamAdmin,
  Language,
  TaskStatus,
  Role,
} from './types';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined),
  };
  const token = getToken();
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const teamId = getCurrentTeamId();
  if (teamId != null) {
    headers['X-Team-Id'] = String(teamId);
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    clearAuth();
    if (!path.startsWith('/api/auth/login')) {
      window.location.href = '/login';
    }
  }
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      if (body.message) message = body.message;
    } catch {
      /* keep default message */
    }
    throw new Error(message);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<{ token: string; employee: Employee }>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  register: (body: { fullName: string; username: string; password: string; phone?: string; language?: Language }) =>
    request<{ token: string; employee: Employee }>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  me: () => request<Employee>('/api/auth/me'),
  updateLanguage: (language: Language) =>
    request<Employee>('/api/auth/language', { method: 'POST', body: JSON.stringify({ language }) }),
  changePassword: (oldPassword: string, newPassword: string) =>
    request<Employee>('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ oldPassword, newPassword }),
    }),

  createTeam: (name: string) => request<MyTeam>('/api/teams', { method: 'POST', body: JSON.stringify({ name }) }),
  myTeams: () => request<MyTeam[]>('/api/teams/my'),

  tags: () => request<Tag[]>('/api/tags'),
  createTag: (body: { name: string; color?: string | null }) =>
    request<Tag>('/api/tags', { method: 'POST', body: JSON.stringify(body) }),
  updateTag: (id: number, body: { name: string; color?: string | null }) =>
    request<Tag>(`/api/tags/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteTag: (id: number) => request<void>(`/api/tags/${id}`, { method: 'DELETE' }),

  tasks: (params?: { status?: TaskStatus; mine?: boolean }) => {
    const query = new URLSearchParams();
    if (params?.status) query.set('status', params.status);
    if (params?.mine) query.set('mine', 'true');
    const qs = query.toString();
    return request<Task[]>(`/api/tasks${qs ? `?${qs}` : ''}`);
  },
  createTask: (body: TaskBody) => request<Task>('/api/tasks', { method: 'POST', body: JSON.stringify(body) }),
  updateTask: (id: number, body: TaskBody) =>
    request<Task>(`/api/tasks/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  assignTask: (id: number, employeeId: number) =>
    request<Task>(`/api/tasks/${id}/assign`, { method: 'POST', body: JSON.stringify({ employeeId }) }),
  takeTask: (id: number) => request<Task>(`/api/tasks/${id}/take`, { method: 'POST' }),
  completeTask: (id: number) => request<Task>(`/api/tasks/${id}/complete`, { method: 'POST' }),
  approveTask: (id: number) => request<Task>(`/api/tasks/${id}/approve`, { method: 'POST' }),
  rejectTask: (id: number) => request<Task>(`/api/tasks/${id}/reject`, { method: 'POST' }),
  releaseTask: (id: number) => request<Task>(`/api/tasks/${id}/release`, { method: 'POST' }),
  cancelTask: (id: number) => request<Task>(`/api/tasks/${id}/cancel`, { method: 'POST' }),

  members: () => request<Member[]>('/api/employees'),
  createMember: (body: {
    fullName: string;
    username: string;
    password: string;
    phone?: string;
    position?: string;
    role?: Role;
    language?: Language;
  }) => request<Member>('/api/employees', { method: 'POST', body: JSON.stringify(body) }),
  addExistingMember: (body: { username: string; role?: Role; position?: string }) =>
    request<Member>('/api/employees/add-existing', { method: 'POST', body: JSON.stringify(body) }),
  updateMember: (employeeId: number, body: { role?: Role; position?: string }) =>
    request<Member>(`/api/employees/${employeeId}`, { method: 'PUT', body: JSON.stringify(body) }),
  removeMember: (employeeId: number) => request<void>(`/api/employees/${employeeId}`, { method: 'DELETE' }),
  resetTelegram: (employeeId: number) =>
    request<Member>(`/api/employees/${employeeId}/reset-telegram`, { method: 'POST' }),

  monthlyStats: (year: number, month: number) =>
    request<MonthlyStats>(`/api/stats/monthly?year=${year}&month=${month}`),

  createInvite: (role?: Role) =>
    request<Invite>('/api/invites', { method: 'POST', body: JSON.stringify({ role: role ?? null }) }),
  invites: () => request<Invite[]>('/api/invites'),
  revokeInvite: (id: number) => request<void>(`/api/invites/${id}`, { method: 'DELETE' }),
  inviteInfo: (token: string) => request<InviteInfo>(`/api/invites/token/${token}`),
  acceptInvite: (token: string) => request<MyTeam>(`/api/invites/token/${token}/accept`, { method: 'POST' }),

  adminTeams: () => request<TeamAdmin[]>('/api/admin/teams'),
  adminEmployees: () => request<Employee[]>('/api/admin/employees'),
};
