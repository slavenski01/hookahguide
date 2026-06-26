# Data layer — справочные данные

Структурированные **read-only** данные в JSON (каталоги, индекс). Источник истины для
текстов — `../knowledge/` (Markdown); здесь — машиночитаемые справочники. Бэкенд отдаёт
их через `/api/reference/{name}`.

> Это **редакционные данные** (бренды, чаши, миксы-рецепты и т.п.), общие для всех.
> **Пользовательский** контент (свои миксы/забивки/кальяны/заметки) хранится отдельно —
> в PostgreSQL, см. `backend/` и эндпоинты `/api/me/*`.

## Файлы

| Файл | Содержание | Связь |
|------|-----------|-------|
| `sections.json` | 13 разделов базы знаний (таксономия) | `id` ↔ папки `knowledge/` |
| `articles.json` | **Генерируется** из frontmatter статей | `section` → `sections.id`, `path` → MD-файл |
| `tobacco-leaf.json` | Сортотипы листа (Вирджиния/Берли/Ориентал) | — |
| `brands.json` | Бренды табака | `leaf` → `tobacco-leaf.id` |
| `bowls.json` | Виды чаш | `articleRef` → статья |
| `coals.json` | Виды углей | `articleRef` → статья |
| `flavor-families.json` | Вкусовые семейства | `id` ← используется в `mixes` |
| `mixes.json` | Рецепты миксов | `components[].family` → `flavor-families.id` |
| `glossary.json` | Термины глоссария | `articleRef` → статья |
| `schema/*.schema.json` | JSON Schema для основных сущностей | — |

## Команды

```bash
npm run build:index   # пересобрать data/articles.json из knowledge/
npm run validate      # проверить JSON + ссылочную целостность
npm run data          # build:index + validate
```

## Соглашения

- **id / slug** — `kebab-case`, латиница, стабильны (на них ссылаются).
- `articles.json` **не редактировать вручную** — генерируется скриптом из frontmatter.
- Доли в миксах (`components[].share`) суммируются ≈100.
- Поля enum (`strength`, `leaf`, `category`) — см. соответствующие схемы в `schema/`.

## Как использовать в приложении

Все файлы — обычный JSON, импортируются напрямую. Пример связи:

```
mix.components[].family → flavor-families.id
brand.leaf             → tobacco-leaf.id
article.section        → sections.id
*.articleRef           → knowledge/<ref>.md  (рендер Markdown)
```
