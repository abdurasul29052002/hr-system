'use client';

import { useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import '@/lib/i18n';

export interface LightboxImage {
  id: number;
  url: string;
  fileName: string;
}

/**
 * Full-screen image viewer, messenger style: click a picture to open it here, arrow through the rest of
 * the thread's images, Escape or a backdrop click to leave.
 *
 * Scroll locking is deliberately NOT handled here: this viewer only ever opens inside the task modal,
 * which already owns that lock. Racing it over the single `document.body.style.overflow` global left the
 * page permanently unscrollable whenever both unmounted in the same commit (React tears a deleted
 * subtree down parent-first, so the modal's cleanup ran before ours and we re-applied the lock).
 */
export default function ImageLightbox({
  images,
  index,
  onIndexChange,
  onClose,
}: {
  images: LightboxImage[];
  index: number;
  onIndexChange: (next: number) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const containerRef = useRef<HTMLDivElement>(null);
  const count = images.length;

  const go = useCallback(
    (delta: number) => {
      if (count < 2) return;
      onIndexChange((index + delta + count) % count);
    },
    [count, index, onIndexChange],
  );

  useEffect(() => {
    // Handled in the CAPTURE phase and stopped there: this viewer opens inside the task modal, whose own
    // document-level (bubble) Escape listener would otherwise fire too and close the task behind us. Tab is
    // trapped so focus cannot reach the comment form sitting invisibly behind the overlay.
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onClose();
      } else if (e.key === 'ArrowRight') {
        e.stopPropagation();
        go(1);
      } else if (e.key === 'ArrowLeft') {
        e.stopPropagation();
        go(-1);
      } else if (e.key === 'Tab') {
        const focusable = containerRef.current?.querySelectorAll<HTMLElement>('button, a[href]');
        if (!focusable || focusable.length === 0) return;
        e.preventDefault();
        e.stopPropagation();
        const items = Array.from(focusable);
        const at = items.indexOf(document.activeElement as HTMLElement);
        const next = e.shiftKey
          ? (at <= 0 ? items.length - 1 : at - 1)
          : (at === items.length - 1 ? 0 : at + 1);
        items[next].focus();
      }
    };
    document.addEventListener('keydown', onKey, true);
    return () => document.removeEventListener('keydown', onKey, true);
  }, [go, onClose]);

  // Pull focus into the overlay on open so the very first Tab already stays inside it, and hand focus back
  // to whatever opened us on close — otherwise focus falls to <body> and the next Tab walks the page
  // sitting behind the still-open task modal.
  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    containerRef.current?.focus();
    return () => previouslyFocused?.focus?.();
  }, []);

  const current = images[index];
  if (!current) return null;

  const stop = (e: React.MouseEvent) => e.stopPropagation();

  // overflow-hidden + overscroll-contain make the overlay its own scroll container, so a wheel/trackpad
  // gesture over the viewer cannot chain through and scroll the task modal's scrollable backdrop behind it.
  return (
    <div
      ref={containerRef}
      tabIndex={-1}
      role="dialog"
      aria-modal="true"
      aria-label={current.fileName}
      className="animate-fade fixed inset-0 z-[60] flex flex-col overflow-hidden overscroll-contain bg-slate-950/90 outline-none"
      onMouseDown={onClose}
    >
      <div className="flex items-center gap-3 px-4 py-3 text-white" onMouseDown={stop}>
        <p className="min-w-0 flex-1 truncate text-sm font-medium">{current.fileName}</p>
        {count > 1 && (
          <span className="shrink-0 text-xs tabular-nums text-white/60">{index + 1} / {count}</span>
        )}
        <a
          href={current.url}
          target="_blank"
          rel="noopener noreferrer"
          title={t('comments.download')}
          aria-label={t('comments.download')}
          className="rounded-lg p-1.5 text-white/70 transition-colors hover:bg-white/10 hover:text-white"
        >
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
        </a>
        <button
          onClick={onClose}
          aria-label={t('common.close')}
          className="rounded-lg p-1.5 text-white/70 transition-colors hover:bg-white/10 hover:text-white"
        >
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div className="relative flex min-h-0 flex-1 items-center justify-center px-4 pb-6">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={current.url}
          alt={current.fileName}
          className="animate-pop max-h-full max-w-full rounded-lg object-contain shadow-2xl"
          onMouseDown={stop}
        />
        {count > 1 && (
          <>
            <NavButton side="left" glyph="‹" label={t('comments.previousImage')} onClick={() => go(-1)} />
            <NavButton side="right" glyph="›" label={t('comments.nextImage')} onClick={() => go(1)} />
          </>
        )}
      </div>
    </div>
  );
}

function NavButton({
  side,
  glyph,
  label,
  onClick,
}: {
  side: 'left' | 'right';
  glyph: string;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      onMouseDown={(e) => e.stopPropagation()}
      onClick={onClick}
      aria-label={label}
      title={label}
      className={`absolute top-1/2 -translate-y-1/2 ${side === 'left' ? 'left-2' : 'right-2'} flex h-10 w-10 items-center justify-center rounded-full bg-white/10 pb-1 text-3xl leading-none text-white/80 backdrop-blur transition-colors hover:bg-white/20 hover:text-white`}
    >
      <span aria-hidden>{glyph}</span>
    </button>
  );
}
