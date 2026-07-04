import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, storeAuth, Language } from '../api';

const LANG_BY_CODE: Record<string, Language> = { en: 'EN', ru: 'RU', uz: 'UZ' };

export default function Register() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const result = await api.register({
        fullName,
        username,
        password,
        phone: phone || undefined,
        language: LANG_BY_CODE[i18n.language] ?? 'EN',
      });
      storeAuth(result.token, result.employee);
      const pendingInvite = localStorage.getItem('pendingInvite');
      navigate(pendingInvite ? `/join/${pendingInvite}` : '/create-team');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>{t('appName')}</h1>
        <h2>{t('register.title')}</h2>
        <label>
          {t('register.fullName')}
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} autoFocus required />
        </label>
        <label>
          {t('login.username')}
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          {t('login.password')}
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
        </label>
        <label>
          {t('register.phone')}
          <input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {t('register.submit')}
        </button>
        <div className="auth-switch">
          {t('register.haveAccount')} <Link to="/login">{t('register.loginLink')}</Link>
        </div>
      </form>
    </div>
  );
}
