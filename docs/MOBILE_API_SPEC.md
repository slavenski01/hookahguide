# HookahGuide — спецификация для мобильного приложения

Самодостаточный документ: контракт API и обзор готового бэкенда. Этого достаточно,
чтобы начать разработку мобильного приложения в новой сессии без доступа к остальному
контексту.

## 1. Что это за продукт

**HookahGuide** — справочник/база знаний по кальянной индустрии (на русском): оборудование,
табак, забивка, миксология, мастерство, уход, индустрия, здоровье. Контент — **37 статей**
в **13 разделах** + справочники (бренды табака, чаши, угли, вкусовые семейства, рецепты
миксов, глоссарий). Тексты статей — в **Markdown** (рендерит клиент).

Бэкенд готов и развёрнут (Kotlin/Ktor + Meilisearch). Задача мобильного приложения —
удобно показывать эту базу: разделы → список статей → статья; поиск; справочники
(каталог брендов/миксов и т.п.).

## 2. Базовый URL и общие правила

- **Прод (HTTP, по IP):** `http://217.144.103.16/`
- **Авторизация:** нет (публичный read-only API, только GET).
- **Формат:** JSON, UTF-8. Тела статей — Markdown-строки.
- **CORS:** открыт (`*`).
- **Язык контента:** русский.

> ⚠️ Сейчас это **HTTP без TLS** (домена пока нет). Мобильным ОС нужно разрешить
> cleartext-трафик в дев-сборке:
> - **Android:** `android:usesCleartextTraffic="true"` в `<application>` манифеста
>   (или `network-security-config` с доменом-исключением `217.144.103.16`).
> - **iOS:** исключение ATS в `Info.plist` (`NSAppTransportSecurity` →
>   `NSExceptionDomains` → `217.144.103.16` → `NSExceptionAllowsInsecureHTTPLoads = YES`).
> Позже подключим домен + HTTPS (Caddy) — тогда эти исключения убрать.

## 3. Эндпоинты

### GET /health
Состояние сервиса.
```json
{ "status": "ok", "articles": 37, "sections": 13, "searchEngine": "meilisearch" }
```

### GET /api/sections
Список из 13 разделов (отсортированы по `order`).
```json
[
  { "id": "00-osnovy", "order": 0, "title": "Основы", "slug": "osnovy",
    "description": "Что такое кальян, история, устройство, виды, физика дыма, культура и этикет." },
  { "id": "02-tabak", "order": 2, "title": "Табак", "slug": "tabak", "description": "..." }
]
```

### GET /api/articles
Список статей (метаданные, без тела). Параметры (все опциональны):

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `section` | string | — | id раздела (напр. `02-tabak`) |
| `level` | string | — | `beginner` \| `intermediate` \| `advanced` |
| `status` | string | — | `draft` \| `review` \| `done` (сейчас все `done`) |
| `tag` | string | — | фильтр по тегу |
| `limit` | int | 100 | 1..500 |
| `offset` | int | 0 | пагинация |

```json
[
  {
    "slug": "krepost",
    "title": "Крепость табака: от чего зависит и как выбрать",
    "section": "02-tabak",
    "level": "beginner",
    "status": "done",
    "tags": ["табак", "крепость", "никотин", "выбор"],
    "sources": 3,
    "path": "knowledge/02-tabak/krepost.md",
    "updated": "2026-06-24"
  }
]
```

### GET /api/articles/{slug}
Полная статья с телом (Markdown). `slug` — из списка статей.
```json
{
  "slug": "krepost",
  "title": "Крепость табака: от чего зависит и как выбрать",
  "section": "02-tabak",
  "level": "beginner",
  "status": "done",
  "tags": ["табак", "крепость", "никотин", "выбор"],
  "sources": 3,
  "path": "knowledge/02-tabak/krepost.md",
  "updated": "2026-06-24",
  "body": "# Крепость табака...\n\n(полный Markdown статьи)"
}
```
404, если не найдено: `{ "error": "article_not_found" }`

### GET /api/search
Полнотекстовый поиск по статьям. Параметры:

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `q` | string | — | поисковый запрос (обязателен; пустой → пустой результат) |
| `section` | string | — | ограничить разделом |
| `limit` | int | 20 | 1..50 |

