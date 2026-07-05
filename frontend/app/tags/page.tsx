'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import DashboardLayout from '@/components/DashboardLayout';
import { api } from '@/lib/api';
import { getStoredEmployee, getCurrentMembership } from '@/lib/auth-client';
import { isManagerRole } from '@/lib/types';
import type { Tag } from '@/lib/types';
import { Button, Card, EmptyState, Field, Input, Modal, PageHeader, PageLoader } from '@/components/ui';
import '@/lib/i18n';

const PALETTE = ['#4f46e5', '#0891b2', '#059669', '#d97706', '#dc2626', '#7c3aed', '#db2777', '#475569'];

export default function TagsPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [tags, setTags] = useState<Tag[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<Tag | 'new' | null>(null);

  const me = getStoredEmployee();
  const isManager = isManagerRole(getCurrentMembership(me)?.role);

  const load = useCallback(async () => {
    try { setTags(await api.tags()); } catch (e) { console.error(e); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    if (!isManager) { router.push('/'); return; }
    load();
  }, [isManager, router, load]);

  const remove = async (id: number) => {
    if (!confirm(t('common.confirmDelete'))) return;
    try { await api.deleteTag(id); await load(); } catch (e) { alert(e instanceof Error ? e.message : 'Error'); }
  };

  if (loading) return <DashboardLayout><PageLoader /></DashboardLayout>;

  return (
    <DashboardLayout>
      <PageHeader title={t('tags.title')} actions={<Button onClick={() => setEditing('new')}>+ {t('tags.add')}</Button>} />
      {tags.length === 0 ? (
        <EmptyState title={t('tags.empty')} />
      ) : (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {tags.map((tag) => (
            <Card key={tag.id} className="flex items-center justify-between p-3">
              <span className="rounded-full px-3 py-1 text-sm font-medium text-white" style={{ background: tag.color || '#64748b' }}>{tag.name}</span>
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" onClick={() => setEditing(tag)}>{t('common.edit')}</Button>
                <Button size="sm" variant="danger" onClick={() => remove(tag.id)}>{t('common.delete')}</Button>
              </div>
            </Card>
          ))}
        </div>
      )}
      {editing && <TagModal tag={editing === 'new' ? null : editing} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); load(); }} />}
    </DashboardLayout>
  );
}

function TagModal({ tag, onClose, onSaved }: { tag: Tag | null; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const [name, setName] = useState(tag?.name ?? '');
  const [color, setColor] = useState(tag?.color ?? PALETTE[0]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      if (tag) await api.updateTag(tag.id, { name, color });
      else await api.createTag({ name, color });
      onSaved();
    } catch (err) { setError(err instanceof Error ? err.message : 'Error'); setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={tag ? t('tags.edit') : t('tags.add')} size="sm">
      <form onSubmit={submit} className="space-y-3">
        <Field label={t('tags.name')}><Input value={name} onChange={(e) => setName(e.target.value)} required autoFocus /></Field>
        <Field label={t('tags.color')}>
          <div className="flex flex-wrap gap-2">
            {PALETTE.map((c) => (
              <button key={c} type="button" onClick={() => setColor(c)}
                className={`h-8 w-8 rounded-full transition-transform ${color === c ? 'scale-110 ring-2 ring-slate-800 ring-offset-2' : ''}`}
                style={{ background: c }} aria-label={c} />
            ))}
          </div>
        </Field>
        <div className="rounded-lg bg-slate-50 p-3 text-center">
          <span className="rounded-full px-3 py-1 text-sm font-medium text-white" style={{ background: color }}>{name || 'Tag'}</span>
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <Button type="button" variant="secondary" onClick={onClose}>{t('common.cancel')}</Button>
          <Button type="submit" disabled={busy}>{t('common.save')}</Button>
        </div>
      </form>
    </Modal>
  );
}
