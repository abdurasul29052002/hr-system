import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

// '/', '/ru' and '/uz' are the three localized landing pages.
const publicPaths = ['/', '/ru', '/uz', '/login', '/register', '/team-access'];
const publicPathPrefixes = ['/join/'];

/**
 * Next.js metadata routes. Crawlers fetch these WITHOUT a session cookie, so they must never fall through
 * to the auth redirect below — a robots.txt that 307s to an HTML login page reads to Google as "no
 * robots.txt at all", which silently voids the whole Disallow list and the Sitemap: reference.
 */
const metadataPaths = ['/robots.txt', '/sitemap.xml', '/manifest.webmanifest'];
const metadataPathPrefixes = ['/opengraph-image', '/icon', '/apple-icon'];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public paths
  if (publicPaths.includes(pathname) || publicPathPrefixes.some(prefix => pathname.startsWith(prefix))) {
    return NextResponse.next();
  }

  // Always serve metadata routes (belt-and-braces alongside the matcher below).
  if (metadataPaths.includes(pathname) || metadataPathPrefixes.some(prefix => pathname.startsWith(prefix))) {
    return NextResponse.next();
  }

  // Check authentication
  const token = request.cookies.get('token')?.value;

  if (!token) {
    const loginUrl = new URL('/login', request.url);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match all request paths except:
     * - _next/static (static files), _next/image (image optimization), _next/data
     * - favicon.ico and the icon metadata routes
     * - robots.txt / sitemap.xml / manifest.webmanifest / opengraph-image (crawlers fetch these
     *   cookie-less; redirecting them to /login breaks indexing and link previews)
     * - API routes
     */
    '/((?!_next/static|_next/image|_next/data|favicon.ico|robots.txt|sitemap.xml|manifest.webmanifest|opengraph-image|icon|apple-icon|api).*)',
  ],
};
