# HookahGuide

Справочник по кальянной индустрии: база знаний (оборудование, табак, забивка,
миксология, мастерство, уход, бизнес) + бэкенд-API с поиском и аккаунтами + мобильное
приложение. Пользователи читают статьи, ищут, ведут заметки и сохраняют свои миксы,
забивки и варианты кальянов.

**Прод:** API развёрнут на `http://217.144.103.16/` (Kotlin/Ktor + Meilisearch +
PostgreSQL). Контракт API — в [docs/MOBILE_API_SPEC.md](docs/MOBILE_API_SPEC.md).

## Структура

Монорепозиторий: **бэкенд** и **мобильное приложение** в одном репо. На сервер
деплоится **только бэкенд** (Docker-сборка использует лишь `backend/`, `knowledge/`,
`data/`; папка `mobile/` исключена через корневой `.dockerignore`).

```
HookahGuide/
├── knowledge/     # База знаний — статьи в Markdown (источник истины для текстов)
│   ├── README.md  # индекс разделов + дорожная карта
│   └── 00-osnovy … 12-glossariy/
├── data/          # Машиночитаемые справочники в JSON (для приложения)
│   ├── README.md
│   ├── *.json
│   └── schema/    # JSON Schema
├── backend/       # API (Kotlin + Ktor): статьи, поиск, справочники  ← деплоится
│   └── README.md  # эндпоинты + деплой
├── mobile/        # Мобильное приложение (клиент к API)  ← НЕ деплоится на сервер
│   └── README.md
├── docs/          # MOBILE_API_SPEC.md — контракт API для приложения
├── scripts/       # Генерация индекса и валидация
├── .dockerignore  # исключает mobile/ и пр. из контекста сборки бэкенда
├── Caddyfile      # reverse proxy + авто-HTTPS (для домена)
├── docker-compose.yml      # Caddy + API + Meilisearch (домен + HTTPS)
├── docker-compose.ip.yml   # API + Meilisearch (по IP)
└── deploy-no-compose.sh    # запуск без compose-плагина
```

## Backend API

REST API на **Kotlin + Ktor**. Стек:

- **Контент** — статьи и справочники из Markdown/JSON (read-only, в git).
- **Поиск** — **Meilisearch** (со встроенным fallback).
- **Аккаунты** — регистрация/логин, **JWT** (пароли BCrypt).
- **Пользовательские данные** — **PostgreSQL**: заметки, свои миксы, забивки, варианты
  кальянов, заявки на правки статей.

Группы эндпоинтов: `/api/sections`, `/api/articles*`, `/api/search`, `/api/reference/*`
(контент); `/api/auth/*`, `/api/me/*` (аккаунты и пользовательский контент).
Полный контракт — в [docs/MOBILE_API_SPEC.md](docs/MOBILE_API_SPEC.md), детали —
в [backend/README.md](backend/README.md).

### Деплой

Секреты — в `.env` (`cp .env.example .env`): `MEILI_MASTER_KEY`, `POSTGRES_PASSWORD`,
`JWT_SECRET` (+ `DOMAIN`, `ACME_EMAIL` для HTTPS).

| Сценарий | Команда |
|----------|---------|
| По IP, HTTP | `docker compose -f docker-compose.ip.yml up -d --build` |
| Домен + HTTPS (Caddy) | `docker compose up -d --build` |
| Без compose-плагина | `API_PORT=80 ./deploy-no-compose.sh` |

Поднимаются контейнеры: `api`, `meilisearch`, `postgres` (+ `caddy` в HTTPS-режиме).
Данные пользователей — в томе `pg_data`. Подробная инструкция — в [DEPLOY.md](DEPLOY.md).

## Содержание базы знаний

- **37 статей** в 13 разделах, каждая с метаданными (frontmatter) и источниками.
- **Справочники:** бренды табака, сортотипы листа, чаши, угли, вкусовые семейства,
  рецепты миксов, глоссарий.
- Тексты на русском, Markdown. Источники приоритизированы по авторитетности
  (для здоровья — CDC/FDA/Mayo/ALA).

## Команды (контент)

```bash
npm run build:index   # пересобрать data/articles.json из frontmatter статей
npm run validate      # проверить JSON и ссылочную целостность
npm run data          # то и другое
```

## Мобильное приложение

В папке `mobile/` — клиент на **Kotlin Multiplatform + Compose Multiplatform**
(Android + iOS). На сервер не деплоится. Старт — в [mobile/README.md](mobile/README.md).

## Дальнейшие шаги

- Домен + HTTPS (Caddy) — убрать cleartext-HTTP для мобилки.
- Разработка мобильного приложения поверх API.
- Углубление контента; шаринг/модерация пользовательских миксов и забивок.

> ⚠️ Материалы носят образовательный характер. Кальян содержит никотин и продукты
> сгорания — см. раздел [Здоровье и безопасность](knowledge/11-zdorovye/riski-i-bezopasnost.md).
