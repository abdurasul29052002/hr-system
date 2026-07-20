'use client';

import { getToken, getCurrentTeamId, clearAuth } from './auth-client';
import type {
  Employee,
  MyTeam,
  Tag,
  Task,
  TaskBody,
  Member,
  MentionMember,
  MemberLabel,
  MemberActivity,
  TimelineTask,
  MonthlyStats,
  Invite,
  InviteInfo,
  TeamAdmin,
  Language,
  TaskStatus,
  Role,
  TaskComment,
  CommentRequest,
  TaskAttachment,
  Notification,
  Ticket,
  TicketDetail,
  CreateTicketRequest,
  TicketMessage,
  TicketStats,
  TicketStatus,
  TicketPriority,
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
      // Spring's ProblemDetail uses 'detail'; older handlers use 'message'.
      if (body.detail) message = body.detail;
      else if (body.message) message = body.message;
    } catch {
      /* keep default message */
    }
    throw new Error(message);
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * Uploads a file straight to S3 with a short-lived signed URL, reporting progress as it goes, and returns
 * the object key to hand back when attaching it.
 *
 * The bytes never touch our backend or the Vercel proxy — that is what makes 100MB screen recordings
 * workable: no proxy request-body limit, no 120s proxy timeout, and no database connection held open for
 * the length of the transfer.
 *
 * XMLHttpRequest rather than fetch, because fetch still cannot report upload progress.
 */
