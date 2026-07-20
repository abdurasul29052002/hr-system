import en from './locales/en.json';
import ru from './locales/ru.json';
import uz from './locales/uz.json';

/**
 * Server-side access to the landing copy.
 *
 * The app itself uses react-i18next with a client-side language toggle, which is fine for screens behind
 * auth but invisible to crawlers: one URL served all three languages, so Google only ever indexed English.
 * The landing page — the only indexable surface — instead gets one route per language, rendered on the
 * server from these dictionaries, with hreflang tying them together.
 */

export const LOCALES = ['en', 'ru', 'uz'] as const;
export type Locale = (typeof LOCALES)[number];

/** Where each locale's landing lives. English is the site root, so `/` stays the primary URL. */
export const LOCALE_PATH: Record<Locale, string> = { en: '/', ru: '/ru', uz: '/uz' };

/** BCP-47 tags for hreflang and the <html lang> attribute. */
export const LOCALE_HREFLANG: Record<Locale, string> = { en: 'en', ru: 'ru', uz: 'uz' };

const DICTS = { en, ru, uz } as const;

/** Exactly the strings the landing renders — kept narrow so the RSC payload stays small. */
export type LandingCopy = {
  appName: string;
  landing: (typeof en)['landing'];
  board: { title: string; open: string; inProgress: string; done: string };
};

export function getLandingCopy(locale: Locale): LandingCopy {
  const d = DICTS[locale];
  return {
    appName: d.appName,
    landing: d.landing as (typeof en)['landing'],
    board: {
      title: d.tasks.board,
      open: d.tasks.open,
      inProgress: d.tasks.inProgress,
      done: d.tasks.done,
    },
  };
}
