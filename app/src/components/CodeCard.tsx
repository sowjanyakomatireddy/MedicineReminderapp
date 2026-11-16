import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { PairingCode } from '../api/family';

/** Shows a one-time family code large enough to read across the room, with copy/share. */
export function CodeCard({ code, help }: { code: PairingCode; help: string }) {
  const { t, i18n } = useTranslation();
  const [copied, setCopied] = useState(false);
  const until = new Date(code.expiresAt).toLocaleString(i18n.resolvedLanguage, {
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  });
  const canShare = typeof navigator !== 'undefined' && 'share' in navigator;

  async function copy() {
    try {
      await navigator.clipboard.writeText(code.code);
      setCopied(true);
    } catch {
      /* clipboard blocked: the code is still on screen */
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-[18px] bg-teal-dark p-4 text-white">
      <p
        className="text-center font-mono text-[34px] font-bold tracking-[0.12em]"
        aria-label={t('code.spoken', { code: code.code.split('').join(' ') })}
      >
        {code.code}
      </p>
      <p className="text-center text-sm text-[#DCEFEC]">{t('code.expires', { time: until })}</p>
      <p className="text-[15px] leading-relaxed">{help}</p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={copy}
          className="min-h-12 flex-1 rounded-xl bg-white font-semibold text-teal-dark"
        >
          {copied ? t('common.copied') : t('common.copy')}
        </button>
        {canShare && (
          <button
            type="button"
            onClick={() => navigator.share({ text: `${help}\n${code.code}` }).catch(() => {})}
            className="min-h-12 flex-1 rounded-xl border-2 border-white font-semibold"
          >
            {t('common.share')}
          </button>
        )}
      </div>
      <span className="sr-only" aria-live="polite">
        {copied ? t('common.copied') : ''}
      </span>
    </div>
  );
}
