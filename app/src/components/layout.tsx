import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

/** Page shell for family screens: phone-width column, room for the bottom nav. */
export function FamilyPage({ children, nav = true }: { children: ReactNode; nav?: boolean }) {
  return (
    <>
      <main className={`mx-auto flex min-h-dvh max-w-md flex-col gap-4 px-4 pt-6 ${nav ? 'pb-28' : 'pb-8'}`}>
        {children}
      </main>
      {nav && <BottomNav />}
    </>
  );
}

export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="flex min-h-11 items-center self-start font-bold text-teal">
      <span aria-hidden="true">←&nbsp;</span>
      {children}
    </Link>
  );
}

export function SectionHeading({ children }: { children: ReactNode }) {
  return <h2 className="mt-2 text-[13px] font-semibold tracking-[0.04em] text-muted uppercase">{children}</h2>;
}

/** A round initial. Colour is decoration only; the name is always shown next to it. */
const AVATAR_COLOURS = [
  'bg-[#DCEFEC] text-teal-dark',
  'bg-[#FBE6EA] text-[#5A2431]',
  'bg-[#FFF1C2] text-[#5E4700]',
  'bg-[#E3E8FA] text-[#25336B]',
];

export function Avatar({ name }: { name: string }) {
  const sum = [...name].reduce((a, c) => a + c.codePointAt(0)!, 0);
  return (
    <span
      aria-hidden="true"
      className={`flex size-12 flex-none items-center justify-center rounded-full text-xl font-bold ${AVATAR_COLOURS[sum % AVATAR_COLOURS.length]}`}
    >
      {[...name.trim()][0]?.toUpperCase() ?? '?'}
    </span>
  );
}

export function Chip({ children, tone = 'grey' }: { children: ReactNode; tone?: 'grey' | 'amber' | 'green' | 'pink' }) {
  const tones = {
    pink: 'bg-[#FBE6EA] text-[#5A2431]',
    grey: 'bg-[#E8EEF0] text-ink',
    amber: 'bg-before-bg text-before-fg',
    green: 'bg-after-bg text-after-fg',
  };
  return <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

function Icon({ d }: { d: string }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={d} />
    </svg>
  );
}

/** Bottom tabs from the prototype: Home, Medicines, Routine, Family. */
export function BottomNav() {
  const { t } = useTranslation();
  const items = [
    { to: '/', label: t('nav.home'), d: 'M4 11l8-7 8 7v9H4z', end: true },
    {
      to: '/medicines',
      label: t('nav.medicines'),
      d: 'M10.5 20.5 3.5 13.5a5 5 0 0 1 7-7l7 7a5 5 0 0 1-7 7zM8.5 8.5l7 7',
      end: false,
    },
    {
      to: '/routine',
      label: t('nav.routine'),
      d: 'M4 5h16v15H4zM4 10h16M9 3v4M15 3v4',
      end: false,
    },
    {
      to: '/family',
      label: t('nav.family'),
      d: 'M9 4.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M17 6.6a2.4 2.4 0 1 1 0 4.8M17 14c2.5 0 4.5 2 4.5 4.5',
      end: false,
    },
  ];
  return (
    <nav aria-label={t('nav.label')} className="fixed inset-x-0 bottom-0 border-t border-[#DCE3E2] bg-white">
      <ul className="mx-auto flex max-w-md justify-around px-2 pt-2 pb-[max(env(safe-area-inset-bottom),12px)]">
        {items.map((i) => (
          <li key={i.to}>
            <NavLink
              to={i.to}
              end={i.end}
              className={({ isActive }) =>
                `flex min-h-14 min-w-16 flex-col items-center justify-center gap-1 text-xs ${isActive ? 'font-bold text-teal' : 'font-semibold text-muted'}`
              }
            >
              <Icon d={i.d} />
              {i.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function Loading() {
  const { t } = useTranslation();
  return (
    <p role="status" className="p-6 text-lg">
      {t('common.loading')}
    </p>
  );
}
