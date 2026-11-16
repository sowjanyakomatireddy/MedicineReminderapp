import { useTranslation } from 'react-i18next';

/**
 * Grandfather's screen. It always shows the linked person's voice language (Telugu for now),
 * not the family member's app language. Real data comes after pairing (Phase 2) and sync (Phase 5).
 */
export function ElderTodayPage() {
  const lang = 'te';
  const { i18n } = useTranslation();
  const t = i18n.getFixedT(lang);
  return (
    <main lang={lang} className="mx-auto flex min-h-dvh max-w-md flex-col gap-6 px-4 py-8 text-[20px]">
      <h1 className="text-[32px] font-bold text-teal-dark">{t('elder.todayTitle')}</h1>
      <p className="rounded-3xl bg-white p-6 text-[22px]">{t('elder.empty')}</p>
    </main>
  );
}
