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
