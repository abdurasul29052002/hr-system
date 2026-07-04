import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, Tag } from '../api';

const PALETTE = ['#2563eb', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#64748b'];

export default function Tags() {
  const { t } = useTranslation();
  const [tags, setTags] = useState<Tag[]>([]);
  const [editing, setEditing] = useState<Tag | 'new' | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      setTags(await api.tags());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (id: number) => {
    setError('');
    try {
      await api.deleteTag(id);
      await load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>{t('tags.title')}</h1>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + {t('tags.add')}
        </button>
      </div>
      {error && <div className="error">{error}</div>}
      {tags.length === 0 ? (
        <p className="hint">{t('tags.empty')}</p>
      ) : (
        <div className="tag-grid">
          {tags.map((tag) => (
            <div key={tag.id} className="tag-row">
              <span className="tag-chip" style={{ background: tag.color ?? '#64748b' }}>
                {tag.name}
              </span>
              <span className="row-actions">
                <button className="btn btn-small btn-ghost" onClick={() => setEditing(tag)}>
                  {t('tags.edit')}
                </button>
                <button className="btn btn-small btn-danger" onClick={() => remove(tag.id)}>
                  {t('tags.delete')}
                </button>
              </span>
            </div>
          ))}
        </div>
      )}
      {editing && (
        <TagModal
          tag={editing === 'new' ? null : editing}
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

function TagModal({ tag, onClose, onSaved }: { tag: Tag | null; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const [name, setName] = useState(tag?.name ?? '');
  const [color, setColor] = useState(tag?.color ?? PALETTE[0]);
  const [error, setError] = useState('');

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      if (tag) {
        await api.updateTag(tag.id, { name, color });
      } else {
        await api.createTag({ name, color });
      }
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>{tag ? t('tags.edit') : t('tags.add')}</h2>
        <label>
          {t('tags.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </label>
        <label>
          {t('tags.color')}
          <div className="palette">
            {PALETTE.map((c) => (
              <button
                key={c}
                type="button"
                className={`swatch ${color === c ? 'selected' : ''}`}
                style={{ background: c }}
                onClick={() => setColor(c)}
                aria-label={c}
              />
            ))}
          </div>
        </label>
        {error && <div className="error">{error}</div>}
        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>
            {t('tasks.close')}
          </button>
          <button type="submit" className="btn btn-primary">
            {t('tasks.save')}
          </button>
        </div>
      </form>
    </div>
  );
}
