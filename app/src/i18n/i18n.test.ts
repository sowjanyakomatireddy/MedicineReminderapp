import { describe, expect, it } from 'vitest';
import { resources } from './index';
import { LOCALES } from './locales';

function keys(obj: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([k, v]) => {
    if (k.startsWith('_')) return []; // metadata like _reviewStatus
    const path = prefix ? `${prefix}.${k}` : k;
    return v && typeof v === 'object' ? keys(v as Record<string, unknown>, path) : [path];
  });
}

describe('i18n', () => {
  const enKeys = keys(resources.en.translation).sort();

  it('has a translation file for every listed locale', () => {
    for (const l of LOCALES) expect(resources[l.code], `missing ${l.code}.json`).toBeDefined();
  });

  it.each(LOCALES.map((l) => l.code))('%s has exactly the same keys as English', (code) => {
    expect(keys(resources[code].translation).sort()).toEqual(enKeys);
  });

  it('non-English files are flagged for native-speaker review', () => {
    for (const l of LOCALES.filter((x) => x.code !== 'en')) {
      expect(resources[l.code].translation._reviewStatus).toMatch(/needs review/);
    }
  });
});
