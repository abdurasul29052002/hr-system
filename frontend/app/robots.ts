import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      // The app itself is behind auth — no point indexing it (and /api is the backend proxy).
      // /team-access is publicly reachable but is a logged-out search shell with no content of its own.
      disallow: ["/tasks", "/stats", "/employees", "/tags", "/tickets", "/admin", "/create-team", "/join",
        "/team-access", "/api"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
