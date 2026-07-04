import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, getToken, setCurrentTeamId, storeEmployee, InviteInfo } from '../api';

export default function Join() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<InviteInfo | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const authed = !!getToken();

  useEffect(() => {
    if (!token) return;
    if (!authed) {
      // remember the invite; Login/Register will come back here
      localStorage.setItem('pendingInvite', token);
      return;
    }
    localStorage.removeItem('pendingInvite');
    api
      .inviteInfo(token)
      .then(setInfo)
      .catch((e) => setError((e as Error).message));
  }, [token, authed]);

  const accept = async () => {
    if (!token) return;
    setError('');
    setLoading(true);
    try {
      const joined = await api.acceptInvite(token);
      const me = await api.me();
      storeEmployee(me);
      setCurrentTeamId(joined.teamId);
      window.location.href = '/';
    } catch (e) {
      setError((e as Error).message);
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <h1>{t('appName')}</h1>
        <h2>{t('join.title')}</h2>
        {!authed ? (
          <>
            <p className="hint">{t('join.authHint')}</p>
            <Link className="btn btn-primary" to="/login">
              {t('login.submit')}
            </Link>
            <Link className="btn btn-ghost" to="/register">
              {t('register.submit')}
            </Link>
          </>
        ) : error ? (
          <div className="error">{error}</div>
        ) : !info ? (
          <p className="hint">…</p>
        ) : info.alreadyMember ? (
          <>
            <p className="hint">{t('join.alreadyMember', { team: info.teamName })}</p>
            <button className="btn btn-primary" onClick={() => navigate('/')}>
              {t('tasks.close')}
            </button>
          </>
        ) : (
          <>
            <p className="hint">
              {t('join.hint', { team: info.teamName, role: t(`employees.roles.${info.role}`) })}
            </p>
            <button className="btn btn-primary" onClick={accept} disabled={loading}>
              {t('join.submit')}
            </button>
            <button className="btn btn-ghost" onClick={() => navigate('/')}>
              {t('tasks.close')}
            </button>
          </>
        )}
      </div>
    </div>
  );
}
