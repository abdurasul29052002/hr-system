import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, storeAuth } from '../api';

export default function Login() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(false);
    setLoading(true);
    try {
      const result = await api.login(username, password);
      storeAuth(result.token, result.employee);
      const pendingInvite = localStorage.getItem('pendingInvite');
      if (pendingInvite && !result.employee.admin) {
        navigate(`/join/${pendingInvite}`);
      } else if (result.employee.admin) {
        navigate('/admin');
      } else {
        navigate(result.employee.memberships.length > 0 ? '/' : '/create-team');
      }
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>{t('appName')}</h1>
        <h2>{t('login.title')}</h2>
        <label>
          {t('login.username')}
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
        </label>
        <label>
          {t('login.password')}
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <div className="error">{t('login.error')}</div>}
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {t('login.submit')}
        </button>
        <div className="auth-switch">
          {t('login.noAccount')} <Link to="/register">{t('login.registerLink')}</Link>
        </div>
      </form>
    </div>
  );
}
