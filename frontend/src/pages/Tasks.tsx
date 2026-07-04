import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, getCurrentMembership, getStoredEmployee, isManagerRole, Member, Tag, Task, TaskPriority, TaskStatus } from '../api';

const COLUMNS: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'TESTING', 'DONE'];

const COLUMN_LABEL: Record<TaskStatus, string> = {
  OPEN: 'tasks.open',
  IN_PROGRESS: 'tasks.inProgress',
  TESTING: 'tasks.testing',
  DONE: 'tasks.done',
  CANCELLED: 'tasks.cancelled',
};

export default function Tasks() {
  const { t } = useTranslation();
  const employee = getStoredEmployee();
  const isManager = isManagerRole(getCurrentMembership(employee)?.role);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [mineOnly, setMineOnly] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setTasks(await api.tasks(mineOnly ? { mine: true } : undefined));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [mineOnly]);

  useEffect(() => {
    load();
    const timer = setInterval(load, 15000);
    return () => clearInterval(timer);
  }, [load]);

  useEffect(() => {
    if (isManager) {
      api
        .members()
        .then(setMembers)
        .catch(() => undefined);
    }
  }, [isManager]);

  const act = async (action: () => Promise<Task>) => {
    setError('');
    try {
      await action();
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>{t('tasks.board')}</h1>
        <div className="page-actions">
          <label className="checkbox">
            <input type="checkbox" checked={mineOnly} onChange={(e) => setMineOnly(e.target.checked)} />
            {t('tasks.mineOnly')}
          </label>
          {isManager && (
            <button className="btn btn-primary" onClick={() => setShowModal(true)}>
              + {t('tasks.newTask')}
            </button>
          )}
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="board">
        {COLUMNS.map((status) => {
          const columnTasks = tasks.filter((task) => task.status === status);
          return (
            <div key={status} className="column">
              <div className={`column-header column-${status.toLowerCase()}`}>
                {t(COLUMN_LABEL[status])} <span className="count">{columnTasks.length}</span>
              </div>
              {columnTasks.length === 0 && <div className="column-empty">{t('tasks.empty')}</div>}
              {columnTasks.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  isManager={isManager}
                  myId={employee?.id}
                  members={members}
                  onAction={act}
                />
              ))}
            </div>
          );
        })}
      </div>
      {showModal && (
        <NewTaskModal
          members={members}
          onClose={() => setShowModal(false)}
          onCreated={() => {
            setShowModal(false);
            load();
          }}
        />
      )}
    </div>
  );
}

function TaskCard({
  task,
  isManager,
  myId,
  members,
  onAction,
}: {
  task: Task;
  isManager: boolean;
  myId?: number;
  members: Member[];
  onAction: (action: () => Promise<Task>) => void;
}) {
  const { t } = useTranslation();
  const isMine = task.assigneeId != null && task.assigneeId === myId;

  return (
    <div className={`card prio-${task.priority.toLowerCase()}`}>
      <div className="card-top">
        <span className="task-id">#{task.id}</span>
        <span className={`chip chip-${task.priority.toLowerCase()}`}>{t(`tasks.prio.${task.priority}`)}</span>
      </div>
      <div className="card-title">{task.title}</div>
      {task.description && <div className="card-desc">{task.description}</div>}
      {task.tags.length > 0 && (
        <div className="card-tags">
          {task.tags.map((tag) => (
            <span key={tag.id} className="tag-chip" style={{ background: tag.color ?? '#64748b' }}>
              {tag.name}
            </span>
          ))}
        </div>
      )}
      <div className="card-meta">
        {task.deadline && (
          <span>
            📅 {t('tasks.deadline')}: {task.deadline}
          </span>
        )}
        {task.assigneeName && (
          <span>
            👤 {t('tasks.assignee')}: {task.assigneeName}
          </span>
        )}
      </div>
      <div className="card-actions">
        {task.status === 'OPEN' && !isManager && (
          <button className="btn btn-small btn-primary" onClick={() => onAction(() => api.takeTask(task.id))}>
            {t('tasks.take')}
          </button>
        )}
        {task.status === 'OPEN' && isManager && members.length > 0 && (
          <select
            className="assign-select"
            value=""
            onChange={(e) => {
              const employeeId = Number(e.target.value);
              if (employeeId) {
                onAction(() => api.assignTask(task.id, employeeId));
              }
            }}
          >
            <option value="">{t('tasks.assign')}…</option>
            {members.map((member) => (
              <option key={member.employeeId} value={member.employeeId}>
                {member.fullName}
              </option>
            ))}
          </select>
        )}
        {task.status === 'IN_PROGRESS' && (isMine || isManager) && (
          <>
            <button className="btn btn-small btn-success" onClick={() => onAction(() => api.completeTask(task.id))}>
              {t('tasks.complete')}
            </button>
            {isMine && (
              <button className="btn btn-small btn-ghost" onClick={() => onAction(() => api.releaseTask(task.id))}>
                {t('tasks.release')}
              </button>
            )}
          </>
        )}
        {task.status === 'TESTING' && isManager && (
          <>
            <button className="btn btn-small btn-success" onClick={() => onAction(() => api.approveTask(task.id))}>
              {t('tasks.approve')}
            </button>
            <button className="btn btn-small btn-ghost" onClick={() => onAction(() => api.rejectTask(task.id))}>
              {t('tasks.reject')}
            </button>
          </>
        )}
        {isManager && task.status !== 'DONE' && task.status !== 'CANCELLED' && (
          <button className="btn btn-small btn-danger" onClick={() => onAction(() => api.cancelTask(task.id))}>
            {t('tasks.cancel')}
          </button>
        )}
      </div>
    </div>
  );
}

