import i18n from '../i18n';
import { findLocale } from '../i18n/locales';
import type { Food } from '../api/medicines';

/**
 * The sentence the alarm speaks, built from the per-language template in the i18n files
 * (voice.sentence and voice.food*), e.g. Telugu: "తాతయ్య, ఇది బీపీ మాత్ర. భోజనానికి ముందు వేసుకోండి."
 * The same function feeds the Android alarm in Phase 4, so the preview is exactly what is spoken.
 */
export function spokenSentence(lang: string, input: { person: string; voiceName: string; food: Food }): string {
  const t = i18n.getFixedT(lang);
  return t('voice.sentence', {
    person: input.person,
    voiceName: input.voiceName,
    foodPhrase: t(`voice.food${input.food}`),
  });
}

/** Whether this browser has a Text-to-Speech voice for the language (null if speech is not supported at all). */
export function hasBrowserVoice(lang: string): boolean | null {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return null;
  const want = (findLocale(lang)?.bcp47 ?? lang).toLowerCase();
  return window.speechSynthesis
    .getVoices()
    .some((v) => v.lang.toLowerCase().replace('_', '-') === want || v.lang.toLowerCase().startsWith(`${lang}-`));
}

/** Speaks a preview on this device. The elder's phone uses Android Text-to-Speech instead (Phase 4). */
export function speakPreview(lang: string, text: string) {
  if (!('speechSynthesis' in window)) return;
  const u = new SpeechSynthesisUtterance(text);
  u.lang = findLocale(lang)?.bcp47 ?? lang;
  u.rate = 0.9;
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(u);
}
