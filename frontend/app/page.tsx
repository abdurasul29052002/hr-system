'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useTranslation } from 'react-i18next';
import { getToken, getStoredEmployee } from '@/lib/auth-client';
import { setLanguage } from '@/lib/i18n';
import type { Language } from '@/lib/types';
import '@/lib/i18n';

const LANGS: Language[] = ['EN', 'RU', 'UZ'];

/* Inline stroke icons (Heroicons-style) so the page has no external asset dependencies. */
const ICON: Record<string, React.ReactNode> = {
  board: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 5h4v14H4zM10 5h4v9h-4zM16 5h4v6h-4z" />,
  teams: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M17 20h5v-1a4 4 0 00-4-4h-1m-3 5H2v-1a4 4 0 014-4h6a4 4 0 014 4v1zm-4-11a3 3 0 11-6 0 3 3 0 016 0zm7 0a3 3 0 11-4 0" />,
  stats: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M4 19V9m5 10V5m5 14v-7m5 7V11" />,
  bot: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 10h.01M15 10h.01M12 3v3m-5 0h10a2 2 0 012 2v7a2 2 0 01-2 2h-4l-3 3v-3H7a2 2 0 01-2-2V8a2 2 0 012-2z" />,
  comments: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M8 10h8M8 14h5m-9 6l3-3h9a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v13z" />,
  reports: <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.8} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h6l6 6v9a2 2 0 01-2 2zM13 3v6h6" />,
};

