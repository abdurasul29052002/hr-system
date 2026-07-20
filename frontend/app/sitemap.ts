import type { MetadataRoute } from "next";
import { LOCALES, LOCALE_HREFLANG, LOCALE_PATH } from "@/lib/landing-copy";
import { SITE_URL } from "@/lib/site";

// Bumped by hand when the public pages actually change. Using new Date() here made every URL claim it had
// changed on every deploy, which trains crawlers to ignore lastmod entirely.
const LAST_MODIFIED = new Date("2026-07-19");

const abs = (path: string) => `${SITE_URL}${path === "/" ? "/" : path}`;

export default function sitemap(): MetadataRoute.Sitemap {
  // Each landing carries the full hreflang cluster, matching the <link rel="alternate"> tags on the pages
  // themselves — Google wants the two to agree.
  const languages = Object.fromEntries(LOCALES.map((l) => [LOCALE_HREFLANG[l], abs(LOCALE_PATH[l])]));

  const landings = LOCALES.map((locale) => ({
    url: abs(LOCALE_PATH[locale]),
    lastModified: LAST_MODIFIED,
    changeFrequency: "weekly" as const,
    priority: locale === "en" ? 1 : 0.9,
    alternates: { languages },
  }));

  // /login is a bare auth form with nothing to index; it stays crawlable but is not advertised here.
  return [
    ...landings,
    { url: abs("/register"), lastModified: LAST_MODIFIED, changeFrequency: "monthly", priority: 0.6 },
  ];
}
