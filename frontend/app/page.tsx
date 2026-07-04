'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Task, TaskStatus } from '@/lib/types';
import '@/lib/i18n';

export default function TasksPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [mineOnly, setMineOnly] = useState(false);

  const employee = getStoredEmployee();
  const membership = getCurrentMembership(employee);
  const isManager = isManagerRole(membership?.role);

  useEffect(() => {
    // Redirect admin to admin page
    if (employee?.admin) {
      router.push('/admin');
      return;
    }

    loadTasks();
  }, [mineOnly, employee, router]);

  const loadTasks = async () => {
    try {
      const data = await api.tasks({ mine: mineOnly });
      setTasks(data);
    } catch (error) {
      console.error('Failed to load tasks:', error);
    } finally {
      setLoading(false);
    }
  };

  const groupTasksByStatus = () => {
    const groups: Record<TaskStatus, Task[]> = {
      OPEN: [],
      IN_PROGRESS: [],
      TESTING: [],
      DONE: [],
      CANCELLED: [],
    };

    tasks.forEach((task) => {
      groups[task.status].push(task);
    });

    return groups;
  };

  const handleTaskAction = async (taskId: number, action: string) => {
    try {
      switch (action) {
        case 'take':
          await api.takeTask(taskId);
          break;
        case 'complete':
          await api.completeTask(taskId);
          break;
        case 'release':
          await api.releaseTask(taskId);
          break;
        case 'approve':
          await api.approveTask(taskId);
          break;
        case 'reject':
          await api.rejectTask(taskId);
          break;
        case 'cancel':
          await api.cancelTask(taskId);
          break;
      }
      loadTasks();
    } catch (error) {
      console.error(`Failed to ${action} task:`, error);
      alert(error instanceof Error ? error.message : `Failed to ${action} task`);
    }
  };

  const grouped = groupTasksByStatus();

  const TaskCard = ({ task }: { task: Task }) => (
    <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
      <div className="flex items-start justify-between mb-2">
        <h3 className="font-semibold text-gray-900">{task.title}</h3>
        <span
          className={`text-xs px-2 py-1 rounded ${
            task.priority === 'HIGH'
              ? 'bg-red-100 text-red-700'
              : task.priority === 'MEDIUM'
              ? 'bg-yellow-100 text-yellow-700'
              : 'bg-gray-100 text-gray-700'
          }`}
        >
          {t(`tasks.prio.${task.priority}`)}
        </span>
      </div>

      {task.description && (
        <p className="text-sm text-gray-600 mb-2">{task.description}</p>
      )}

      <div className="text-xs text-gray-500 mb-3">
        <div>{t('tasks.createdBy')}: {task.createdByName}</div>
        {task.assigneeName && <div>{t('tasks.assignee')}: {task.assigneeName}</div>}
        {task.deadline && <div>{t('tasks.deadline')}: {new Date(task.deadline).toLocaleDateString()}</div>}
      </div>

      {task.tags.length > 0 && (
        <div className="flex flex-wrap gap-1 mb-3">
          {task.tags.map((tag) => (
            <span
              key={tag.id}
              className="text-xs px-2 py-1 rounded"
              style={{ backgroundColor: tag.color || '#e5e7eb', color: '#1f2937' }}
            >
              {tag.name}
            </span>
          ))}
        </div>
      )}

      <div className="flex gap-2">
        {task.status === 'OPEN' && !task.assigneeId && (
          <button
            onClick={() => handleTaskAction(task.id, 'take')}
            className="text-xs px-3 py-1 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            {t('tasks.take')}
          </button>
        )}

        {task.status === 'IN_PROGRESS' && task.assigneeId === employee?.id && (
          <>
            <button
              onClick={() => handleTaskAction(task.id, 'complete')}
              className="text-xs px-3 py-1 bg-green-600 text-white rounded hover:bg-green-700"
            >
              {t('tasks.complete')}
            </button>
            <button
              onClick={() => handleTaskAction(task.id, 'release')}
              className="text-xs px-3 py-1 bg-gray-600 text-white rounded hover:bg-gray-700"
            >
              {t('tasks.release')}
            </button>
          </>
        )}

        {task.status === 'TESTING' && isManager && (
          <>
            <button
              onClick={() => handleTaskAction(task.id, 'approve')}
              className="text-xs px-3 py-1 bg-green-600 text-white rounded hover:bg-green-700"
            >
              {t('tasks.approve')}
            </button>
            <button
              onClick={() => handleTaskAction(task.id, 'reject')}
              className="text-xs px-3 py-1 bg-yellow-600 text-white rounded hover:bg-yellow-700"
            >
              {t('tasks.reject')}
            </button>
          </>
        )}

        {isManager && task.status !== 'DONE' && task.status !== 'CANCELLED' && (
          <button
            onClick={() => handleTaskAction(task.id, 'cancel')}
            className="text-xs px-3 py-1 bg-red-600 text-white rounded hover:bg-red-700"
          >
            {t('tasks.cancel')}
          </button>
        )}
      </div>
    </div>
  );

  if (loading) {
    return (
      <DashboardLayout>
        <div className="text-center py-8">Loading...</div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900">{t('tasks.board')}</h2>
        <div className="flex gap-4">
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(e) => setMineOnly(e.target.checked)}
              className="rounded"
            />
            <span className="text-sm">{t('tasks.mineOnly')}</span>
          </label>
          {isManager && (
            <button className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
              {t('tasks.newTask')}
            </button>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-4">
        {/* Open */}
        <div>
          <h3 className="font-semibold text-gray-700 mb-3">{t('tasks.open')}</h3>
          <div className="space-y-3">
            {grouped.OPEN.length === 0 ? (
              <p className="text-sm text-gray-500">{t('tasks.empty')}</p>
            ) : (
              grouped.OPEN.map((task) => <TaskCard key={task.id} task={task} />)
            )}
          </div>
        </div>

        {/* In Progress */}
        <div>
          <h3 className="font-semibold text-gray-700 mb-3">{t('tasks.inProgress')}</h3>
          <div className="space-y-3">
            {grouped.IN_PROGRESS.length === 0 ? (
              <p className="text-sm text-gray-500">{t('tasks.empty')}</p>
            ) : (
              grouped.IN_PROGRESS.map((task) => <TaskCard key={task.id} task={task} />)
            )}
          </div>
        </div>

        {/* Testing */}
        <div>
          <h3 className="font-semibold text-gray-700 mb-3">{t('tasks.testing')}</h3>
          <div className="space-y-3">
            {grouped.TESTING.length === 0 ? (
              <p className="text-sm text-gray-500">{t('tasks.empty')}</p>
            ) : (
              grouped.TESTING.map((task) => <TaskCard key={task.id} task={task} />)
            )}
          </div>
        </div>

        {/* Done */}
        <div>
          <h3 className="font-semibold text-gray-700 mb-3">{t('tasks.done')}</h3>
          <div className="space-y-3">
            {grouped.DONE.length === 0 ? (
              <p className="text-sm text-gray-500">{t('tasks.empty')}</p>
            ) : (
              grouped.DONE.map((task) => <TaskCard key={task.id} task={task} />)
            )}
          </div>
        </div>

        {/* Cancelled */}
        <div>
          <h3 className="font-semibold text-gray-700 mb-3">{t('tasks.cancelled')}</h3>
          <div className="space-y-3">
            {grouped.CANCELLED.length === 0 ? (
              <p className="text-sm text-gray-500">{t('tasks.empty')}</p>
            ) : (
              grouped.CANCELLED.map((task) => <TaskCard key={task.id} task={task} />)
            )}
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
