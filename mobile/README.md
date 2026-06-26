# HookahGuide — мобильное приложение (KMP + Compose Multiplatform)

Клиент к API HookahGuide. Живёт в этом монорепозитории, но **на сервер не деплоится**
(серверная Docker-сборка не включает `mobile/`, см. корневой `.dockerignore`).

## Стек

- **Kotlin Multiplatform (KMP)** + **Compose Multiplatform** — общий UI и логика.
- **Таргеты:** Android + iOS.
- Преимущество: весь продукт на Kotlin (бэкенд — Ktor), можно переиспользовать
  `kotlinx.serialization`-модели и **Ktor Client**.

## Контекст для разработки

- **Контракт API:** [`../docs/MOBILE_API_SPEC.md`](../docs/MOBILE_API_SPEC.md) — эндпоинты,
  модели, примеры ответов, экраны.
- **Базовый URL (прод):** `http://217.144.103.16/`
- **Как устроен бэкенд:** скилл `.claude/skills/hookahguide-backend/`.

## Рекомендуемые библиотеки

| Назначение | Библиотека |
|------------|-----------|
| HTTP-клиент | **Ktor Client** (engines: `okhttp` для Android, `darwin` для iOS) |
| JSON | **kotlinx.serialization** (те же DTO, что в бэкенде) |
| Асинхронность | kotlinx.coroutines |
| Навигация | **Voyager** или **Decompose** |
| DI | **Koin** |
| Рендер Markdown (тело статьи) | **multiplatform-markdown-renderer** (`com.mikepenz`) |
| (опц.) Картинки | Coil 3 (multiplatform) — если появятся изображения |

## DTO: можно переиспользовать с бэкенда

Модели ответов API совпадают с `backend/.../Models.kt` (Section, ArticleMeta, Article,
SearchResponse/SearchHit). Варианты:
- **Просто скопировать** нужные `@Serializable data class` в `commonMain` приложения
  (быстро, без связности с бэкендом). ← рекомендуется для старта.
- Позже — вынести в общий Gradle-модуль `:shared-api-models`, подключаемый и бэкендом,
  и приложением (если захочется единый источник).

## Структура (ориентир, создаёт сессия разработки приложения)

```
mobile/
├── composeApp/
│   └── src/
│       ├── commonMain/    # UI (Compose), API-клиент, модели, VM — общий код
│       ├── androidMain/   # Android entry point, Ktor okhttp engine
│       └── iosMain/        # iOS entry point, Ktor darwin engine
├── iosApp/                 # Xcode-обёртка для iOS
└── settings.gradle.kts / build.gradle.kts
```
Проще всего сгенерировать каркас через **Kotlin Multiplatform Wizard**
(kmp.jetbrains.com) или шаблон Android Studio «Kotlin Multiplatform App», затем
положить результат в `mobile/`.

## Конфиг сети (HTTP без TLS, пока нет домена)

- **Android:** `android:usesCleartextTraffic="true"` (или network-security-config с
  исключением для `217.144.103.16`).
- **iOS:** ATS-исключение в `Info.plist` для `217.144.103.16`.
- BaseUrl держать в одном месте (`expect/actual` или просто константа в commonMain),
  чтобы потом легко переключить на `https://домен`.

## Первый майлстоун (предложение)

1. Каркас KMP+CMP в `mobile/`, запуск на обоих таргетах («Hello»).
2. Ktor-клиент + DTO + репозиторий: `GET /api/sections`, `/api/articles`.
3. Экраны: разделы → список статей → статья (рендер Markdown `body`).
4. Поиск (`/api/search`).
5. Каталоги из `/api/reference/*` (бренды, миксы).

## Требования к окружению
- Android Studio + плагин Kotlin Multiplatform; JDK 17+.
- Для iOS-сборки нужен **macOS + Xcode** (у тебя Mac — ок).
