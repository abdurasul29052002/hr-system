import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

// Bumped by hand when the public pages actually change. Using new Date() here made every URL claim it had
// changed on every deploy, which trains crawlers to ignore lastmod entirely.
const LAST_MODIFIED = new Date("2026-07-19");

export default function sitemap(): MetadataRoute.Sitemap {
  // Only pages worth ranking. /login is a bare auth form with nothing to index; it stays crawlable (so the
  // signup path is followable) but is not advertised here.
  return [
    { url: `${SITE_URL}/`, lastModified: LAST_MODIFIED, changeFrequency: "weekly", priority: 1 },
    { url: `${SITE_URL}/register`, lastModified: LAST_MODIFIED, changeFrequency: "monthly", priority: 0.6 },
  ];
}
