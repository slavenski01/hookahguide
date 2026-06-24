# HookahGuide API (Kotlin + Ktor)

REST API поверх базы знаний для мобильного приложения: список разделов и статей,
полная статья (Markdown), полнотекстовый поиск и справочные данные. Поиск — через
**Meilisearch**; при его недоступности автоматически работает встроенный поиск.

## Стек

- **Kotlin + Ktor 3** (движок CIO), kotlinx.serialization
- **Meilisearch** — поисковый движок (отдельный сервис)
- **Java 21**, сборка Gradle (fat jar)
- Деплой — **Docker Compose**

## Архитектура

```
knowledge/*.md  ─┐                    ┌─ GET /api/articles, /api/articles/{slug}
data/*.json     ─┤→ ContentRepository ┤
                 │                    └─ GET /api/reference/{name}
                 └→ SearchService ──────→ Meilisearch  (fallback: встроенный поиск)
                                          GET /api/search
```

Контент читается из `CONTENT_ROOT` (папки `knowledge/` и `data/`). Статьи — источник
истины в Markdown; при старте индексируются в Meilisearch.

## Эндпоинты

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/health` | Статус, число статей/разделов, активный движок поиска |
| GET | `/api/sections` | Список из 13 разделов |
| GET | `/api/articles` | Список статей (мета). Фильтры: `section`, `level`, `status`, `tag`, `limit`, `offset` |
| GET | `/api/articles/{slug}` | Полная статья с телом Markdown |
| GET | `/api/search?q=` | Поиск по статьям. Доп.: `section`, `limit`. Возвращает сниппеты |
| GET | `/api/reference/{name}` | Справочник: `brands`, `bowls`, `coals`, `mixes`, `glossary`, `tobacco-leaf`, `flavor-families`, `sections`, `articles` |

Примеры:
```bash
curl localhost:8080/health
curl "localhost:8080/api/articles?section=02-tabak&level=beginner"
curl localhost:8080/api/articles/krepost
curl "localhost:8080/api/search?q=горчит%20перегрев&limit=5"
curl localhost:8080/api/reference/mixes
```

## Конфигурация (env)

| Переменная | По умолчанию | Назначение |
|------------|--------------|-----------|
| `PORT` | `8080` | Порт API |
| `HOST` | `0.0.0.0` | Хост |
| `CONTENT_ROOT` | `..` | Корень с `knowledge/` и `data/` |
| `MEILI_URL` | — (не задан → встроенный поиск) | URL Meilisearch |
| `MEILI_MASTER_KEY` | — | Ключ Meilisearch |

## Запуск локально (без Docker)

```bash
cd backend
./gradlew buildFatJar
CONTENT_ROOT=.. java -jar build/libs/hookahguide-api-all.jar
# → встроенный поиск (Meili не задан). API на http://localhost:8080
```

## Деплой на сервер (Docker Compose + HTTPS)

На сервере нужен только Docker + Docker Compose. Поднимаются три контейнера:
**caddy** (reverse proxy, 80/443, авто-HTTPS), **api** (внутренний), **meilisearch**
(внутренний). Наружу торчит только Caddy — API напрямую не доступен.

**Предварительно:** направьте A-запись домена (`api.example.ru`) на IP сервера и
откройте порты 80 и 443.

```bash
cp .env.example .env
# задайте: DOMAIN (ваш домен), ACME_EMAIL, MEILI_MASTER_KEY (openssl rand -base64 32)
docker compose up -d --build

# Проверка (Caddy сам получит TLS-сертификат за ~10–30 сек):
curl https://api.example.ru/health   # {"status":"ok",...,"searchEngine":"meilisearch"}
docker compose logs api | grep Meili # "Meilisearch проиндексирован: 29 статей"
```

Контент монтируется read-only из репозитория — правки статей подхватываются при
перезапуске `api` (переиндексация Meili). API сам ждёт готовности Meilisearch при
старте (retry), поэтому порядок запуска контейнеров не важен.

Обновление контента после изменения статей:
```bash
git pull
docker compose restart api      # переиндексирует Meilisearch
```

> Локальная проверка без домена: поставьте `DOMAIN=localhost` — Caddy выдаст
> самоподписанный сертификат, API будет на `https://localhost`. Либо раскомментируйте
> блок `ports` у сервиса `api` в `docker-compose.yml` и обращайтесь на `http://localhost:8080`.

## Заметки

- CORS открыт (`anyHost`) для мобильного клиента — при необходимости ограничьте в
  `Application.kt`.
- Тело статьи отдаётся как Markdown; рендер — на стороне клиента.
- Поиск устойчив к сбою Meili: API не падает, переключается на встроенный движок.
