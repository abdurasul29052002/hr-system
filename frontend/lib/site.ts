import type { Metadata } from 'next';
import { LOCALES, LOCALE_HREFLANG, LOCALE_PATH, type Locale } from './landing-copy';

// Central place for public site identity, shared by metadata, sitemap, robots and the manifest.
//
// NEXT_PUBLIC_SITE_URL MUST be set to the real production origin (no trailing slash) in the Vercel
// Production environment, then redeployed — NEXT_PUBLIC_* is inlined at build time, so changing it in the
// dashboard does nothing until the next build. Canonical, Open Graph, JSON-LD, robots and sitemap URLs all
// derive from it; if it points at a host that isn't the one being crawled, Google discards the sitemap as
// cross-host and link previews break.
//
// The fallback is the real production origin, so a build with the env var missing still ships correct
// absolute URLs. (It once fell back to hr-system.vercel.app, which belongs to an unrelated app, and
// briefly to localhost, which put "http://localhost:3000" into the live canonical and sitemap.)
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || 'https://hr.sonic.uz').replace(/\/+$/, '');

export const SITE_NAME = 'HR System';

export const SITE_TITLE = 'HR System — task management & Telegram bot for teams';

export const SITE_DESCRIPTION =
  'Free task manager for teams: a kanban board, roles, live statistics, monthly reports and a ' +
  'Telegram bot to take and finish tasks where your team already chats. Works in English, Russian and Uzbek.';

export const SITE_KEYWORDS = [
  'task management', 'team task manager', 'kanban board', 'project management', 'team collaboration',
  'Telegram bot tasks', 'HR system', 'free task manager',
  'vazifa boshqaruvi', 'jamoa vazifalari', 'kanban doska',
  'управление задачами', 'таск-менеджер', 'канбан доска', 'управление командой',
];

// ---------------------------------------------------------------- localized landing metadata

/**
 * Per-language title/description for the three landing routes. These are what actually appear in the
 * search result, so each is written in its own language rather than machine-translated from the English.
 */
export const LANDING_TITLE: Record<Locale, string> = {
  en: SITE_TITLE,
  ru: 'HR System — управление задачами и Telegram-бот для команд',
  uz: 'HR System — jamoa vazifalarini boshqarish va Telegram bot',
};

export const LANDING_DESCRIPTION: Record<Locale, string> = {
  en: SITE_DESCRIPTION,
  ru:
    'Бесплатный таск-менеджер для команд: канбан-доска, роли и права, статистика в реальном времени, ' +
    'месячные отчёты и Telegram-бот, чтобы брать и закрывать задачи прямо в чате. ' +
    'Интерфейс на русском, узбекском и английском.',
  uz:
    "Jamoalar uchun bepul vazifa boshqaruvi: kanban doska, rollar, jonli statistika, oylik hisobotlar " +
    "va Telegram bot orqali tasklarni to'g'ridan-to'g'ri chatda olish va yakunlash. " +
    "O'zbek, rus va ingliz tillarida.",
};

/** Open Graph locale tags. */
const OG_LOCALE: Record<Locale, string> = { en: 'en_US', ru: 'ru_RU', uz: 'uz_UZ' };

/**
 * Metadata for one landing route, including the hreflang cluster. Every language points at all three
 * URLs (including itself) plus x-default, which is what tells Google these are translations of one page
 * rather than duplicates competing with each other.
 */
export function landingMetadata(locale: Locale): Metadata {
  const languages: Record<string, string> = { 'x-default': LOCALE_PATH.en };
  for (const l of LOCALES) {
    languages[LOCALE_HREFLANG[l]] = LOCALE_PATH[l];
  }
  return {
    // `absolute` opts out of the root layout's "%s · HR System" template — the landing titles already
    // start with the brand, so the template would render "HR System — … · HR System".
    title: { absolute: LANDING_TITLE[locale] },
    description: LANDING_DESCRIPTION[locale],
    alternates: { canonical: LOCALE_PATH[locale], languages },
    openGraph: {
      type: 'website',
      siteName: SITE_NAME,
      title: LANDING_TITLE[locale],
      description: LANDING_DESCRIPTION[locale],
      url: LOCALE_PATH[locale],
      locale: OG_LOCALE[locale],
    },
    twitter: {
      card: 'summary_large_image',
      title: LANDING_TITLE[locale],
      description: LANDING_DESCRIPTION[locale],
    },
  };
}
