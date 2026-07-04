export type Role = 'LEADER' | 'MANAGER' | 'MEMBER';
export type Language = 'EN' | 'RU' | 'UZ';
export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'TESTING' | 'DONE' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface MyTeam {
  teamId: number;
  teamName: string;
  role: Role;
  position: string | null;
}

export interface Employee {
  id: number;
  fullName: string;
  username: string;
  phone: string | null;
  language: Language;
  admin: boolean;
  telegramLinked: boolean;
  telegramLinkCode: string | null;
  active: boolean;
  memberships: MyTeam[];
}

export interface Member {
  employeeId: number;
  fullName: string;
  username: string;
  phone: string | null;
  position: string | null;
  role: Role;
  telegramLinked: boolean;
  telegramLinkCode: string | null;
  joinedAt: string;
}

export interface Tag {
  id: number;
  name: string;
  color: string | null;
}

export interface Task {
  id: number;
  title: string;
  description: string | null;
  priority: TaskPriority;
  status: TaskStatus;
  createdById: number;
  createdByName: string;
  assigneeId: number | null;
  assigneeName: string | null;
  tags: Tag[];
  deadline: string | null;
  createdAt: string;
  takenAt: string | null;
  completedAt: string | null;
}

export interface EmployeeStats {
  employeeId: number;
  fullName: string;
  taken: number;
  completed: number;
  inProgress: number;
  overdue: number;
  avgCompletionHours: number | null;
}

export interface MonthlyStats {
  year: number;
  month: number;
  totalCreated: number;
  totalCompleted: number;
  totalOpen: number;
  totalInProgress: number;
  totalTesting: number;
  perEmployee: EmployeeStats[];
}

export interface Invite {
  id: number;
  token: string;
  role: Role;
  createdAt: string;
  expiresAt: string | null;
  createdByName: string;
}

export interface InviteInfo {
  teamName: string;
  role: Role;
  alreadyMember: boolean;
}

export interface TaskBody {
  title: string;
  description?: string;
  priority?: TaskPriority;
  deadline?: string | null;
  tagIds?: number[];
  assigneeId?: number | null;
}

export interface TeamAdmin {
  id: number;
  name: string;
  memberCount: number;
  createdAt: string;
}

export function isManagerRole(role: Role | undefined): boolean {
  return role === 'LEADER' || role === 'MANAGER';
}