function NewTaskModal({
  members,
  onClose,
  onCreated,
}: {
  members: Member[];
  onClose: () => void;
  onCreated: () => void;
}) {
  const { t } = useTranslation();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [deadline, setDeadline] = useState('');
  const [assigneeId, setAssigneeId] = useState<number | ''>('');
  const [tags, setTags] = useState<Tag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<number[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    api
      .tags()
      .then(setTags)
      .catch(() => undefined);
  }, []);

  const toggleTag = (id: number) => {
    setSelectedTagIds((current) =>
      current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
    );
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await api.createTask({
        title,
        description: description || undefined,
        priority,
        deadline: deadline || null,
        tagIds: selectedTagIds,
        assigneeId: assigneeId === '' ? null : assigneeId,
      });
      onCreated();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>{t('tasks.newTask')}</h2>
        <label>
          {t('tasks.title')}
          <input value={title} onChange={(e) => setTitle(e.target.value)} required autoFocus />
        </label>
        <label>
          {t('tasks.description')}
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} />
        </label>
        <div className="form-row">
          <label>
            {t('tasks.priority')}
            <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)}>
              <option value="LOW">{t('tasks.prio.LOW')}</option>
              <option value="MEDIUM">{t('tasks.prio.MEDIUM')}</option>
              <option value="HIGH">{t('tasks.prio.HIGH')}</option>
            </select>
          </label>
          <label>
            {t('tasks.deadline')}
            <input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
          </label>
        </div>
        <label>
          {t('tasks.assignee')}
          <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">{t('tasks.openPool')}</option>
            {members.map((member) => (
              <option key={member.employeeId} value={member.employeeId}>
                {member.fullName}
              </option>
            ))}
          </select>
        </label>
        {tags.length > 0 && (
          <label>
            {t('tasks.tags')}
            <div className="tag-picker">
              {tags.map((tag) => {
                const selected = selectedTagIds.includes(tag.id);
                return (
                  <button
                    key={tag.id}
                    type="button"
                    className={`tag-chip tag-option ${selected ? 'selected' : ''}`}
                    style={{ background: tag.color ?? '#64748b' }}
                    onClick={() => toggleTag(tag.id)}
                  >
                    {selected ? '✓ ' : ''}
                    {tag.name}
                  </button>
                );
              })}
            </div>
          </label>
        )}
        {error && <div className="error">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {t('tasks.close')}
          </button>
          <button type="submit" className="btn btn-primary">
            {t('tasks.save')}
          </button>
        </div>
      </form>
    </div>
  );
}
