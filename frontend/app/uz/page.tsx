import type { Metadata } from 'next';
import LandingPage from '@/components/LandingPage';
import { getLandingCopy } from '@/lib/landing-copy';
import { landingMetadata } from '@/lib/site';

export const metadata: Metadata = landingMetadata('uz');

export default function Page() {
  return <LandingPage locale="uz" copy={getLandingCopy('uz')} />;
}