export async function uploadDirect(
  file: File,
  onProgress?: (percent: number) => void,
  signal?: AbortSignal,
): Promise<string> {
  const { key, uploadUrl } = await request<{ key: string; uploadUrl: string }>('/api/uploads/presign', {
    method: 'POST',
    body: JSON.stringify({ fileName: file.name, contentType: file.type }),
  });

  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl);
    // A dead connection would otherwise leave the row uploading forever and Send disabled.
    xhr.timeout = 10 * 60 * 1000;
    xhr.ontimeout = () => reject(new Error('Upload timed out'));
    // Both headers are covered by the signature the backend produced — S3 rejects the PUT if either
    // differs, and the bucket's CORS "allowed headers" must permit them.
    xhr.setRequestHeader('Content-Type', file.type);
    xhr.setRequestHeader('x-amz-acl', 'public-read');

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status})`));
    xhr.onerror = () => reject(new Error('Upload failed — check your connection'));
    xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));

    signal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(file);
  });

  return key;
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
  botInfo: () => request<{ enabled: boolean; username: string | null }>('/api/bot-info'),
  updateLanguage: (language: Language) =>
    request<Employee>('/api/auth/language', { method: 'POST', body: JSON.stringify({ language }) }),
  changePassword: (oldPassword: string, newPassword: string) =>
    request<Employee>('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ oldPassword, newPassword }),
    }),
  deleteAccount: (password: string) =>
    request<void>('/api/auth/delete-account', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),

  teamJoinSearch: (q: string) => request<{ id: number; name: string; memberCount: number; createdAt: string }[]>(`/api/team-join-requests/search?q=${encodeURIComponent(q)}`),
  createTeamJoinRequest: (teamId: number) => request<{ id: number; teamId: number; teamName: string; employeeId: number; employeeName: string; employeeUsername: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string }>(`/api/team-join-requests/${teamId}`, { method: 'POST' }),
  pendingTeamJoinRequests: () => request<{ id: number; teamId: number; teamName: string; employeeId: number; employeeName: string; employeeUsername: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string }[]>('/api/team-join-requests'),
  approveTeamJoinRequest: (requestId: number) => request<{ id: number; teamId: number; teamName: string; employeeId: number; employeeName: string; employeeUsername: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string }>(`/api/team-join-requests/${requestId}/approve`, { method: 'POST' }),
  rejectTeamJoinRequest: (requestId: number) => request<{ id: number; teamId: number; teamName: string; employeeId: number; employeeName: string; employeeUsername: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; createdAt: string }>(`/api/team-join-requests/${requestId}/reject`, { method: 'POST' }),

  createTeam: (name: string) => request<MyTeam>('/api/teams', { method: 'POST', body: JSON.stringify({ name }) }),
  myTeams: () => request<MyTeam[]>('/api/teams/my'),
  deleteTeam: (teamId: number) => request<void>(`/api/teams/${teamId}`, { method: 'DELETE' }),

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
  // Self-reported work: a member proposes a task; a leader confirms or declines it.
  proposeTask: (body: TaskBody) => request<Task>('/api/tasks/propose', { method: 'POST', body: JSON.stringify(body) }),
  approveProposal: (id: number) => request<Task>(`/api/tasks/${id}/approve-proposal`, { method: 'POST' }),
  rejectProposal: (id: number) => request<void>(`/api/tasks/${id}/reject-proposal`, { method: 'POST' }),

  members: () => request<Member[]>('/api/employees'),
  mentionableMembers: () => request<MentionMember[]>('/api/employees/mentionable'),
  createMember: (body: {
    fullName: string;
    username: string;
    password: string;
    phone?: string;
    position?: string;
    role?: Role;
    language?: Language;
    labelIds?: number[];
  }) => request<Member>('/api/employees', { method: 'POST', body: JSON.stringify(body) }),
  addExistingMember: (body: { username: string; role?: Role; position?: string; labelIds?: number[] }) =>
    request<Member>('/api/employees/add-existing', { method: 'POST', body: JSON.stringify(body) }),
  updateMember: (employeeId: number, body: { role?: Role; position?: string; labelIds?: number[] }) =>
    request<Member>(`/api/employees/${employeeId}`, { method: 'PUT', body: JSON.stringify(body) }),
  removeMember: (employeeId: number) => request<void>(`/api/employees/${employeeId}`, { method: 'DELETE' }),
  resetTelegram: (employeeId: number) =>
    request<Member>(`/api/employees/${employeeId}/reset-telegram`, { method: 'POST' }),

  // Member specialization labels
  memberLabels: () => request<MemberLabel[]>('/api/member-labels'),
  createMemberLabel: (body: { name: string; color?: string | null }) =>
    request<MemberLabel>('/api/member-labels', { method: 'POST', body: JSON.stringify(body) }),
  updateMemberLabel: (id: number, body: { name: string; color?: string | null }) =>
    request<MemberLabel>(`/api/member-labels/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteMemberLabel: (id: number) => request<void>(`/api/member-labels/${id}`, { method: 'DELETE' }),

  monthlyStats: (year: number, month: number) =>
    request<MonthlyStats>(`/api/stats/monthly?year=${year}&month=${month}`),
  currentActivity: () => request<MemberActivity[]>('/api/stats/current'),
  timeline: (year: number, month: number) =>
    request<TimelineTask[]>(`/api/stats/timeline?year=${year}&month=${month}`),
  downloadReport: async (year: number, month: number): Promise<void> => {
    const headers: Record<string, string> = {};
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const teamId = getCurrentTeamId();
    if (teamId != null) headers['X-Team-Id'] = String(teamId);
    const res = await fetch(`/api/stats/report?year=${year}&month=${month}`, { headers });
    if (!res.ok) throw new Error('Failed to generate report');
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `report-${year}-${String(month).padStart(2, '0')}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  },

  createInvite: (role?: Role) =>
    request<Invite>('/api/invites', { method: 'POST', body: JSON.stringify({ role: role ?? null }) }),
  invites: () => request<Invite[]>('/api/invites'),
  revokeInvite: (id: number) => request<void>(`/api/invites/${id}`, { method: 'DELETE' }),
  inviteInfo: (token: string) => request<InviteInfo>(`/api/invites/token/${token}`),
  acceptInvite: (token: string) => request<MyTeam>(`/api/invites/token/${token}/accept`, { method: 'POST' }),

  adminTeams: () => request<TeamAdmin[]>('/api/admin/teams'),
  adminEmployees: () => request<Employee[]>('/api/admin/employees'),
  adminDeleteTeam: (teamId: number) => request<void>(`/api/admin/teams/${teamId}`, { method: 'DELETE' }),

  // Task Comments
  /** Attachments are uploaded to S3 first (see uploadDirect) and referenced here by key. */
  addComment: (taskId: number, content: string, attachments: { key: string; fileName: string }[] = []) =>
    request<TaskComment>(`/api/tasks/${taskId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ content, attachments }),
    }),
  listComments: (taskId: number) => request<TaskComment[]>(`/api/tasks/${taskId}/comments`),
  getCommentCount: (taskId: number) => request<number>(`/api/tasks/${taskId}/comments/count`),
  updateComment: (commentId: number, body: CommentRequest) =>
    request<TaskComment>(`/api/comments/${commentId}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteComment: (commentId: number) => request<void>(`/api/comments/${commentId}`, { method: 'DELETE' }),

  // Task Attachments
  /** Attaches an object already uploaded to S3 by uploadDirect. */
  attachUploaded: (taskId: number, key: string, fileName: string) =>
    request<TaskAttachment>(`/api/tasks/${taskId}/attachments`, {
      method: 'POST',
      body: JSON.stringify({ key, fileName }),
    }),
  /** Upload straight to S3, then attach — with optional progress for large files. */
  uploadAttachment: async (
    taskId: number,
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<TaskAttachment> => {
    const key = await uploadDirect(file, onProgress);
    return api.attachUploaded(taskId, key, file.name);
  },
  listAttachments: (taskId: number) => request<TaskAttachment[]>(`/api/tasks/${taskId}/attachments`),
  deleteAttachment: (attachmentId: number) => request<void>(`/api/attachments/${attachmentId}`, { method: 'DELETE' }),

  // Notifications
  getNotifications: (unreadOnly?: boolean) =>
    request<Notification[]>(`/api/notifications${unreadOnly ? '?unreadOnly=true' : ''}`),
  getUnreadCount: () => request<{ count: number }>('/api/notifications/unread-count'),
  markAsRead: (notificationId: number) => request<void>(`/api/notifications/${notificationId}/read`, { method: 'POST' }),
  markAllAsRead: () => request<void>('/api/notifications/read-all', { method: 'POST' }),

  // Tickets (User)
  createTicket: (body: CreateTicketRequest) => request<Ticket>('/api/tickets', { method: 'POST', body: JSON.stringify(body) }),
  getMyTickets: () => request<Ticket[]>('/api/tickets/my'),
  getTicket: (ticketId: number) => request<TicketDetail>(`/api/tickets/${ticketId}`),
  addTicketMessage: (ticketId: number, message: string) =>
    request<TicketMessage>(`/api/tickets/${ticketId}/messages`, { method: 'POST', body: JSON.stringify({ message }) }),

  // Tickets (Admin)
  getAllTickets: (status?: TicketStatus) =>
    request<Ticket[]>(`/api/admin/tickets${status ? `?status=${status}` : ''}`),
  getAdminTicket: (ticketId: number) => request<TicketDetail>(`/api/admin/tickets/${ticketId}`),
  respondToTicket: (ticketId: number, message: string) =>
    request<TicketMessage>(`/api/admin/tickets/${ticketId}/respond`, { method: 'POST', body: JSON.stringify({ message }) }),
  updateTicketStatus: (ticketId: number, status: TicketStatus) =>
    request<Ticket>(`/api/admin/tickets/${ticketId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  updateTicketPriority: (ticketId: number, priority: TicketPriority) =>
    request<Ticket>(`/api/admin/tickets/${ticketId}/priority`, { method: 'PATCH', body: JSON.stringify({ priority }) }),
  getTicketStats: () => request<TicketStats>('/api/admin/tickets/stats'),
};
