#!/usr/bin/env node
// Валидирует data/*.json: корректность JSON + ссылочная целостность.
// Запуск: node scripts/validate.mjs
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const DATA = join(ROOT, 'data');
const load = (f) => JSON.parse(readFileSync(join(DATA, f), 'utf8'));

const errors = [];
const err = (m) => errors.push(m);

const sections = load('sections.json');
const leaves = load('tobacco-leaf.json');
const brands = load('brands.json');
const bowls = load('bowls.json');
const coals = load('coals.json');
const families = load('flavor-families.json');
const mixes = load('mixes.json');
const glossary = load('glossary.json');
const articles = load('articles.json');

const sectionIds = new Set(sections.map(s => s.id));
const leafIds = new Set(leaves.map(l => l.id));
const familyIds = new Set(families.map(f => f.id));
const articlePaths = new Set(articles.map(a => a.path.replace(/^knowledge\//, '').replace(/\.md$/, '')));

const refExists = (ref) => !ref || articlePaths.has(ref) || existsSync(join(ROOT, 'knowledge', ref + '.md'));

// brands -> leaf
for (const b of brands) {
  if (b.leaf && !leafIds.has(b.leaf)) err(`brand "${b.id}": неизвестный лист "${b.leaf}"`);
  if (!['light', 'medium', 'strong'].includes(b.strength)) err(`brand "${b.id}": крепость "${b.strength}"`);
}
// articleRef целостность
for (const set of [['bowls', bowls], ['coals', coals], ['leaves', leaves], ['glossary', glossary]]) {
  for (const item of set[1]) {
    if (item.articleRef && !refExists(item.articleRef)) err(`${set[0]}: битая ссылка articleRef "${item.articleRef}"`);
  }
}
// mixes: суммы долей и семейства
for (const m of mixes) {
  const sum = m.components.reduce((s, c) => s + c.share, 0);
  if (sum < 95 || sum > 105) err(`mix "${m.id}": сумма долей ${sum} вне диапазона 95–105`);
  for (const c of m.components) {
    if (c.family && !familyIds.has(c.family)) err(`mix "${m.id}": неизвестное семейство "${c.family}"`);
  }
}
// articles -> section
for (const a of articles) {
  if (!sectionIds.has(a.section)) err(`article "${a.slug}": неизвестный раздел "${a.section}"`);
}

const counts = { sections: sections.length, leaves: leaves.length, brands: brands.length, bowls: bowls.length, coals: coals.length, families: families.length, mixes: mixes.length, glossary: glossary.length, articles: articles.length };
console.log('Загружено:', JSON.stringify(counts));
if (errors.length) {
  console.error(`\n❌ Ошибок валидации: ${errors.length}`);
  for (const e of errors) console.error('  - ' + e);
  process.exit(1);
}
console.log('✅ Все JSON валидны, ссылочная целостность в порядке.');
