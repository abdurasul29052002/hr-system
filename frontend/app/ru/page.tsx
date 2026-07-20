import type { Metadata } from 'next';
import LandingPage from '@/components/LandingPage';
import { getLandingCopy } from '@/lib/landing-copy';
import { landingMetadata } from '@/lib/site';

export const metadata: Metadata = landingMetadata('ru');

export default function Page() {
  return <LandingPage locale="ru" copy={getLandingCopy('ru')} />;
}
