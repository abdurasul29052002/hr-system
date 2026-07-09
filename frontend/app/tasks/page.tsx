'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Member, MentionMember, Tag, Task, TaskPriority, TaskStatus } from '@/lib/types';
import { Avatar, Badge, Button, Field, Input, Modal, PageHeader, PageLoader, Select, Textarea } from '@/components/ui';
import TaskCommentSection from '@/components/TaskCommentSection';
import TaskAttachmentSection from '@/components/TaskAttachmentSection';
import '@/lib/i18n';

const TITLE_MAX = 120;

const COLUMNS: { status: TaskStatus; dot: string; key: string }[] = [
  { status: 'OPEN', dot: 'bg-blue-500', key: 'tasks.open' },
  { status: 'IN_PROGRESS', dot: 'bg-amber-500', key: 'tasks.inProgress' },
  { status: 'TESTING', dot: 'bg-violet-500', key: 'tasks.testing' },
  { status: 'DONE', dot: 'bg-emerald-500', key: 'tasks.done' },
];

const PRIO_COLOR: Record<TaskPriority, 'red' | 'amber' | 'slate'> = { HIGH: 'red', MEDIUM: 'amber', LOW: 'slate' };

export default function TasksPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [mentionMembers, setMentionMembers] = useState<MentionMember[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(true);
  const [mineOnly, setMineOnly] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [editTask, setEditTask] = useState<Task | null>(null);
  const [showPropose, setShowPropose] = useState(false);
  const [detailTask, setDetailTask] = useState<Task | null>(null);

  const employee = getStoredEmployee();
  const membership = getCurrentMembership(employee);
  const isManager = isManagerRole(membership?.role);
  const myId = employee?.id;

  const load = useCallback(async () => {
    try {
      setTasks(await api.tasks({ mine: mineOnly }));
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [mineOnly]);

  useEffect(() => {
    if (employee?.admin) {
      router.push('/admin');
      return;
    }
    load();
  }, [load, employee?.admin, router]);

  useEffect(() => {
    // Every team member can read the lightweight roster (for @mention autocomplete in comments).
    api.mentionableMembers().then(setMentionMembers).catch(() => undefined);
    // The full roster + tags are manager-only (assignee pickers, task form).
    if (isManager) {
      api.members().then(setMembers).catch(() => undefined);
      api.tags().then(setTags).catch(() => undefined);
    }
  }, [isManager]);

  const act = async (id: number, fn: () => Promise<unknown>) => {
    try {
      await fn();
      await load();
      setDetailTask(null);
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Error');
    }
  };

  const pending = tasks.filter((tk) => tk.status === 'PENDING');
  const cancelled = tasks.filter((tk) => tk.status === 'CANCELLED');
  // A member with nothing on their plate (no proposal, nothing in progress / in review) is nudged to report.
  const hasOwnOngoing = tasks.some(
    (tk) => tk.assigneeId === myId && ['PENDING', 'IN_PROGRESS', 'TESTING'].includes(tk.status),
  );
  const showProposePrompt = !isManager && !hasOwnOngoing;

  if (loading) {
    return <DashboardLayout><PageLoader /></DashboardLayout>;
  }

  return (
    <DashboardLayout>
      <PageHeader
        title={t('tasks.board')}
        actions={
          <>
            <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
              <input type="checkbox" checked={mineOnly} onChange={(e) => setMineOnly(e.target.checked)} className="accent-brand-600" />
              {t('tasks.mineOnly')}
            </label>
            {isManager && <Button onClick={() => setShowCreate(true)}>+ {t('tasks.newTask')}</Button>}
          </>
        }
      />

      {/* "What are you working on?" nudge for members with nothing active */}
      {showProposePrompt && (
        <div className="mb-4 flex flex-wrap items-center gap-3 rounded-xl border border-brand-200 bg-brand-50 px-4 py-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-600 text-white">
            <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" /></svg>
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-semibold text-slate-900">{t('tasks.whatWorkingOn')}</p>
            <p className="text-xs text-slate-500">{t('tasks.proposeHint')}</p>
          </div>
          <Button size="sm" onClick={() => setShowPropose(true)}>{t('tasks.report')}</Button>
        </div>
      )}

      {/* Proposals awaiting a leader's confirmation */}
      {pending.length > 0 && (
        <div className="mb-4 rounded-xl border border-dashed border-amber-300 bg-amber-50/60 p-3">
          <div className="mb-2 flex items-center gap-2 px-1">
            <span className="h-2.5 w-2.5 rounded-full bg-amber-500" />
            <h3 className="text-sm font-semibold text-amber-800">{t('tasks.pendingApproval')}</h3>
            <span className="ml-auto rounded-full bg-white px-2 text-xs font-medium text-amber-600">{pending.length}</span>
          </div>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
            {pending.map((task) => (
              <PendingCard key={task.id} task={task} isManager={isManager} onOpen={() => setDetailTask(task)} onAction={act} />
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {COLUMNS.map((col) => {
          const items = tasks.filter((tk) => tk.status === col.status);
          return (
            <div key={col.status} className="rounded-xl bg-slate-200/40 p-3">
              <div className="mb-3 flex items-center gap-2 px-1">
                <span className={`h-2.5 w-2.5 rounded-full ${col.dot}`} />
                <h3 className="text-sm font-semibold text-slate-700">{t(col.key)}</h3>
                <span className="ml-auto rounded-full bg-white px-2 text-xs font-medium text-slate-500">{items.length}</span>
              </div>
              <div className="space-y-2.5">
                {items.length === 0 ? (
                  <p className="px-1 py-6 text-center text-xs text-slate-400">{t('tasks.empty')}</p>
                ) : (
                  items.map((task) => (
                    <TaskCard key={task.id} task={task} onOpen={() => setDetailTask(task)} />
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>

      {cancelled.length > 0 && (
        <details className="mt-4">
          <summary className="cursor-pointer text-sm text-slate-500">{t('tasks.cancelled')} ({cancelled.length})</summary>
          <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-4">
            {cancelled.map((task) => <TaskCard key={task.id} task={task} onOpen={() => setDetailTask(task)} muted />)}
          </div>
        </details>
      )}

      {(showCreate || editTask) && (
        <TaskFormModal
          members={members}
          tags={tags}
          editTask={editTask}
          onClose={() => { setShowCreate(false); setEditTask(null); }}
          onSaved={() => { setShowCreate(false); setEditTask(null); setDetailTask(null); load(); }}
        />
      )}

      {showPropose && (
        <ProposeModal onClose={() => setShowPropose(false)} onDone={() => { setShowPropose(false); load(); }} />
      )}

      {detailTask && (
        <TaskDetailModal
          task={detailTask}
          isManager={isManager}
          myId={myId}
          members={members}
          mentionMembers={mentionMembers}
          onClose={() => setDetailTask(null)}
          onEdit={() => { setEditTask(detailTask); setDetailTask(null); }}
          onAction={act}
        />
      )}
    </DashboardLayout>
  );
}

function TaskCard({ task, onOpen, muted }: { task: Task; onOpen: () => void; muted?: boolean }) {
  const { t } = useTranslation();
  return (
    <button
      onClick={onOpen}
      className={`block w-full rounded-lg border border-slate-200 bg-white p-3 text-left shadow-sm transition-shadow hover:shadow-md ${muted ? 'opacity-60' : ''}`}
    >
      <div className="mb-1 flex items-start justify-between gap-2">
        <h4 className="text-sm font-semibold leading-snug text-slate-900">{task.title}</h4>
        <Badge color={PRIO_COLOR[task.priority]}>{t(`tasks.prio.${task.priority}`)}</Badge>
      </div>
      {task.description && (
        <p className="mb-2 line-clamp-2 text-xs leading-relaxed text-slate-500">{task.description}</p>
      )}
      {task.tags.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1">
          {task.tags.map((tag) => (
            <span key={tag.id} className="rounded-full px-2 py-0.5 text-[11px] font-medium text-white" style={{ background: tag.color || '#64748b' }}>
              {tag.name}
            </span>
          ))}
        </div>
      )}
      <div className="flex items-center gap-2 text-xs text-slate-500">
        {task.assigneeName ? (
          <span className="flex items-center gap-1"><Avatar name={task.assigneeName} size={5} /> {task.assigneeName}</span>
        ) : (
          <span className="text-slate-400">{t('tasks.unassigned')}</span>
        )}
        {task.reviewerName && (
          <span className="flex items-center gap-1 text-slate-400" title={t('tasks.reviewer')}>
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
            {task.reviewerName}
          </span>
        )}
        {task.deadline && <span className="ml-auto">📅 {new Date(task.deadline).toLocaleDateString()}</span>}
      </div>
    </button>
  );
}

function PendingCard({ task, isManager, onOpen, onAction }: {
  task: Task; isManager: boolean; onOpen: () => void;
  onAction: (id: number, fn: () => Promise<unknown>) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="rounded-lg border border-amber-200 bg-white p-3 shadow-sm">
      <button onClick={onOpen} className="block w-full text-left">
        <h4 className="text-sm font-semibold leading-snug text-slate-900">{task.title}</h4>
        {task.description && <p className="mt-1 line-clamp-2 text-xs text-slate-500">{task.description}</p>}
        {task.assigneeName && (
          <span className="mt-2 flex items-center gap-1 text-xs text-slate-500"><Avatar name={task.assigneeName} size={5} /> {task.assigneeName}</span>
        )}
      </button>
      {isManager ? (
        <div className="mt-2.5 flex gap-2">
          <Button size="sm" variant="success" onClick={() => onAction(task.id, () => api.approveProposal(task.id))}>{t('tasks.confirmProposal')}</Button>
          <Button size="sm" variant="danger" onClick={() => onAction(task.id, () => api.rejectProposal(task.id))}>{t('tasks.declineProposal')}</Button>
        </div>
      ) : (
        <p className="mt-2 text-[11px] italic text-amber-600">{t('tasks.awaitingApproval')}</p>
      )}
    </div>
  );
}

function TaskActions({ task, isManager, myId, members, onAction }: {
  task: Task; isManager: boolean; myId?: number; members: Member[];
  onAction: (id: number, fn: () => Promise<unknown>) => void;
}) {
  const { t } = useTranslation();
  const isMine = task.assigneeId != null && task.assigneeId === myId;
  const isReviewer = task.reviewerId != null && task.reviewerId === myId;
  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* OPEN + assigned to me → Start; OPEN + unassigned → Take (grab from pool) */}
      {task.status === 'OPEN' && isMine && (
        <Button size="sm" onClick={() => onAction(task.id, () => api.takeTask(task.id))}>{t('tasks.start')}</Button>
      )}
      {task.status === 'OPEN' && !isManager && !task.assigneeId && (
        <Button size="sm" onClick={() => onAction(task.id, () => api.takeTask(task.id))}>{t('tasks.take')}</Button>
      )}
      {task.status === 'OPEN' && isManager && members.length > 0 && (
        <Select
          className="w-auto"
          value=""
          onChange={(e) => e.target.value && onAction(task.id, () => api.assignTask(task.id, Number(e.target.value)))}
        >
          <option value="">{t('tasks.assignTo')}…</option>
          {members.map((m) => <option key={m.employeeId} value={m.employeeId}>{m.fullName}</option>)}
        </Select>
      )}
      {task.status === 'IN_PROGRESS' && (isMine || isManager) && (
        <Button size="sm" variant="success" onClick={() => onAction(task.id, () => api.completeTask(task.id))}>{t('tasks.complete')}</Button>
      )}
      {task.status === 'IN_PROGRESS' && isMine && (
        <Button size="sm" variant="subtle" onClick={() => onAction(task.id, () => api.releaseTask(task.id))}>{t('tasks.release')}</Button>
      )}
      {task.status === 'TESTING' && (isManager || isReviewer) && (
        <>
          <Button size="sm" variant="success" onClick={() => onAction(task.id, () => api.approveTask(task.id))}>{t('tasks.approve')}</Button>
          <Button size="sm" variant="subtle" onClick={() => onAction(task.id, () => api.rejectTask(task.id))}>{t('tasks.reject')}</Button>
        </>
      )}
      {isManager && task.status !== 'DONE' && task.status !== 'CANCELLED' && task.status !== 'PENDING' && (
        <Button size="sm" variant="danger" onClick={() => onAction(task.id, () => api.cancelTask(task.id))}>{t('tasks.cancel')}</Button>
      )}
    </div>
  );
}

function TaskDetailModal({ task, isManager, myId, members, mentionMembers, onClose, onEdit, onAction }: {
  task: Task; isManager: boolean; myId?: number; members: Member[]; mentionMembers: MentionMember[];
  onClose: () => void; onEdit: () => void; onAction: (id: number, fn: () => Promise<unknown>) => void;
}) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<'comments' | 'attachments'>('comments');
  const reviewed = task.status === 'DONE' && task.reviewerName;
  return (
    <Modal open onClose={onClose} title={task.title} size="lg">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-2">
          <Badge color={PRIO_COLOR[task.priority]}>{t(`tasks.prio.${task.priority}`)}</Badge>
          {task.tags.map((tag) => (
            <span key={tag.id} className="rounded-full px-2 py-0.5 text-xs font-medium text-white" style={{ background: tag.color || '#64748b' }}>{tag.name}</span>
          ))}
          {isManager && task.status !== 'PENDING' && (
            <Button size="sm" variant="ghost" className="ml-auto" onClick={onEdit}>✎ {t('tasks.edit')}</Button>
          )}
        </div>
        {task.description && <p className="whitespace-pre-wrap text-sm text-slate-700">{task.description}</p>}
        <div className="grid grid-cols-2 gap-3 rounded-lg bg-slate-50 p-3 text-sm sm:grid-cols-3">
          <div><p className="text-xs text-slate-400">{t('tasks.createdBy')}</p><p className="text-slate-700">{task.createdByName}</p></div>
          <div><p className="text-xs text-slate-400">{t('tasks.assignee')}</p><p className="text-slate-700">{task.assigneeName || t('tasks.unassigned')}</p></div>
          <div>
            <p className="text-xs text-slate-400">{reviewed ? t('tasks.reviewedBy') : t('tasks.reviewer')}</p>
            <p className="text-slate-700">{task.reviewerName || t('common.none')}</p>
          </div>
          {task.deadline && <div><p className="text-xs text-slate-400">{t('tasks.deadline')}</p><p className="text-slate-700">{new Date(task.deadline).toLocaleDateString()}</p></div>}
        </div>

        <TaskActions task={task} isManager={isManager} myId={myId} members={members} onAction={onAction} />

        <div className="border-t border-slate-100 pt-3">
          <div className="mb-3 flex gap-1 rounded-lg bg-slate-100 p-0.5">
            {(['comments', 'attachments'] as const).map((tb) => (
              <button
                key={tb}
                onClick={() => setTab(tb)}
                className={`flex-1 rounded-md py-1.5 text-sm font-medium ${tab === tb ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500'}`}
              >
                {t(tb === 'comments' ? 'tasks.comments' : 'tasks.attachments')}
              </button>
            ))}
          </div>
          {tab === 'comments' ? <TaskCommentSection taskId={task.id} members={mentionMembers} /> : <TaskAttachmentSection taskId={task.id} />}
        </div>
      </div>
    </Modal>
  );
}

/** Create or edit a task. In edit mode, prefills from the task, saves via updateTask, and hides uploads. */
function TaskFormModal({ members, tags, editTask, onClose, onSaved }: {
  members: Member[]; tags: Tag[]; editTask: Task | null; onClose: () => void; onSaved: () => void;
}) {
  const { t } = useTranslation();
  const editing = editTask != null;
  const [title, setTitle] = useState(editTask?.title ?? '');
  const [description, setDescription] = useState(editTask?.description ?? '');
  const [priority, setPriority] = useState<TaskPriority>(editTask?.priority ?? 'MEDIUM');
  const [deadline, setDeadline] = useState(editTask?.deadline ?? '');
  const [assigneeId, setAssigneeId] = useState<number | ''>(editTask?.assigneeId ?? '');
  const [reviewerId, setReviewerId] = useState<number | ''>(editTask?.reviewerId ?? '');
  const [tagIds, setTagIds] = useState<number[]>(editTask?.tags.map((tg) => tg.id) ?? []);
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const toggleTag = (id: number) => setTagIds((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const body = {
        title,
        description: description || undefined,
        priority,
        deadline: deadline || null,
        tagIds,
        assigneeId: assigneeId === '' ? null : assigneeId,
        reviewerId: reviewerId === '' ? null : reviewerId,
      };
      if (editing) {
        await api.updateTask(editTask!.id, body);
      } else {
        const task = await api.createTask(body);
        for (const file of files) {
          await api.uploadAttachment(task.id, file).catch(() => undefined);
        }
      }
      onSaved();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={editing ? t('tasks.editTask') : t('tasks.newTask')} size="lg">
      <form onSubmit={submit} className="space-y-3">
        <Field label={t('tasks.title')}>
          <Input value={title} onChange={(e) => setTitle(e.target.value.slice(0, TITLE_MAX))} maxLength={TITLE_MAX} required autoFocus />
          <p className="mt-1 text-right text-[11px] text-slate-400">{title.length}/{TITLE_MAX}</p>
        </Field>
        <Field label={t('tasks.description')}>
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} placeholder={t('tasks.descriptionHint')} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('tasks.priority')}>
            <Select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
              <option value="LOW">{t('tasks.prio.LOW')}</option>
              <option value="MEDIUM">{t('tasks.prio.MEDIUM')}</option>
              <option value="HIGH">{t('tasks.prio.HIGH')}</option>
            </Select>
          </Field>
          <Field label={t('tasks.deadline')}>
            <Input type="date" value={deadline ?? ''} onChange={(e) => setDeadline(e.target.value)} />
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t('tasks.assignee')}>
            <Select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('tasks.openPool')}</option>
              {members.map((m) => <option key={m.employeeId} value={m.employeeId}>{m.fullName}</option>)}
            </Select>
          </Field>
          <Field label={t('tasks.reviewer')}>
            <Select value={reviewerId} onChange={(e) => setReviewerId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('tasks.noReviewer')}</option>
              {members.filter((m) => m.employeeId !== assigneeId).map((m) => <option key={m.employeeId} value={m.employeeId}>{m.fullName}</option>)}
            </Select>
          </Field>
        </div>
        {tags.length > 0 && (
          <Field label={t('tasks.tags')}>
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => {
                const on = tagIds.includes(tag.id);
                return (
                  <button
                    key={tag.id}
                    type="button"
                    onClick={() => toggleTag(tag.id)}
                    className={`rounded-full px-2.5 py-1 text-xs font-medium text-white transition-opacity ${on ? 'opacity-100 ring-2 ring-offset-1 ring-slate-400' : 'opacity-50'}`}
                    style={{ background: tag.color || '#64748b' }}
                  >
                    {on ? '✓ ' : ''}{tag.name}
                  </button>
                );
              })}
            </div>
          </Field>
        )}
        {!editing && (
          <Field label={t('tasks.attachImages')}>
            <label className="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 py-3 text-sm text-slate-500 hover:bg-slate-50">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14M4 6h16v12H4z" /></svg>
              {t('tasks.attachImages')}
              <input type="file" accept="image/*" multiple className="hidden" onChange={(e) => setFiles(Array.from(e.target.files || []))} />
            </label>
            {files.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {files.map((f, i) => (
                  <span key={i} className="rounded-md bg-brand-50 px-2 py-1 text-xs text-brand-700">{f.name}</span>
                ))}
              </div>
            )}
          </Field>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy || !title.trim()}>{busy ? t('common.saving') : editing ? t('common.save') : t('common.create')}</Button>
        </div>
      </form>
    </Modal>
  );
}

/** A member reports what they're working on — becomes a proposal for a leader to confirm. */
function ProposeModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await api.proposeTask({ title, description: description || undefined });
      onDone();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
      setBusy(false);
    }
  };

  return (
    <Modal open onClose={onClose} title={t('tasks.whatWorkingOn')} size="sm">
      <form onSubmit={submit} className="space-y-3">
        <p className="text-sm text-slate-500">{t('tasks.proposeModalHint')}</p>
        <Field label={t('tasks.title')}>
          <Input value={title} onChange={(e) => setTitle(e.target.value.slice(0, TITLE_MAX))} maxLength={TITLE_MAX} required autoFocus />
        </Field>
        <Field label={t('tasks.description')}>
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
        </Field>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy || !title.trim()}>{busy ? t('common.saving') : t('tasks.sendForApproval')}</Button>
        </div>
      </form>
    </Modal>
  );
}
