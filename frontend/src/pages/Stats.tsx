import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, MonthlyStats } from '../api';

export default function Stats() {
  const { t } = useTranslation();
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [stats, setStats] = useState<MonthlyStats | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      setStats(await api.monthlyStats(year, month));
    } catch (e) {
      setError((e as Error).message);
    }
  }, [year, month]);

  useEffect(() => {
    load();
  }, [load]);

  const years = [];
  for (let y = now.getFullYear(); y >= now.getFullYear() - 4; y--) {
    years.push(y);
  }

  return (
    <div>
      <div className="page-header">
        <h1>{t('stats.title')}</h1>
        <div className="page-actions">
          <select value={year} onChange={(e) => setYear(Number(e.target.value))} aria-label={t('stats.year')}>
            {years.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
          <select value={month} onChange={(e) => setMonth(Number(e.target.value))} aria-label={t('stats.month')}>
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>
                {String(m).padStart(2, '0')}
              </option>
            ))}
          </select>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {stats && (
        <>
          <div className="stat-cards">
            <div className="stat-card">
              <div className="stat-value">{stats.totalCreated}</div>
              <div className="stat-label">{t('stats.created')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalCompleted}</div>
              <div className="stat-label">{t('stats.completed')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalInProgress}</div>
              <div className="stat-label">{t('stats.inProgress')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalTesting}</div>
              <div className="stat-label">{t('stats.testing')}</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.totalOpen}</div>
              <div className="stat-label">{t('stats.open')}</div>
            </div>
          </div>
          {stats.perEmployee.length === 0 ? (
            <p>{t('stats.empty')}</p>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>{t('stats.employee')}</th>
                  <th>{t('stats.taken')}</th>
                  <th>{t('stats.completed')}</th>
                  <th>{t('stats.inProgress')}</th>
                  <th>{t('stats.overdue')}</th>
                  <th>{t('stats.avgHours')}</th>
                </tr>
              </thead>
              <tbody>
                {stats.perEmployee.map((row) => (
                  <tr key={row.employeeId}>
                    <td>{row.fullName}</td>
                    <td>{row.taken}</td>
                    <td>{row.completed}</td>
                    <td>{row.inProgress}</td>
                    <td>{row.overdue}</td>
                    <td>{row.avgCompletionHours ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </div>
  );
}
