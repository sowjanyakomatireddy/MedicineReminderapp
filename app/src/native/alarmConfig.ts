import i18n from '../i18n';
import { findLocale } from '../i18n/locales';
import type { Food } from '../api/medicines';
import { COLOURS, FOODS, SHAPES } from '../api/medicines';
import { spokenSentence } from '../voice/sentence';
import type { AlarmChip, AlarmConfig, AlarmProfile, AlarmTemplates, AlarmUi, NativeAlarm } from './medAlarm';

export interface AlarmPerson {
  name: string;
  voiceLanguage: string;
  timeZone: string;
}

/** BCP-47 voice for a language code, e.g. te → te-IN. */
export const voiceLocale = (lang: string) => findLocale(lang)?.bcp47 ?? lang;

/** All words on the native alarm and Taken screens, in the person's language (from the i18n files). */
export function alarmUi(lang: string): AlarmUi {
  const t = i18n.getFixedT(lang);
  return {
    medicineTime: t('alarm.medicineTime'),
    routineTime: t('alarm.routineTime'),
    taken: t('alarm.taken'),
    takenDone: t('alarm.takenDone'),
    listenAgain: t('alarm.listenAgain'),
    voiceSays: t('alarm.voiceSays'),
    morning: t('alarm.morning'),
    afternoon: t('alarm.afternoon'),
    evening: t('alarm.evening'),
    night: t('alarm.night'),
    takenTitle: t('alarm.takenTitle'),
    takenLine: raw(lang, 'alarm.takenLine'),
    nextMedicine: t('alarm.nextMedicine'),
    familyInformed: t('alarm.familyInformed'),
    familyLater: t('alarm.familyLater'),
    ok: t('alarm.ok'),
  };
}

/** A translation with its {{blanks}} left in, for the native side to fill. */
function raw(lang: string, key: string): string {
  return (i18n.getResource(lang, 'translation', key) ?? i18n.getResource('en', 'translation', key)) as string;
}

/** The sentence template and word lists in the person's language (see AlarmBuilder.java). */
export function alarmTemplates(lang: string): AlarmTemplates {
  const t = i18n.getFixedT(lang);
  const map = (keys: readonly string[], prefix: string) => Object.fromEntries(keys.map((k) => [k, t(`${prefix}${k}`)]));
  return {
    sentence: raw(lang, 'voice.sentence'),
    foodPhrase: map(FOODS, 'voice.food'),
    food: map(FOODS, 'food.'),
    shape: map(SHAPES, 'shape.'),
    colour: map(COLOURS, 'colour.'),
  };
}

/** Everything the phone needs to speak and show alarms for this person. */
export function alarmProfile(person: AlarmPerson): AlarmProfile {
  return {
    timeZone: person.timeZone,
    lang: voiceLocale(person.voiceLanguage),
    ui: alarmUi(person.voiceLanguage),
    templates: alarmTemplates(person.voiceLanguage),
  };
}

/** Chips under the name: food rule, and shape · colour (e.g. "గుండ్రని · తెల్లని"). */
export function alarmChips(lang: string, food: Food, shape?: string, color?: string): AlarmChip[] {
  const t = i18n.getFixedT(lang);
  const chips: AlarmChip[] = [];
  if (food !== 'ANY') chips.push({ text: t(`food.${food}`), tone: food === 'BEFORE' ? 'before' : 'after' });
  const look = [shape && t(`shape.${shape}`), color && t(`colour.${color}`)].filter(Boolean).join(' · ');
  if (look) chips.push({ text: look, tone: 'grey' });
  return chips;
}

/** For the debug screen's sample alarm. Real alarms are built natively from the sync (AlarmBuilder.java). */
export function alarmConfig(person: AlarmPerson, alarms: NativeAlarm[]): AlarmConfig {
  return { timeZone: person.timeZone, lang: voiceLocale(person.voiceLanguage), ui: alarmUi(person.voiceLanguage), alarms };
}

/** The test alarm: a pretend tablet, spoken with the person's real sentence template. */
export function testAlarm(person: AlarmPerson, kind: 'MEDICINE' | 'ROUTINE' = 'MEDICINE') {
  const lang = person.voiceLanguage;
  const t = i18n.getFixedT(lang);
  const name = t('elderSetup.testMedicine');
  return {
    kind,
    title: name,
    subtitle: t('elderSetup.testSubtitle'),
    chips: alarmChips(lang, 'BEFORE', 'ROUND', 'WHITE'),
    spoken: spokenSentence(lang, { person: person.name, voiceName: name, food: 'BEFORE' }),
    shape: 'ROUND',
    color: 'WHITE',
  };
}
