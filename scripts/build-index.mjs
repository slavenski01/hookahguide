#!/usr/bin/env node
// Генерирует data/articles.json из frontmatter всех статей в knowledge/.
// Запуск: node scripts/build-index.mjs
import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = new URL('..', import.meta.url).pathname;
const KNOWLEDGE = join(ROOT, 'knowledge');
const OUT = join(ROOT, 'data', 'articles.json');

const SKIP = new Set(['README.md', '_template.md']);

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else if (name.endsWith('.md')) out.push(full);
  }
  return out;
}

// Минимальный парсер frontmatter (YAML-подмножество, используемое в статьях).
function parseFrontmatter(text) {
  const m = text.match(/^---\n([\s\S]*?)\n---/);
  if (!m) return null;
  const fm = {};
  let key = null;
  for (const raw of m[1].split('\n')) {
    if (/^\s*-\s+/.test(raw) && key) {
      fm[key].push(raw.replace(/^\s*-\s+/, '').trim());
      continue;
    }
    const kv = raw.match(/^([a-zA-Z_]+):\s*(.*)$/);
    if (!kv) continue;
    key = kv[1];
    let val = kv[2].trim();
    if (val === '' || val === '[]') {
      fm[key] = val === '[]' ? [] : [];
    } else if (val.startsWith('[') && val.endsWith(']')) {
      fm[key] = val.slice(1, -1).split(',').map(s => s.trim()).filter(Boolean);
    } else {
      fm[key] = val;
    }
  }
  return fm;
}

const articles = [];
for (const file of walk(KNOWLEDGE)) {
  const base = file.split('/').pop();
  const rel = relative(ROOT, file);
  const isGlossary = rel.includes('12-glossariy') && base === 'README.md';
  if (SKIP.has(base) && !isGlossary) continue;
  const fm = parseFrontmatter(readFileSync(file, 'utf8'));
  if (!fm) continue;
  articles.push({
    slug: fm.slug || base.replace(/\.md$/, ''),
    title: fm.title || '',
    section: fm.category || rel.split('/')[1],
    level: fm.level || 'beginner',
    status: fm.status || 'draft',
    tags: Array.isArray(fm.tags) ? fm.tags : [],
    sources: Array.isArray(fm.sources) ? fm.sources.length : 0,
    path: 'knowledge/' + relative(KNOWLEDGE, file),
    updated: fm.updated || null,
  });
}

articles.sort((a, b) => a.path.localeCompare(b.path));
writeFileSync(OUT, JSON.stringify(articles, null, 2) + '\n');
console.log(`articles.json: ${articles.length} статей записано`);
