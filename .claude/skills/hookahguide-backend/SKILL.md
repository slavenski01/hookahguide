---
name: hookahguide-backend
description: How the HookahGuide backend works and how to run, deploy, update, and extend it. Kotlin/Ktor API over a Markdown knowledge base with Meilisearch search, deployed via Docker on a VPS. Use when working on the backend (endpoints, content, search, deploy) or when you need the API contract for the mobile app.
---

# HookahGuide Backend

REST API на **Kotlin + Ktor 3** поверх базы знаний по кальянам (Markdown + JSON), с
поиском через **Meilisearch**. Полный контракт API для клиента — в
[docs/MOBILE_API_SPEC.md](../../../docs/MOBILE_API_SPEC.md).

## Структура репозитория

```
knowledge/          # Источник истины: статьи в Markdown (37 шт., 13 разделов)
  00-osnovy/ … 12-glossariy/   каждая статья — frontmatter + тело
  README.md         # индекс разделов
  _template.md      # шаблон статьи
data/               # Машиночитаемые справочники (JSON) + JSON Schema
  sections.json brands.json mixes.json bowls.json coals.json
  tobacco-leaf.json flavor-families.json glossary.json
  articles.json     # ГЕНЕРИРУЕТСЯ из frontmatter — вручную НЕ править
  schema/*.schema.json
backend/            # Kotlin/Ktor приложение
  src/main/kotlin/com/hookahguide/
    Application.kt        # main, конфиг (env), роуты
    ContentRepository.kt  # загрузка knowledge/*.md + data/*.json, парсер frontmatter
    SearchService.kt      # Meilisearch + встроенный fallback
    Models.kt             # DTO (@Serializable)
  Dockerfile        # multi-stage: gradle build → JRE runtime
scripts/            # build-index.mjs (генерит articles.json), validate.mjs
docker-compose.yml          # Caddy + API + Meili (для домена + HTTPS)
docker-compose.ip.yml       # API(:8080) + Meili (по IP)
deploy-no-compose.sh        # запуск через голый docker run (если нет compose-плагина)
Caddyfile, .env.example, DEPLOY.md
```

## Как работает

1. На старте `ContentRepository` читает `CONTENT_ROOT/knowledge/**.md` (frontmatter +
   тело) и `CONTENT_ROOT/data/*.json` (read-only контент).
2. `SearchService.indexAll()` дожидается Meilisearch (retry) и индексирует статьи.
   Если Meili недоступен — прозрачный fallback на встроенный поиск (API не падает).
3. `Persistence.init()` подключает БД (Postgres в проде, H2 локально), создаёт схему
   (retry — БД может стартовать позже). `UserService` — CRUD пользовательского контента.
4. Роуты: контент (`/api/sections`, `/api/articles*`, `/api/search`, `/api/reference/*`),
   аккаунты (`/api/auth/register|login`), пользовательский контент под JWT
   (`/api/me`, `/api/me/{notes|mixes|packings|hookahs|edit-requests}`).

Конфиг через env: `PORT`, `HOST`, `CONTENT_ROOT`, `MEILI_URL`, `MEILI_MASTER_KEY`,
`DATABASE_URL` (дефолт H2 in-memory), `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`.

## Слои данных

- **Контент** (статьи, справочники) — Markdown/JSON в файлах, read-only, в git.
- **Пользовательские данные** (users, notes, edit_requests, user_mixes, user_packings,
  user_hookahs) — в PostgreSQL (Exposed + HikariCP). JSON-поля хранятся как text для
  переносимости H2/Postgres. Пароли — BCrypt, сессии — JWT (30 дней).

## Частые задачи

**Добавить/изменить статью:** создать `.md` в нужном `knowledge/<раздел>/` по
`_template.md` (frontmatter: title, slug, category, tags, level, status, sources,
updated), затем:
```bash
npm run data        # пересобрать data/articles.json + валидация целостности
```

**Локальный запуск (без Docker, встроенный поиск):**
```bash
cd backend && ./gradlew buildFatJar
CONTENT_ROOT=.. java -jar build/libs/hookahguide-api-all.jar
```

**Проверить сборку после правок Kotlin:**
```bash
cd backend && ./gradlew buildFatJar
```

## Монорепо

Репозиторий содержит и бэкенд, и мобильное приложение (`mobile/`). На сервер
деплоится **только бэкенд**: Dockerfile копирует лишь `backend/`, `knowledge/`,
`data/`, а корневой `.dockerignore` исключает `mobile/`, `docs/`, `.git` и пр. из
контекста сборки. Поэтому код приложения в репо не влияет на серверный билд.

## Прод-деплой (текущее состояние)

- VPS на **IHC.ru**, Ubuntu 24.04, IP **217.144.103.16**, hostname `p1028305`.
- На сервере **нет docker compose plugin** → используется `deploy-no-compose.sh`
  (голый `docker run`: контейнеры `hookahguide-api` + `hookahguide-meili` в сети
  `hookah-net`).
- API опубликован на **порту 80** (не 8080: IHC фильтрует нестандартные порты).
  Базовый URL: `http://217.144.103.16/`.
- Репозиторий приватный: `github.com/slavenski01/hookahguide`.

**Обновить контент на сервере:**
```bash
cd hookahguide && git pull && docker restart hookahguide-api   # переиндексация Meili
```
**Пересобрать после изменения кода:**
```bash
API_PORT=80 ./deploy-no-compose.sh
```

## Подводные камни (важно!)

- **Kotlin-комментарии:** НЕ писать `data/*.json` или `knowledge/**/*.md` в комментариях
  — последовательность слеш-звезда открывает вложенный блок-комментарий и ломает файл.
  Писать «json-файлы из data» и т.п.
- **articles.json генерируется** скриптом — не редактировать руками; после правок
  статей запускать `npm run data`.
- **Сеть контейнеров:** API и Meili должны быть в одной docker-сети (`hookah-net`),
  иначе API не резолвит `hookahguide-meili` и уходит в `builtin`. Починка:
  `docker network connect hookah-net hookahguide-meili && docker restart hookahguide-api`.
- **Сеть пользователя:** рабочий Cisco VPN ломает SSH (banner timeout) и часть портов —
  деплой/проверки делать через мобильный хотспот или веб-консоль провайдера; внешнюю
  доступность проверять онлайн-чекером портов или с телефона без VPN.
- **Порт 8080 у IHC закрыт**, 80 — открыт. Для HTTPS позже: домен + Caddy (`docker-compose.yml`).
- **Gradle-прокси:** в `~/.gradle/gradle.properties` прописан рабочий прокси
  `127.0.0.1:10809`. Если он выключен — `./gradlew` падает с `Connection refused` при
  докачке. Обход для сборки (ничего не коммитя):
  `./gradlew ... -Dhttp.proxyHost= -Dhttps.proxyHost= -Dhttp.nonProxyHosts='*' -Dhttps.nonProxyHosts='*'`.
- **Exposed 0.57:** в `deleteWhere { }` лямбда имеет receiver-таблицу, не
  SqlExpressionBuilder → `eq` не виден. Оборачивать условие в `Op.build { ... }`.
- **Локальный запуск без Postgres:** `DATABASE_URL` по умолчанию = H2 in-memory, можно
  поднимать API и тестировать auth/CRUD без БД-сервиса.

## Дальнейшие шаги
Домен + HTTPS (Caddy), OpenAPI-спека, мобильный клиент (см. MOBILE_API_SPEC.md),
углубление контента.
