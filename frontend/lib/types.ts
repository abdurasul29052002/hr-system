export type Role = 'LEADER' | 'MANAGER' | 'MEMBER';
export type Language = 'EN' | 'RU' | 'UZ';
export type TaskStatus = 'PENDING' | 'OPEN' | 'IN_PROGRESS' | 'TESTING' | 'DONE' | 'CANCELLED';
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

export interface MemberLabel {
  id: number;
  name: string;
  color: string | null;
}

export interface Member {
  employeeId: number;
  fullName: string;
  username: string;
  phone: string | null;
  position: string | null;
  role: Role;
  labels: MemberLabel[];
  telegramLinked: boolean;
  telegramLinkCode: string | null;
  joinedAt: string;
}

/** Minimal roster entry any team member can read, for @mention autocomplete. */
export interface MentionMember {
  employeeId: number;
  fullName: string;
  username: string;
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
  reviewerId: number | null;
  reviewerName: string | null;
  tags: Tag[];
  deadline: string | null;
  createdAt: string;
  takenAt: string | null;
  submittedAt: string | null;
  completedAt: string | null;
}

export interface TaskBrief {
  id: number;
  title: string;
  status: TaskStatus;
  overdue: boolean;
}

export interface EmployeeStats {
  employeeId: number;
  fullName: string;
  taken: number;
  completed: number;
  inProgress: number;
  testing: number;
  cancelled: number;
  overdue: number;
  onTime: number;
  reviewed: number;
  avgCompletionHours: number | null;
  tasks: TaskBrief[];
}

export interface StatusSlice {
  category: string;
  count: number;
}

export interface MonthlyStats {
  year: number;
  month: number;
  totalCreated: number;
  totalCompleted: number;
  totalOpen: number;
  totalInProgress: number;
  totalTesting: number;
  totalCancelled: number;
  totalOverdue: number;
  employeeOfMonth: string | null;
  perEmployee: EmployeeStats[];
  distribution: StatusSlice[];
}

export interface ActiveTask {
  id: number;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  deadline: string | null;
  takenAt: string | null;
}

export interface MemberActivity {
  employeeId: number;
  fullName: string;
  position: string | null;
  labels: MemberLabel[];
  activeTasks: ActiveTask[];
}

export interface TimelineTask {
  id: number;
  title: string;
  assigneeName: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  createdAt: string;
  takenAt: string | null;
  submittedAt: string | null;
  completedAt: string | null;
  deadline: string | null;
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
  reviewerId?: number | null;
}

export interface TeamAdmin {
  id: number;
  name: string;
  memberCount: number;
  createdAt: string;
}

export type JoinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface TeamSearchResult {
  id: number;
  name: string;
  memberCount: number;
  createdAt: string;
}

export interface TeamJoinRequest {
  id: number;
  teamId: number;
  teamName: string;
  employeeId: number;
  employeeName: string;
  employeeUsername: string;
  status: JoinRequestStatus;
  createdAt: string;
}

export interface TaskComment {
  id: number;
  taskId: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string | null;
  mentionedEmployeeIds: number[];
  attachments: CommentAttachment[];
}

export interface CommentAttachment {
  id: number;
  fileName: string;
  fileSize: number;
  mimeType: string;
  uploadedAt: string;
  downloadUrl: string;
}

export interface CommentRequest {
  content: string;
}

export interface TaskAttachment {
  id: number;
  taskId: number;
  fileName: string;
  fileSize: number;
  mimeType: string;
  uploadedByName: string;
  uploadedAt: string;
  downloadUrl: string;
}

export type NotificationType =
  | 'MENTIONED'
  | 'TASK_ASSIGNED'
  | 'TASK_COMPLETED'
  | 'TASK_APPROVED'
  | 'TASK_REJECTED'
  | 'TASK_PROPOSED'
  | 'TEAM_JOIN_REQUESTED'
  | 'TEAM_JOIN_APPROVED'
  | 'TEAM_JOIN_REJECTED'
  | 'INVITE_RECEIVED'
  | 'COMMENT_ADDED'
  | 'TICKET_REPLY'
  | 'TICKET_STATUS';

export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  message: string | null;
  relatedTaskId: number | null;
  relatedCommentId: number | null;
  relatedInviteId: number | null;
  actorName: string | null;
  isRead: boolean;
  createdAt: string;
}

export type TicketType = 'SUGGESTION' | 'BUG' | 'ISSUE' | 'PERMISSION_REQUEST' | 'OTHER';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Ticket {
  id: number;
  creatorId: number;
  creatorName: string;
  subject: string;
  description: string;
  type: TicketType;
  priority: TicketPriority;
  status: TicketStatus;
  createdAt: string;
  updatedAt: string | null;
  resolvedAt: string | null;
  resolvedByName: string | null;
  messageCount: number;
}

export interface TicketDetail extends Ticket {
  messages: TicketMessage[];
}

export interface TicketMessage {
  id: number;
  senderId: number;
  senderName: string;
  message: string;
  isAdminResponse: boolean;
  createdAt: string;
}

export interface CreateTicketRequest {
  subject: string;
  description: string;
  type: TicketType;
  priority: TicketPriority;
}

export interface TicketStats {
  openCount: number;
  inProgressCount: number;
  resolvedCount: number;
  totalCount: number;
}

export function isManagerRole(role: Role | undefined): boolean {
  return role === 'LEADER' || role === 'MANAGER';
}
