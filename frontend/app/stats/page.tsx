'use client';

import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getCurrentMembership, getStoredEmployee } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { MemberActivity, MonthlyStats, TaskBrief, TaskPriority, TaskStatus, TimelineTask } from '@/lib/types';
import { Avatar, Badge, Button, Card, EmptyState, PageHeader, PageLoader, Select } from '@/components/ui';
import '@/lib/i18n';

const PRIO_COLOR: Record<TaskPriority, 'red' | 'amber' | 'slate'> = { HIGH: 'red', MEDIUM: 'amber', LOW: 'slate' };
const STATUS_COLOR: Record<TaskStatus, 'blue' | 'amber' | 'violet' | 'green' | 'slate'> = {
  OPEN: 'blue', IN_PROGRESS: 'amber', TESTING: 'violet', DONE: 'green', CANCELLED: 'slate',
};
const STATUS_KEY: Record<TaskStatus, string> = {
  OPEN: 'tasks.open', IN_PROGRESS: 'tasks.inProgress', TESTING: 'tasks.testing', DONE: 'tasks.done', CANCELLED: 'tasks.cancelled',
};

export default function StatsPage() {
  const { t } = useTranslation();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [stats, setStats] = useState<MonthlyStats | null>(null);
  const [activity, setActivity] = useState<MemberActivity[]>([]);
  const [timeline, setTimeline] = useState<TimelineTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [downloading, setDownloading] = useState(false);

  const isManager = isManagerRole(getCurrentMembership(getStoredEmployee())?.role);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // Only monthlyStats is critical; isolate the others so one failing call can't blank the whole page.
      const [s, a, tl] = await Promise.all([
        api.monthlyStats(year, month),
        api.currentActivity().catch(() => []),
        api.timeline(year, month).catch(() => []),
      ]);
      setStats(s);
      setActivity(a);
      setTimeline(tl);
    } catch {
      setStats(null);
    } finally {
      setLoading(false);
    }
  }, [year, month]);

  useEffect(() => { load(); }, [load]);

  const download = async () => {
    setDownloading(true);
    try { await api.downloadReport(year, month); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); } finally { setDownloading(false); }
  };

  const years = Array.from({ length: 5 }, (_, i) => now.getFullYear() - i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);

  const cards = stats ? [
    { label: t('stats.created'), value: stats.totalCreated, color: 'text-slate-900' },
    { label: t('stats.completed'), value: stats.totalCompleted, color: 'text-emerald-600' },
    { label: t('stats.inProgress'), value: stats.totalInProgress, color: 'text-amber-600' },
    { label: t('stats.testing'), value: stats.totalTesting, color: 'text-violet-600' },
    { label: t('stats.overdue'), value: stats.totalOverdue, color: 'text-red-600' },
    { label: t('stats.cancelled'), value: stats.totalCancelled, color: 'text-slate-500' },
  ] : [];

  return (
    <DashboardLayout>
      <PageHeader
        title={t('stats.title')}
        actions={
          <>
            <Select className="w-auto" value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {years.map((y) => <option key={y} value={y}>{y}</option>)}
            </Select>
            <Select className="w-auto" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
              {months.map((m) => <option key={m} value={m}>{new Date(2000, m - 1).toLocaleDateString('en', { month: 'long' })}</option>)}
            </Select>
            {isManager && <Button onClick={download} disabled={downloading}>📄 {downloading ? t('common.saving') : t('stats.downloadReport')}</Button>}
          </>
        }
      />

      {loading ? (
        <PageLoader />
      ) : !stats ? (
        <EmptyState title={t('stats.empty')} />
      ) : (
        <div className="space-y-6">
          {/* Summary */}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
            {cards.map((c) => (
              <Card key={c.label} className="p-4">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{c.label}</p>
                <p className={`mt-1 text-3xl font-bold ${c.color}`}>{c.value}</p>
              </Card>
            ))}
          </div>

          {/* Overall task distribution — phone-storage-style proportional bar */}
          {stats.distribution.length > 0 && (() => {
            const total = stats.distribution.reduce((sum, s) => sum + s.count, 0) || 1;
            const label = (cat: string) => (cat === 'OVERDUE' ? t('stats.overdue') : t(STATUS_KEY[cat as TaskStatus]));
            return (
              <Card className="p-4">
                <div className="mb-2.5 flex items-baseline justify-between">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{t('stats.distribution')}</p>
                  <p className="text-xs text-slate-400">{total} {t('stats.tasksTotal')}</p>
                </div>
                <DistBar slices={stats.distribution} total={total} />
                <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-xs">
                  {stats.distribution.map((s) => (
                    <span key={s.category} className="flex items-center gap-1.5 text-slate-600">
                      <span className={`h-2.5 w-2.5 rounded-sm ${DIST_COLOR[s.category] ?? 'bg-slate-300'}`} />
                      {label(s.category)}
                      <span className="font-semibold text-slate-800">{s.count}</span>
                      <span className="text-slate-400">{Math.round((s.count / total) * 100)}%</span>
                    </span>
                  ))}
                </div>
              </Card>
            );
          })()}

          {/* Employee of the month */}
          {stats.employeeOfMonth && (
            <Card className="flex items-center gap-4 border-brand-200 bg-gradient-to-r from-brand-50 to-white p-5">
              <span className="text-3xl">⭐</span>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-brand-600">{t('stats.employeeOfMonth')}</p>
                <p className="text-xl font-bold text-slate-900">{stats.employeeOfMonth}</p>
              </div>
            </Card>
          )}

          {/* Monthly timeline (Gantt) */}
          <MonthlyTimeline tasks={timeline} year={year} month={month} />

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* Currently working on */}
            <div>
              <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{t('stats.workingNow')}</h3>
              <div className="space-y-3">
                {activity.length === 0 ? (
                  <EmptyState title={t('stats.noActive')} />
                ) : (
                  activity.map((a) => (
                    <Card key={a.employeeId} className="p-4">
                      <div className="mb-2 flex items-center gap-2.5">
                        <Avatar name={a.fullName} size={8} />
                        <div className="min-w-0 flex-1">
                          <p className="font-medium text-slate-900">{a.fullName}</p>
                          <p className="text-xs text-slate-400">{a.position || t('common.none')}</p>
                        </div>
                        <Badge color={a.activeTasks.length > 0 ? 'amber' : 'slate'}>{a.activeTasks.length} {t('stats.active')}</Badge>
                      </div>
                      {a.labels.length > 0 && (
                        <div className="mb-2 flex flex-wrap gap-1">
                          {a.labels.map((l) => <span key={l.id} className="rounded-full px-2 py-0.5 text-[11px] font-medium text-white" style={{ background: l.color || '#64748b' }}>{l.name}</span>)}
                        </div>
                      )}
                      {a.activeTasks.length === 0 ? (
                        <p className="text-xs text-slate-400">{t('stats.idle')}</p>
                      ) : (
                        <ul className="space-y-1.5">
                          {a.activeTasks.map((tk) => (
                            <li key={tk.id} className="flex items-center gap-2 text-sm">
                              <Badge color={STATUS_COLOR[tk.status]}>{t(STATUS_KEY[tk.status])}</Badge>
                              <span className="min-w-0 flex-1 truncate text-slate-700">{tk.title}</span>
                              <Badge color={PRIO_COLOR[tk.priority]}>{t(`tasks.prio.${tk.priority}`)}</Badge>
                            </li>
                          ))}
                        </ul>
                      )}
                    </Card>
                  ))
                )}
              </div>
            </div>

            {/* Performance */}
            <div>
              <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{t('stats.performance')}</h3>
              {stats.perEmployee.length === 0 ? (
                <EmptyState title={t('stats.empty')} />
              ) : (
                <div className="space-y-2.5">
                  <div className="flex flex-wrap gap-3 text-[11px] text-slate-500">
                    {[
                      { key: 'DONE', color: BAR_COLOR.DONE, label: t(STATUS_KEY.DONE) },
                      { key: 'OVERDUE', color: 'bg-red-500', label: t('stats.overdue') },
                      { key: 'TESTING', color: BAR_COLOR.TESTING, label: t(STATUS_KEY.TESTING) },
                      { key: 'IN_PROGRESS', color: BAR_COLOR.IN_PROGRESS, label: t(STATUS_KEY.IN_PROGRESS) },
                      { key: 'CANCELLED', color: BAR_COLOR.CANCELLED, label: t(STATUS_KEY.CANCELLED) },
                    ].map((it) => (
                      <span key={it.key} className="flex items-center gap-1.5">
                        <span className={`h-2.5 w-2.5 rounded-sm ${it.color}`} />
                        {it.label}
                      </span>
                    ))}
                  </div>
                  {stats.perEmployee.map((e) => {
                    const onTimeRate = e.completed > 0 ? Math.round((e.onTime / e.completed) * 100) : 0;
                    // This member's own proportional distribution bar (same phone-storage style as the
                    // overall one): group their tasks by category, with overdue carved out of its status.
                    const catOf = (tk: TaskBrief) => (tk.overdue ? 'OVERDUE' : tk.status);
                    const memberCount: Record<string, number> = {};
                    e.tasks.forEach((tk) => { const c = catOf(tk); memberCount[c] = (memberCount[c] ?? 0) + 1; });
                    const memberSlices = ['DONE', 'OVERDUE', 'TESTING', 'IN_PROGRESS', 'OPEN', 'CANCELLED']
                      .filter((c) => memberCount[c]).map((c) => ({ category: c, count: memberCount[c] }));
                    const memberTotal = e.taken || 1;
                    return (
                      <Card key={e.employeeId} className="p-4">
                        <div className="mb-2 flex items-center justify-between">
                          <div className="flex items-center gap-2.5">
                            <Avatar name={e.fullName} size={8} />
                            <p className="font-medium text-slate-900">{e.fullName}</p>
                          </div>
                          <span className="text-sm font-semibold text-emerald-600">{e.completed} {t('stats.done')}</span>
                        </div>
                        {/* This member's proportional status-distribution bar (overdue → red). */}
                        <div className="mb-2">
                          <DistBar slices={memberSlices} total={memberTotal} />
                        </div>
                        <div className="grid grid-cols-4 gap-2 text-center text-xs">
                          <div><p className="font-semibold text-slate-700">{e.taken}</p><p className="text-slate-400">{t('stats.taken')}</p></div>
                          <div><p className="font-semibold text-blue-600">{e.onTime}</p><p className="text-slate-400">{t('stats.onTime')}</p></div>
                          <div><p className="font-semibold text-red-600">{e.overdue}</p><p className="text-slate-400">{t('stats.overdue')}</p></div>
                          <div><p className="font-semibold text-slate-700">{e.avgCompletionHours != null ? e.avgCompletionHours + 'h' : '—'}</p><p className="text-slate-400">{t('stats.avgHours')}</p></div>
                        </div>
                        {e.completed > 0 && (
                          <p className="mt-2 text-center text-xs text-slate-400">{onTimeRate}% {t('stats.onTimeRate')}</p>
                        )}
                      </Card>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </DashboardLayout>
  );
}

const BAR_COLOR: Record<TaskStatus, string> = {
  OPEN: 'bg-blue-400', IN_PROGRESS: 'bg-amber-400', TESTING: 'bg-violet-400', DONE: 'bg-emerald-500', CANCELLED: 'bg-slate-300',
};

// Colors for the overall task-distribution bar (overdue carved out of its status).
const DIST_COLOR: Record<string, string> = {
  DONE: 'bg-emerald-500', OVERDUE: 'bg-red-500', TESTING: 'bg-violet-400',
  IN_PROGRESS: 'bg-amber-400', OPEN: 'bg-blue-400', CANCELLED: 'bg-slate-300',
};

/**
 * Proportional status-distribution bar (phone-storage style). Hovering a segment pops an immediate
 * styled tooltip with the category, its count and share — used for the overall bar and each member's.
 */
function DistBar({ slices, total }: { slices: { category: string; count: number }[]; total: number }) {
  const { t } = useTranslation();
  const [hover, setHover] = useState<{ cat: string; count: number; pct: number; center: number } | null>(null);
  const label = (c: string) => (c === 'OVERDUE' ? t('stats.overdue') : t(STATUS_KEY[c as TaskStatus]));
  let acc = 0;
  const segs = slices.map((s) => {
    const w = total > 0 ? (s.count / total) * 100 : 0;
    const seg = { ...s, w, center: acc + w / 2 };
    acc += w;
    return seg;
  });
  return (
    <div className="relative">
      <div className="flex h-2.5 w-full overflow-hidden rounded-full bg-slate-100">
        {segs.map((s) => (
          <div key={s.category}
            onMouseEnter={() => setHover({ cat: s.category, count: s.count, pct: s.w, center: s.center })}
            onMouseLeave={() => setHover(null)}
            className={`${DIST_COLOR[s.category] ?? 'bg-slate-300'} cursor-default transition-opacity hover:opacity-80`}
            style={{ width: `${s.w}%` }} />
        ))}
      </div>
      {hover && (
        <div
          className="pointer-events-none absolute bottom-full z-20 mb-1.5 -translate-x-1/2 whitespace-nowrap rounded-md bg-slate-900 px-2 py-1 text-[11px] font-medium text-white shadow-lg"
          style={{ left: `${Math.min(92, Math.max(8, hover.center))}%` }}
        >
          {label(hover.cat)}: <span className="font-bold">{hover.count}</span> · {Math.round(hover.pct)}%
        </div>
      )}
    </div>
  );
}

function MonthlyTimeline({ tasks, year, month }: { tasks: TimelineTask[]; year: number; month: number }) {
  const { t } = useTranslation();
  // Uzbekistan is UTC+5 with no DST. Anchor the month window and day labels to this fixed zone so the
  // chart agrees with the backend (which buckets the month in Asia/Tashkent) regardless of the viewer's
  // browser timezone — otherwise tasks near a month boundary clamp/label off-by-one.
  const TZ = 5 * 60 * 60 * 1000;
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const monthStart = Date.UTC(year, month - 1, 1) - TZ;
  const monthEndExcl = Date.UTC(year, month, 1) - TZ;
  const span = monthEndExcl - monthStart;
  const now = Date.now();

  const frac = (ms: number) => Math.min(1, Math.max(0, (ms - monthStart) / span));
  const dayOf = (iso: string | null) => (iso ? new Date(new Date(iso).getTime() + TZ).getUTCDate() : '—');

  // Per-task geometry, filtered to spans that actually fall inside the elapsed part of this month.
  const bars = tasks
    .map((tk) => {
      const taken = !!tk.takenAt;
      const startMs = new Date(tk.takenAt ?? tk.createdAt).getTime();
      let endMs: number;
      if (tk.completedAt) endMs = new Date(tk.completedAt).getTime();
      else if (tk.status === 'CANCELLED') endMs = tk.submittedAt ? new Date(tk.submittedAt).getTime() : startMs;
      else endMs = now; // ongoing → only up to the current moment
      const visStart = Math.max(startMs, monthStart);
      const visEnd = Math.min(Math.max(endMs, startMs), monthEndExcl);
      return { tk, taken, startMs, visStart, visEnd };
    })
    .filter((r) => (r.taken ? r.visEnd > r.visStart : r.startMs >= monthStart && r.startMs < monthEndExcl));

  // Group by assignee — one row per worker.
  const groupMap = new Map<string, typeof bars>();
  bars.forEach((b) => {
    const key = b.tk.assigneeName || t('common.none');
    if (!groupMap.has(key)) groupMap.set(key, []);
    groupMap.get(key)!.push(b);
  });

  // Lane-stack each worker's bars so overlapping tasks stack instead of colliding (usually 1 lane).
  const start = (b: (typeof bars)[number]) => (b.taken ? b.visStart : b.startMs);
  const end = (b: (typeof bars)[number]) => (b.taken ? b.visEnd : b.startMs);
  const workers = [...groupMap.entries()]
    .sort((a, b) => b[1].length - a[1].length)
    .map(([name, items]) => {
      const sorted = [...items].sort((a, b) => start(a) - start(b));
      const laneEnds: number[] = [];
      const placed = sorted.map((b) => {
        let lane = laneEnds.findIndex((laneEnd) => laneEnd <= start(b));
        if (lane === -1) { lane = laneEnds.length; laneEnds.push(end(b)); } else { laneEnds[lane] = end(b); }
        return { b, lane };
      });
      return { name, placed, laneCount: Math.max(1, laneEnds.length) };
    });

  const step = Math.max(1, Math.ceil(daysInMonth / 6));
  const ticks: number[] = [];
  for (let d = 1; d <= daysInMonth; d += step) ticks.push(d);
  if (ticks[ticks.length - 1] !== daysInMonth) ticks.push(daysInMonth);

  const nowUz = new Date(now + TZ);
  const isThisMonth = year === nowUz.getUTCFullYear() && month === nowUz.getUTCMonth() + 1;
  const todayLeft = isThisMonth ? frac(now) * 100 : null;

  const LANE_H = 24;

  return (
    <div>
      <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{t('stats.timeline')}</h3>
      <Card className="p-4">
        {workers.length === 0 ? (
          <EmptyState title={t('stats.noTimeline')} />
        ) : (
          <div className="overflow-x-auto">
            <div className="min-w-[640px]">
              {/* Day axis */}
              <div className="mb-1 flex">
                <div className="w-40 shrink-0" />
                <div className="relative h-5 flex-1">
                  {ticks.map((d) => (
                    <span key={d} className="absolute -translate-x-1/2 text-[11px] tabular-nums text-slate-400"
                      style={{ left: `${((d - 1) / daysInMonth) * 100}%` }}>{d}</span>
                  ))}
                </div>
              </div>
              {/* One row per worker; their tasks are bars on a shared track (lane-stacked). */}
              <div>
                {workers.map(({ name, placed, laneCount }) => (
                  <div key={name} className="flex border-t border-slate-100 py-1.5 first:border-t-0">
                    <div className="flex w-40 shrink-0 items-center pr-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-slate-800">{name}</p>
                        <p className="text-xs text-slate-400">{placed.length} {t('stats.tasksTotal')}</p>
                      </div>
                    </div>
                    <div className="relative flex-1" style={{ height: laneCount * LANE_H }}>
                      {ticks.map((d) => (
                        <span key={d} className="absolute top-0 h-full w-px bg-slate-100"
                          style={{ left: `${((d - 1) / daysInMonth) * 100}%` }} />
                      ))}
                      {todayLeft != null && (
                        <span className="absolute top-0 z-10 h-full w-0.5 bg-brand-400/70" style={{ left: `${todayLeft}%` }} />
                      )}
                      {placed.map(({ b, lane }) => {
                        const left = frac(start(b)) * 100;
                        const width = Math.max(1.5, (frac(end(b)) - frac(start(b))) * 100);
                        return (
                          <div key={b.tk.id}
                            className={`absolute flex items-center overflow-hidden rounded px-1 ${BAR_COLOR[b.tk.status]}`}
                            style={{ left: `${left}%`, width: `${width}%`, minWidth: 10, top: lane * LANE_H + 2, height: LANE_H - 6 }}
                            title={`${b.tk.title}\n${t(STATUS_KEY[b.tk.status])} · ${dayOf(b.tk.takenAt)} → ${dayOf(b.tk.completedAt)}`}>
                            <span className="truncate text-[10px] leading-none text-white">{b.tk.title}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
              {/* Legend */}
              <div className="mt-3 flex flex-wrap gap-3 text-xs text-slate-500">
                {(['IN_PROGRESS', 'TESTING', 'DONE'] as TaskStatus[]).map((s) => (
                  <span key={s} className="flex items-center gap-1.5">
                    <span className={`h-2.5 w-2.5 rounded-sm ${BAR_COLOR[s]}`} />
                    {t(STATUS_KEY[s])}
                  </span>
                ))}
              </div>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
