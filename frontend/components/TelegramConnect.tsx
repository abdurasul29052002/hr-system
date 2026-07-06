'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api } from '@/lib/api';
import { storeEmployee } from '@/lib/auth-client';
import type { Employee } from '@/lib/types';
import { Button, Modal } from './ui';

type BotInfo = { enabled: boolean; username: string | null };

const DISMISS_KEY = 'hr_tg_reminder_dismissed';

/** Paper-plane glyph so the feature reads as "Telegram" without shipping an external asset. */
function TelegramGlyph({ className = 'h-5 w-5' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="currentColor" aria-hidden>
      <path d="M21.94 4.36 18.9 19.1c-.23 1.02-.84 1.27-1.7.79l-4.7-3.47-2.27 2.18c-.25.25-.46.46-.94.46l.33-4.78L18.6 5.9c.38-.34-.08-.53-.6-.19L6.55 12.9l-4.66-1.46c-1.01-.32-1.03-1.01.21-1.5l18.22-7.02c.84-.31 1.58.2 1.32 1.44Z" />
    </svg>
  );
}

/**
 * Surfaces the Telegram bot to logged-in members: a dismissible reminder banner
 * while their account isn't linked, plus a modal (also reachable from the user
 * menu) with a one-tap deep link and a fallback code. Renders nothing for admins
 * or when the server reports the bot is not configured.
 */
export default function TelegramConnect({
  employee,
  open,
  setOpen,
  onEmployeeUpdate,
}: {
  employee: Employee;
  open: boolean;
  setOpen: (open: boolean) => void;
  onEmployeeUpdate: (employee: Employee) => void;
}) {
  const { t } = useTranslation();
  const [bot, setBot] = useState<BotInfo | null>(null);
  const [dismissed, setDismissed] = useState(true); // hidden until we read localStorage
  const [copied, setCopied] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkedMiss, setCheckedMiss] = useState(false);

  useEffect(() => {
    setDismissed(localStorage.getItem(DISMISS_KEY) === String(employee.id));
    api.botInfo().then(setBot).catch(() => setBot(null));
  }, [employee.id]);

  const linked = employee.telegramLinked;
  const code = employee.telegramLinkCode;
  const botDisabled = bot?.enabled === false;
  const showBanner = !linked && !dismissed && !botDisabled;
  const deepLink = bot?.username && code ? `https://t.me/${bot.username}?start=${code}` : null;

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, String(employee.id));
    setDismissed(true);
  };

  const copyCode = async () => {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked — the code is visible for manual copy */
    }
  };

  const recheck = async () => {
    setChecking(true);
    setCheckedMiss(false);
    try {
      const me = await api.me();
      storeEmployee(me);
      onEmployeeUpdate(me);
      if (me.telegramLinked) {
        setOpen(false);
      } else {
        setCheckedMiss(true);
      }
    } catch {
      /* ignore — user can retry */
    } finally {
      setChecking(false);
    }
  };

  return (
    <>
      {showBanner && (
        <div className="border-b border-sky-100 bg-sky-50">
          <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-2.5">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-sky-500 text-white">
              <TelegramGlyph />
            </span>
            <p className="min-w-0 flex-1 text-sm text-sky-900">
              <span className="font-semibold">{t('telegram.reminderTitle')}</span>{' '}
              <span className="hidden text-sky-700 sm:inline">{t('telegram.reminderText')}</span>
            </p>
            <Button size="sm" onClick={() => setOpen(true)} className="shrink-0 bg-sky-500 hover:bg-sky-600">
              {t('telegram.connect')}
            </Button>
            <button
              onClick={dismiss}
              aria-label={t('telegram.later')}
              className="shrink-0 rounded-md p-1 text-sky-400 hover:bg-sky-100 hover:text-sky-700"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {open && (
        <Modal open onClose={() => setOpen(false)} title={t('telegram.title')} size="sm">
          {linked ? (
            <div className="py-4 text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
                <svg className="h-7 w-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
              </div>
              <p className="mt-3 text-base font-semibold text-slate-900">{t('telegram.connected')}</p>
              <p className="mt-1 text-sm text-slate-500">{t('telegram.connectedHint')}</p>
              <div className="mt-5 flex justify-center">
                <Button variant="secondary" onClick={() => setOpen(false)}>
                  {t('tasks.close')}
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex items-start gap-3 rounded-xl bg-sky-50 p-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-sky-500 text-white">
                  <TelegramGlyph />
                </span>
                <p className="text-sm text-sky-900">{t('telegram.intro')}</p>
              </div>

              {deepLink && (
                <a
                  href={deepLink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-sky-500 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-sky-600"
                >
                  <TelegramGlyph className="h-4 w-4" />
                  {t('telegram.openBot')}
                </a>
              )}

              <ol className="space-y-2 text-sm text-slate-600">
                <li className="flex gap-2">
                  <Step n={1} />
                  <span>{deepLink ? t('telegram.step1DeepLink') : t('telegram.step1Manual')}</span>
                </li>
                <li className="flex gap-2">
                  <Step n={2} />
                  <span>{deepLink ? t('telegram.step2DeepLink') : t('telegram.step2Manual')}</span>
                </li>
              </ol>

              {code && (
                <div>
                  <p className="mb-1 text-xs font-medium text-slate-500">
                    {deepLink ? t('telegram.orUseCode') : t('telegram.yourCode')}
                  </p>
                  <div className="flex items-stretch gap-2">
                    <code className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-slate-300 bg-slate-50 py-2 text-lg font-bold tracking-[0.3em] text-slate-800">
                      {code}
                    </code>
                    <Button variant="secondary" onClick={copyCode} className="shrink-0">
                      {copied ? t('telegram.copied') : t('telegram.copy')}
                    </Button>
                  </div>
                </div>
              )}

              <div className="flex items-center justify-between border-t border-slate-100 pt-3">
                {checkedMiss ? (
                  <span className="text-xs text-amber-600">{t('telegram.stillNotLinked')}</span>
                ) : (
                  <span />
                )}
                <Button variant="success" onClick={recheck} disabled={checking}>
                  {checking ? t('telegram.checking') : t('telegram.iveConnected')}
                </Button>
              </div>
            </div>
          )}
        </Modal>
      )}
    </>
  );
}

function Step({ n }: { n: number }) {
  return (
    <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-sky-100 text-[11px] font-bold text-sky-700">
      {n}
    </span>
  );
}
