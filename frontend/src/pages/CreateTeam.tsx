import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, clearAuth, getStoredEmployee, setCurrentTeamId, storeEmployee } from '../api';

export default function CreateTeam() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const employee = getStoredEmployee();
  const hasTeams = (employee?.memberships.length ?? 0) > 0;
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const created = await api.createTeam(name);
      const me = await api.me();
      storeEmployee(me);
      setCurrentTeamId(created.teamId);
      window.location.href = '/';
    } catch (err) {
      setError((err as Error).message);
      setLoading(false);
    }
  };

  const logout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>{t('appName')}</h1>
        <h2>{t('team.createTitle')}</h2>
        <p className="hint">{t('team.createHint', { name: employee?.fullName ?? '' })}</p>
        <label>
          {t('team.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} autoFocus required />
        </label>
        {error && <div className="error">{error}</div>}
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {t('team.createSubmit')}
        </button>
        {hasTeams ? (
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/')}>
            {t('tasks.close')}
          </button>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={logout}>
            {t('nav.logout')}
          </button>
        )}
      </form>
    </div>
  );
}
