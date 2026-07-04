import { useEffect } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  api,
  clearAuth,
  getCurrentMembership,
  getStoredEmployee,
  getToken,
  isManagerRole,
  setCurrentTeamId,
  storeEmployee,
  Language,
} from '../api';

const LANGUAGES: { code: string; api: Language; label: string }[] = [
  { code: 'en', api: 'EN', label: 'EN' },
  { code: 'ru', api: 'RU', label: 'RU' },
  { code: 'uz', api: 'UZ', label: 'UZ' },
];

const NEW_TEAM = '__new__';

export default function Layout() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const employee = getStoredEmployee();
  const membership = getCurrentMembership(employee);
  const isManager = isManagerRole(membership?.role);
  const isAdmin = employee?.admin === true;

  useEffect(() => {
    // refresh cached profile (roles/teams may have changed elsewhere)
    api
      .me()
      .then((me) => {
        storeEmployee(me);
        if (!me.admin && me.memberships.length === 0) {
          navigate('/create-team');
        }
      })
      .catch(() => undefined);
  }, [navigate]);

  const changeLanguage = (code: string) => {
    i18n.changeLanguage(code);
    localStorage.setItem('lang', code);
    const entry = LANGUAGES.find((l) => l.code === code);
    if (entry && getToken()) {
      api.updateLanguage(entry.api).catch(() => undefined);
    }
  };

  const switchTeam = (value: string) => {
    if (value === NEW_TEAM) {
      navigate('/create-team');
      return;
    }
    setCurrentTeamId(Number(value));
    // full reload so every page refetches within the new team context
    window.location.href = '/';
  };

  const logout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <div className="layout">
      <header className="topbar">
        <div className="brand">{t('appName')}</div>
        {!isAdmin && employee && employee.memberships.length > 0 && (
          <select
            className="team-select"
            value={membership?.teamId ?? ''}
            onChange={(e) => switchTeam(e.target.value)}
            aria-label={t('team.switcher')}
          >
            {employee.memberships.map((m) => (
              <option key={m.teamId} value={m.teamId}>
                {m.teamName} ({t(`employees.roles.${m.role}`)})
              </option>
            ))}
            <option value={NEW_TEAM}>+ {t('team.new')}</option>
          </select>
        )}
        <nav>
          {isAdmin ? (
            <NavLink to="/admin">{t('nav.admin')}</NavLink>
          ) : (
            <>
              <NavLink to="/" end>
                {t('nav.tasks')}
              </NavLink>
              {isManager && <NavLink to="/employees">{t('nav.employees')}</NavLink>}
              {isManager && <NavLink to="/tags">{t('nav.tags')}</NavLink>}
              <NavLink to="/stats">{t('nav.stats')}</NavLink>
            </>
          )}
        </nav>
        <div className="topbar-right">
          <select
            className="lang-select"
            value={i18n.language}
            onChange={(e) => changeLanguage(e.target.value)}
            aria-label={t('settings.language')}
          >
            {LANGUAGES.map((l) => (
              <option key={l.code} value={l.code}>
                {l.label}
              </option>
            ))}
          </select>
          <span className="user-name">{employee?.fullName}</span>
          <button className="btn btn-ghost" onClick={logout}>
            {t('nav.logout')}
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