```json
{
  "query": "густой дым",
  "engine": "meilisearch",
  "total": 3,
  "hits": [
    { "slug": "gustoy-dym", "title": "Как добиться густого дыма",
      "section": "08-masterstvo", "level": "intermediate",
      "snippet": "…густой «молочный» дым — результат связки…" }
  ]
}
```
`engine` может быть `meilisearch` (норма) или `builtin` (fallback) — для клиента разницы нет.

### GET /api/reference/{name}
Справочные данные (как есть, массив объектов). Доступные `name`:
`brands`, `bowls`, `coals`, `tobacco-leaf`, `flavor-families`, `mixes`, `glossary`,
`sections`, `articles`.
404 при неизвестном имени: `{ "error": "reference_not_found: доступны ..." }`

## 4. Модели справочников

**brands** — бренды табака:
```json
{ "id": "al-fakher", "name": "Al Fakher", "origin": "ОАЭ", "since": 1999,
  "leaf": "virginia", "strength": "light", "lines": [], "notes": "..." }
```
`leaf`: `virginia|burley|oriental`; `strength`: `light|medium|strong`.

**mixes** — рецепты миксов:
```json
{ "id": "cherry-cola", "name": "Вишня + кола", "category": "drinks",
  "components": [ { "flavor": "вишня", "family": "berry", "share": 60 },
                  { "flavor": "кола", "family": "drinks", "share": 40 } ],
  "profile": "Вишнёвая кола, ностальгическая классика." }
```

**bowls** — чаши: `{ id, name, nameEn, shape, materials[], bestFor[], packing[], notes, articleRef }`
**coals** — угли: `{ id, name, nameEn, recommended, ash, coMonoxide, sizesMm[], lightingTimeMin[], notes, articleRef }`
**tobacco-leaf** — сорта листа: `{ id, name, nameEn, nicotinePct[], sugar, characteristics, typicalBrands[], articleRef }`
**flavor-families** — вкусовые семейства: `{ id, name, examples[], role, notes }`
**glossary** — термины: `{ term, category, definition, articleRef }`

> `articleRef` (если есть) указывает на статью без расширения, напр. `02-tabak/krepost`
> → её можно открыть как `/api/articles/krepost` (slug — последний сегмент).

## 5. Разделы (id → название)

`00-osnovy` Основы · `01-oborudovanie` Оборудование · `02-tabak` Табак ·
`03-ugli` Угли · `04-zabivka` Забивка · `05-chashi` Чаши · `06-nagrev` Нагрев ·
`07-miksologiya` Миксология · `08-masterstvo` Мастерство · `09-uhod` Уход ·
`10-industriya` Индустрия · `11-zdorovye` Здоровье и безопасность · `12-glossariy` Глоссарий

## 6. Предлагаемые экраны приложения (ориентир)

1. **Главная / разделы** — `GET /api/sections` → плитки 13 разделов.
2. **Список статей раздела** — `GET /api/articles?section={id}` → карточки (title, level, tags).
3. **Статья** — `GET /api/articles/{slug}` → рендер Markdown `body` + теги/уровень.
4. **Поиск** — `GET /api/search?q=` → результаты со сниппетами, тап → статья.
5. **Каталоги** (опц.) — `GET /api/reference/brands` (фильтр по крепости/листу),
   `GET /api/reference/mixes` (конструктор/просмотр миксов).

Рекомендации: кешировать `/api/sections` и списки; Markdown рендерить библиотекой
(Android — Markwon; iOS — swift-markdown-ui или Down). Дизайн «офлайн-френдли»: данные
маленькие, можно кешировать локально.

## 7. Технический контекст (на случай вопросов)

- Бэкенд: **Kotlin + Ktor 3** (движок CIO), поиск **Meilisearch**, контент из Markdown.
- Деплой: Docker на VPS (IHC.ru), API на порту **80**. Репозиторий приватный:
  `github.com/slavenski01/hookahguide`.
- Контракт стабилен; новые статьи/справочники добавляются без изменения схемы ответов.