export default function LandingPage() {
  const { t, i18n } = useTranslation();
  const [mounted, setMounted] = useState(false);
  const [loggedIn, setLoggedIn] = useState(false);

  useEffect(() => {
    setMounted(true);
    const emp = getStoredEmployee();
    setLoggedIn(!!getToken());
    if (emp?.language) setLanguage(emp.language);
  }, []);

  const features = ['board', 'teams', 'stats', 'bot', 'comments', 'reports'] as const;
  const steps = ['one', 'two', 'three'] as const;

  return (
    <div className="min-h-screen bg-white text-slate-900">
      {/* ---------------------------------------------------------------- Nav */}
      <header className="sticky top-0 z-40 border-b border-slate-200/70 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3 sm:px-6">
          <Link href="/" className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white shadow-sm shadow-brand-600/30">HR</span>
            <span className="text-base font-bold tracking-tight">{t('appName')}</span>
          </Link>

          <div className="flex-1" />

          {/* Language switcher */}
          <div className="flex items-center gap-0.5 rounded-lg bg-slate-100 p-0.5">
            {LANGS.map((l) => (
              <button
                key={l}
                onClick={() => setLanguage(l)}
                className={`rounded-md px-2 py-1 text-xs font-semibold transition-colors ${
                  mounted && i18n.language === l ? 'bg-white text-brand-600 shadow-sm' : 'text-slate-500 hover:text-slate-700'
                }`}
              >
                {l}
              </button>
            ))}
          </div>

          {loggedIn ? (
            <Link href="/tasks" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-brand-700">
              {t('landing.nav.dashboard')}
            </Link>
          ) : (
            <>
              <Link href="/login" className="hidden rounded-lg px-3 py-2 text-sm font-medium text-slate-600 transition-colors hover:text-slate-900 sm:block">
                {t('landing.nav.login')}
              </Link>
              <Link href="/register" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition-colors hover:bg-brand-700">
                {t('landing.nav.getStarted')}
              </Link>
            </>
          )}
        </div>
      </header>

      {/* ---------------------------------------------------------------- Hero */}
      <section className="relative overflow-hidden">
        {/* decorative blurred blobs */}
        <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
          <div className="absolute -top-24 -left-24 h-96 w-96 rounded-full bg-brand-300/40 blur-3xl" />
          <div className="absolute -top-10 right-0 h-80 w-80 rounded-full bg-violet-300/30 blur-3xl" />
          <div className="absolute inset-0 bg-gradient-to-b from-brand-50/60 to-white" />
        </div>

        <div className="mx-auto grid max-w-6xl items-center gap-12 px-4 py-16 sm:px-6 lg:grid-cols-2 lg:py-24">
          <div className="animate-fade">
            <span className="inline-flex items-center gap-2 rounded-full border border-brand-200 bg-white/70 px-3 py-1 text-xs font-semibold text-brand-700 shadow-sm">
              <span className="h-1.5 w-1.5 rounded-full bg-brand-500" />
              {t('landing.hero.badge')}
            </span>
            <h1 className="mt-5 text-4xl font-extrabold leading-[1.1] tracking-tight text-slate-900 sm:text-5xl">
              {t('landing.hero.title')}
            </h1>
            <p className="mt-5 max-w-xl text-lg leading-relaxed text-slate-600">
              {t('landing.hero.subtitle')}
            </p>
            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                href={loggedIn ? '/tasks' : '/register'}
                className="rounded-xl bg-brand-600 px-6 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-600/25 transition-all hover:-translate-y-0.5 hover:bg-brand-700"
              >
                {loggedIn ? t('landing.nav.dashboard') : t('landing.hero.ctaPrimary')}
              </Link>
              {!loggedIn && (
                <Link href="/login" className="rounded-xl border border-slate-300 bg-white px-6 py-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-50">
                  {t('landing.hero.ctaSecondary')}
                </Link>
              )}
            </div>
            <p className="mt-4 text-sm text-slate-400">{t('landing.hero.note')}</p>
          </div>

          {/* Product mock: a mini kanban board */}
          <div className="animate-pop lg:justify-self-end">
            <KanbanMock t={t} />
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- Features */}
      <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-24">
        <div className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">{t('landing.featuresTitle')}</h2>
          <p className="mt-3 text-lg text-slate-600">{t('landing.featuresSubtitle')}</p>
        </div>
        <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((key) => (
            <div key={key} className="group rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all hover:-translate-y-1 hover:border-brand-200 hover:shadow-lg hover:shadow-brand-100/60">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600 transition-colors group-hover:bg-brand-600 group-hover:text-white">
                <svg className="h-6 w-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">{ICON[key]}</svg>
              </span>
              <h3 className="mt-4 text-lg font-semibold text-slate-900">{t(`landing.features.${key}.title`)}</h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{t(`landing.features.${key}.desc`)}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ---------------------------------------------------------------- How it works */}
      <section className="border-y border-slate-200 bg-slate-50">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-24">
          <h2 className="text-center text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">{t('landing.howTitle')}</h2>
          <div className="mt-12 grid gap-6 md:grid-cols-3">
            {steps.map((key, i) => (
              <div key={key} className="relative rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-600 text-base font-bold text-white shadow-sm shadow-brand-600/30">
                  {i + 1}
                </span>
                <h3 className="mt-4 text-lg font-semibold text-slate-900">{t(`landing.steps.${key}.title`)}</h3>
                <p className="mt-2 text-sm leading-relaxed text-slate-600">{t(`landing.steps.${key}.desc`)}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ---------------------------------------------------------------- CTA */}
      <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:py-20">
        <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-6 py-14 text-center shadow-xl shadow-brand-600/20 sm:px-12">
          <div aria-hidden className="pointer-events-none absolute -top-16 -right-16 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
          <div aria-hidden className="pointer-events-none absolute -bottom-20 -left-10 h-64 w-64 rounded-full bg-brand-400/30 blur-2xl" />
          <h2 className="relative text-3xl font-bold tracking-tight text-white sm:text-4xl">{t('landing.ctaTitle')}</h2>
          <p className="relative mx-auto mt-3 max-w-xl text-lg text-brand-100">{t('landing.ctaSubtitle')}</p>
          <Link
            href={loggedIn ? '/tasks' : '/register'}
            className="relative mt-8 inline-block rounded-xl bg-white px-7 py-3.5 text-sm font-semibold text-brand-700 shadow-lg transition-transform hover:-translate-y-0.5"
          >
            {loggedIn ? t('landing.nav.dashboard') : t('landing.ctaButton')}
          </Link>
        </div>
      </section>

      {/* ---------------------------------------------------------------- Footer */}
      <footer className="border-t border-slate-200">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-4 py-8 sm:flex-row sm:px-6">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-brand-600 text-xs font-bold text-white">HR</span>
            <span className="text-sm font-semibold text-slate-700">{t('appName')}</span>
            <span className="hidden text-sm text-slate-400 sm:inline">· {t('landing.footerTagline')}</span>
          </div>
          <div className="flex items-center gap-5 text-sm text-slate-500">
            <Link href="/login" className="hover:text-slate-900">{t('landing.nav.login')}</Link>
            <Link href="/register" className="hover:text-slate-900">{t('landing.nav.getStarted')}</Link>
          </div>
        </div>
      </footer>
    </div>
  );
}

/* A decorative mini kanban board — pure CSS, no data. */
function KanbanMock({ t }: { t: (k: string) => string }) {
  const columns = [
    { key: 'tasks.open', dot: 'bg-blue-500', cards: [{ p: 'bg-red-400', w: 'w-3/4' }, { p: 'bg-amber-400', w: 'w-1/2' }] },
    { key: 'tasks.inProgress', dot: 'bg-amber-500', cards: [{ p: 'bg-amber-400', w: 'w-2/3' }] },
    { key: 'tasks.done', dot: 'bg-emerald-500', cards: [{ p: 'bg-slate-300', w: 'w-3/5' }, { p: 'bg-slate-300', w: 'w-1/2' }] },
  ];
  return (
    <div className="w-full max-w-md rotate-1 rounded-2xl border border-slate-200 bg-white p-4 shadow-2xl shadow-slate-300/50 ring-1 ring-slate-900/5">
      <div className="mb-3 flex items-center gap-1.5 px-1">
        <span className="h-2.5 w-2.5 rounded-full bg-red-400" />
        <span className="h-2.5 w-2.5 rounded-full bg-amber-400" />
        <span className="h-2.5 w-2.5 rounded-full bg-emerald-400" />
        <span className="ml-2 text-xs font-medium text-slate-400">{t('tasks.board')}</span>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {columns.map((col) => (
          <div key={col.key} className="rounded-lg bg-slate-100/70 p-2">
            <div className="mb-2 flex items-center gap-1.5 px-0.5">
              <span className={`h-2 w-2 rounded-full ${col.dot}`} />
              <span className="truncate text-[10px] font-semibold text-slate-500">{t(col.key)}</span>
            </div>
            <div className="space-y-1.5">
              {col.cards.map((c, i) => (
                <div key={i} className="rounded-md border border-slate-200 bg-white p-2 shadow-sm">
                  <div className="mb-1.5 flex items-center gap-1">
                    <span className={`h-1.5 w-1.5 rounded-full ${c.p}`} />
                    <span className={`h-1.5 ${c.w} rounded-full bg-slate-200`} />
                  </div>
                  <span className="block h-3 w-3 rounded-full bg-gradient-to-br from-brand-400 to-brand-600" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
