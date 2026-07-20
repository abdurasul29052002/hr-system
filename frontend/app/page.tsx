import type { Metadata } from 'next';
import LandingPage from '@/components/LandingPage';
import { getLandingCopy } from '@/lib/landing-copy';
import { landingMetadata } from '@/lib/site';

// English landing, served at the site root. Its Russian and Uzbek siblings live at /ru and /uz; the
// hreflang cluster in landingMetadata ties all three together.
export const metadata: Metadata = landingMetadata('en');

export default function Page() {
  return <LandingPage locale="en" copy={getLandingCopy('en')} />;
}
