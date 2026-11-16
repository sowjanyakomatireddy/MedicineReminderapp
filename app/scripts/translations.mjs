#!/usr/bin/env node
/**
 * Translation review kit for native speakers.
 *
 *   npm run translations:export
 *     → docs/translations/review-<lang>.csv for every non-English language:
 *       key, English, current draft, an empty "Corrected" column, and notes.
 *       Includes the app (app/src/i18n/locales/*.json) and the backend's push alerts
 *       (backend/src/main/resources/i18n/messages_*.properties). Opens in Excel / Google Sheets.
 *
 *   npm run translations:import -- ../docs/translations/review-te.csv
 *     → copies every filled-in "Corrected" cell back into the app or backend file.
 *       Placeholders like {{name}} or {0} must stay; rows that lose one are refused and listed.
 */
import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..', '..');
const LOCALES = join(root, 'app/src/i18n/locales');
const BACKEND = join(root, 'backend/src/main/resources/i18n');
const OUT = join(root, 'docs/translations');
const HEADER = ['key', 'English', 'Current draft', 'Corrected (leave empty if the draft is right)', 'Notes'];

// ---------------------------------------------------------------- reading and writing the sources

function flatten(obj, prefix = '') {
  return Object.entries(obj).flatMap(([k, v]) => {
    if (k.startsWith('_')) return []; // metadata such as _reviewStatus
    const key = prefix ? `${prefix}.${k}` : k;
    return v && typeof v === 'object' ? flatten(v, key) : [[key, String(v)]];
  });
}

function setPath(obj, path, value) {
  const parts = path.split('.');
  let o = obj;
  for (const p of parts.slice(0, -1)) o = o[p] ??= {};
  o[parts.at(-1)] = value;
}

/** Java .properties (UTF-8, as Spring Boot reads them): key=value lines, # comments. */
function readProperties(file) {
  if (!existsSync(file)) return new Map();
  return new Map(
    readFileSync(file, 'utf8').split('\n')
      .filter((l) => l.trim() && !l.trim().startsWith('#') && l.includes('='))
      .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]),
  );
}

function writeProperty(file, key, value) {
  const lines = existsSync(file) ? readFileSync(file, 'utf8').split('\n') : [];
  const i = lines.findIndex((l) => l.startsWith(`${key}=`));
  if (i >= 0) lines[i] = `${key}=${value}`;
  else lines.splice(lines.length - (lines.at(-1) === '' ? 1 : 0), 0, `${key}=${value}`);
  writeFileSync(file, lines.join('\n'));
}

// ---------------------------------------------------------------- CSV

const cell = (s) => (/[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s);
const toCsv = (rows) => '﻿' + rows.map((r) => r.map((c) => cell(c ?? '')).join(',')).join('\r\n') + '\r\n';

function parseCsv(text) {
  const rows = [];
  let row = [], field = '', quoted = false;
  text = text.replace(/^﻿/, '');
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field || row.length) { row.push(field); rows.push(row); }
  return rows.filter((r) => r.some((c) => c.trim()));
}

// ---------------------------------------------------------------- notes for reviewers

const placeholders = (s) => [...s.matchAll(/\{\{\w+\}\}|\{\d+\}/g)].map((m) => m[0]);

function notes(key, english) {
  const n = [];
  const ph = placeholders(english);
  if (ph.length) n.push(`Keep ${ph.join(' ')} exactly (filled in by the app)`);
  if (/_one$/.test(key)) n.push('Singular form (count = 1)');
  if (/_other$/.test(key)) n.push('Plural form');
  if (key.startsWith('voice.')) n.push('SPOKEN aloud by the alarm: should sound natural and polite to an elder');
  if (key.startsWith('alarm.') || key.startsWith('elder')) n.push('Shown to the elder: short and simple');
  if (key.startsWith('backend:')) n.push('Push alert to family members');
  return n.join('. ');
}

// ---------------------------------------------------------------- export / import

function exportAll() {
  const en = Object.fromEntries(flatten(JSON.parse(readFileSync(join(LOCALES, 'en.json'), 'utf8'))));
  const enBackend = readProperties(join(BACKEND, 'messages.properties'));
  const langs = readdirSync(LOCALES).filter((f) => f.endsWith('.json') && f !== 'en.json').map((f) => f.slice(0, -5));
  for (const lang of langs) {
    const draft = Object.fromEntries(flatten(JSON.parse(readFileSync(join(LOCALES, `${lang}.json`), 'utf8'))));
    const backend = readProperties(join(BACKEND, `messages_${lang}.properties`));
    const rows = [HEADER];
    for (const [key, english] of Object.entries(en)) rows.push([key, english, draft[key] ?? '', '', notes(key, english)]);
    for (const [key, english] of enBackend) {
      rows.push([`backend:${key}`, english, backend.get(key) ?? '', '', notes(`backend:${key}`, english)]);
    }
    const file = join(OUT, `review-${lang}.csv`);
    writeFileSync(file, toCsv(rows));
    console.log(`${file.replace(root + '/', '')}: ${rows.length - 1} strings`);
  }
}

function importFile(path) {
  const rows = parseCsv(readFileSync(path, 'utf8'));
  const lang = path.match(/review-([a-z]{2,3})\.csv$/)?.[1];
  if (!lang) throw new Error('File name must be review-<lang>.csv, e.g. review-te.csv');
  const jsonFile = join(LOCALES, `${lang}.json`);
  const json = JSON.parse(readFileSync(jsonFile, 'utf8'));
  let applied = 0;
  const refused = [];
  for (const [key, english, , corrected] of rows.slice(1)) {
    const fix = (corrected ?? '').trim();
    if (!fix) continue;
    const missing = placeholders(english).filter((p) => !fix.includes(p));
    if (missing.length) {
      refused.push(`${key}: missing ${missing.join(' ')}`);
      continue;
    }
    if (key.startsWith('backend:')) writeProperty(join(BACKEND, `messages_${lang}.properties`), key.slice(8), fix);
    else setPath(json, key, fix);
    applied++;
  }
  writeFileSync(jsonFile, JSON.stringify(json, null, 2) + '\n');
  console.log(`Applied ${applied} correction(s) to ${lang}.`);
  if (refused.length) {
    console.log(`Not applied (a placeholder was removed):\n  ${refused.join('\n  ')}`);
    process.exitCode = 1;
  }
  console.log(`When every row has been checked, set "_reviewStatus" in ${jsonFile.replace(root + '/', '')} to e.g. "reviewed by <name> on <date>".`);
}

const [cmd, file] = process.argv.slice(2);
if (cmd === 'export') exportAll();
else if (cmd === 'import' && file) importFile(resolve(process.cwd(), file));
else {
  console.log('Usage: npm run translations:export | npm run translations:import -- <review-xx.csv>');
  process.exitCode = 1;
}
