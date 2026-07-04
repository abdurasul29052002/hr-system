import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, getCurrentMembership, getStoredEmployee, Invite, Language, Member, Role } from '../api';

export default function Employees() {
  const { t } = useTranslation();
  const me = getStoredEmployee();
  const myMembership = getCurrentMembership(me);
  const isLeader = myMembership?.role === 'LEADER';
  const [members, setMembers] = useState<Member[]>([]);
  const [editing, setEditing] = useState<Member | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [showInvites, setShowInvites] = useState(false);
  const [addUsername, setAddUsername] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setMembers(await api.members());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: () => Promise<unknown>) => {
    setError('');
    try {
      await action();
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const addExisting = (e: FormEvent) => {
    e.preventDefault();
    if (!addUsername.trim()) return;
    run(async () => {
      await api.addExistingMember({ username: addUsername.trim() });
      setAddUsername('');
    });
  };

  return (
    <div>
      <div className="page-header">
        <h1>{t('employees.title')}</h1>
        <div className="page-actions">
          <form className="add-existing" onSubmit={addExisting}>
            <input
              value={addUsername}
              onChange={(e) => setAddUsername(e.target.value)}
              placeholder={t('employees.addExistingPlaceholder')}
            />
            <button className="btn btn-ghost" type="submit">
              {t('employees.addExisting')}
            </button>
          </form>
          <button className="btn btn-ghost" onClick={() => setShowInvites(true)}>
            🔗 {t('invite.button')}
          </button>
          <button className="btn btn-primary" onClick={() => setShowNew(true)}>
            + {t('employees.add')}
          </button>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <table className="table">
        <thead>
          <tr>
            <th>{t('employees.fullName')}</th>
            <th>{t('employees.username')}</th>
            <th>{t('employees.position')}</th>
            <th>{t('employees.phone')}</th>
            <th>{t('employees.role')}</th>
            <th>{t('employees.telegram')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => {
            const isSelf = member.employeeId === me?.id;
            return (
              <tr key={member.employeeId}>
                <td>
                  {member.fullName}
                  {isSelf ? ' (you)' : ''}
                </td>
                <td>{member.username}</td>
                <td>{member.position}</td>
                <td>{member.phone}</td>
                <td>{t(`employees.roles.${member.role}`)}</td>
                <td>
                  {member.telegramLinked ? (
                    <span className="chip chip-success">✅ {t('employees.linked')}</span>
                  ) : (
                    <span className="chip">
                      {t('employees.code')}: <b>{member.telegramLinkCode}</b>
                    </span>
                  )}
                </td>
                <td className="row-actions">
                  <button className="btn btn-small btn-ghost" onClick={() => setEditing(member)}>
                    {t('employees.edit')}
                  </button>
                  {member.telegramLinked && (
                    <button
                      className="btn btn-small btn-ghost"
                      onClick={() => run(() => api.resetTelegram(member.employeeId))}
                    >
                      {t('employees.resetTelegram')}
                    </button>
                  )}
                  {!isSelf && (
                    <button
                      className="btn btn-small btn-danger"
                      onClick={() => run(() => api.removeMember(member.employeeId))}
                    >
                      {t('employees.remove')}
                    </button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {showInvites && <InvitesModal isLeader={isLeader} onClose={() => setShowInvites(false)} />}
      {showNew && (
        <NewMemberModal
          isLeader={isLeader}
          onClose={() => setShowNew(false)}
          onSaved={() => {
            setShowNew(false);
            load();
          }}
        />
      )}
      {editing && (
        <EditMemberModal
          member={editing}
          isLeader={isLeader}
          isSelf={editing.employeeId === me?.id}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}
    </div>
  );
}

function InvitesModal({ isLeader, onClose }: { isLeader: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [invites, setInvites] = useState<Invite[]>([]);
  const [role, setRole] = useState<Role>('MEMBER');
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setInvites(await api.invites());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const inviteUrl = (token: string) => `${window.location.origin}/join/${token}`;

  const create = async () => {
    setError('');
    try {
      await api.createInvite(role);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const copy = async (invite: Invite) => {
    try {
      await navigator.clipboard.writeText(inviteUrl(invite.token));
      setCopiedId(invite.id);
      setTimeout(() => setCopiedId(null), 1500);
    } catch {
      /* clipboard unavailable */
    }
  };

  const revoke = async (id: number) => {
    setError('');
    try {
      await api.revokeInvite(id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>{t('invite.title')}</h2>
        <p className="hint">{t('invite.hint')}</p>
        <div className="invite-create">
          <select value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={!isLeader}>
            <option value="MEMBER">{t('employees.roles.MEMBER')}</option>
            <option value="MANAGER">{t('employees.roles.MANAGER')}</option>
            <option value="LEADER">{t('employees.roles.LEADER')}</option>
          </select>
          <button className="btn btn-primary" onClick={create}>
            + {t('invite.create')}
          </button>
        </div>
        {error && <div className="error">{error}</div>}
        {invites.length === 0 ? (
          <p className="hint">{t('invite.empty')}</p>
        ) : (
          <div className="invite-list">
            {invites.map((invite) => (
              <div key={invite.id} className="invite-row">
                <div className="invite-info">
                  <code className="invite-url">{inviteUrl(invite.token)}</code>
                  <span className="invite-meta">
                    {t(`employees.roles.${invite.role}`)}
                    {invite.expiresAt && ` · ${t('invite.expires')} ${new Date(invite.expiresAt).toLocaleDateString()}`}
                  </span>
                </div>
                <span className="row-actions">
                  <button className="btn btn-small btn-ghost" onClick={() => copy(invite)}>
                    {copiedId === invite.id ? '✅' : t('invite.copy')}
                  </button>
                  <button className="btn btn-small btn-danger" onClick={() => revoke(invite.id)}>
                    {t('invite.revoke')}
                  </button>
                </span>
              </div>
            ))}
          </div>
        )}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {t('employees.close')}
          </button>
        </div>
      </div>
    </div>
  );
}

function NewMemberModal({
  isLeader,
  onClose,
  onSaved,
}: {
  isLeader: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [position, setPosition] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [language, setLanguage] = useState<Language>('EN');
  const [error, setError] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await api.createMember({ fullName, username, password, phone, position, role, language });
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>{t('employees.add')}</h2>
        <label>
          {t('employees.fullName')}
          <input value={fullName} onChange={(e) => setFullName(e.target.value)} required autoFocus />
        </label>
        <div className="form-row">
          <label>
            {t('employees.username')}
            <input value={username} onChange={(e) => setUsername(e.target.value)} required />
          </label>
          <label>
            {t('employees.password')}
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
        </div>
        <div className="form-row">
          <label>
            {t('employees.phone')}
            <input value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
          <label>
            {t('employees.position')}
            <input value={position} onChange={(e) => setPosition(e.target.value)} />
          </label>
        </div>
        <div className="form-row">
          <label>
            {t('employees.role')}
            <select value={role} onChange={(e) => setRole(e.target.value as Role)} disabled={!isLeader}>
              <option value="MEMBER">{t('employees.roles.MEMBER')}</option>
              <option value="MANAGER">{t('employees.roles.MANAGER')}</option>
              <option value="LEADER">{t('employees.roles.LEADER')}</option>
            </select>
          </label>
          <label>
            {t('employees.language')}
            <select value={language} onChange={(e) => setLanguage(e.target.value as Language)}>
              <option value="EN">English</option>
              <option value="RU">Русский</option>
              <option value="UZ">O'zbekcha</option>
            </select>
          </label>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {t('employees.close')}
          </button>
          <button type="submit" className="btn btn-primary">
            {t('employees.save')}
          </button>
        </div>
      </form>
    </div>
  );
}

function EditMemberModal({
  member,
  isLeader,
  isSelf,
  onClose,
  onSaved,
}: {
  member: Member;
  isLeader: boolean;
  isSelf: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [role, setRole] = useState<Role>(member.role);
  const [position, setPosition] = useState(member.position ?? '');
  const [error, setError] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      await api.updateMember(member.employeeId, { role, position });
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>
          {t('employees.edit')} — {member.fullName}
        </h2>
        <div className="form-row">
          <label>
            {t('employees.role')}
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as Role)}
              disabled={!isLeader || isSelf}
            >
              <option value="MEMBER">{t('employees.roles.MEMBER')}</option>
              <option value="MANAGER">{t('employees.roles.MANAGER')}</option>
              <option value="LEADER">{t('employees.roles.LEADER')}</option>
            </select>
          </label>
          <label>
            {t('employees.position')}
            <input value={position} onChange={(e) => setPosition(e.target.value)} />
          </label>
        </div>
        {error && <div className="error">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {t('employees.close')}
          </button>
          <button type="submit" className="btn btn-primary">
            {t('employees.save')}
          </button>
        </div>
      </form>
    </div>
  );
}
