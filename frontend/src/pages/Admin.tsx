import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, Employee, TeamAdmin } from '../api';

export default function Admin() {
  const { t } = useTranslation();
  const [teams, setTeams] = useState<TeamAdmin[]>([]);
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.adminTeams(), api.adminEmployees()])
      .then(([teamList, employeeList]) => {
        setTeams(teamList);
        setEmployees(employeeList);
      })
      .catch((e) => setError((e as Error).message));
  }, []);

  return (
    <div>
      <div className="page-header">
        <h1>{t('admin.title')}</h1>
      </div>
      {error && <div className="error">{error}</div>}

      <h2 className="section-title">
        {t('admin.teams')} ({teams.length})
      </h2>
      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>{t('admin.teamName')}</th>
            <th>{t('admin.memberCount')}</th>
            <th>{t('admin.createdAt')}</th>
          </tr>
        </thead>
        <tbody>
          {teams.map((team) => (
            <tr key={team.id}>
              <td>{team.id}</td>
              <td>{team.name}</td>
              <td>{team.memberCount}</td>
              <td>{new Date(team.createdAt).toLocaleDateString()}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2 className="section-title">
        {t('admin.users')} ({employees.length})
      </h2>
      <table className="table">
        <thead>
          <tr>
            <th>{t('employees.fullName')}</th>
            <th>{t('employees.username')}</th>
            <th>{t('employees.role')}</th>
            <th>{t('admin.team')}</th>
            <th>{t('employees.telegram')}</th>
            <th>{t('employees.active')}</th>
          </tr>
        </thead>
        <tbody>
          {employees.map((employee) => (
            <tr key={employee.id} className={employee.active ? '' : 'row-inactive'}>
              <td>{employee.fullName}</td>
              <td>{employee.username}</td>
              <td>{employee.admin ? 'Admin' : '—'}</td>
              <td>
                {employee.memberships.length === 0
                  ? '—'
                  : employee.memberships
                      .map((m) => `${m.teamName} (${t(`employees.roles.${m.role}`)})`)
                      .join(', ')}
              </td>
              <td>{employee.telegramLinked ? '✅' : '—'}</td>
              <td>{employee.active ? t('employees.active') : t('employees.inactive')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
