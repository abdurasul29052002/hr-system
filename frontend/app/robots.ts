import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // The app itself is behind auth — no point indexing it (and /api is the backend proxy).
      disallow: ["/tasks", "/stats", "/employees", "/tags", "/tickets", "/admin", "/create-team", "/join", "/api"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
