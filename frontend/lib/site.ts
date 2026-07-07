// Central place for public site identity, shared by metadata, sitemap, robots and the manifest.
// Set NEXT_PUBLIC_SITE_URL on Vercel to your real domain (e.g. https://hrsystem.uz) so canonical,
// Open Graph and sitemap URLs are correct. The fallback is only used until that env var is set.
export const SITE_URL = (process.env.NEXT_PUBLIC_SITE_URL || 'https://hr-system.vercel.app').replace(/\/+$/, '');

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
