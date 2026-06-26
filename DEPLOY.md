# Деплой HookahGuide API на сервер

Инструкция для VPS на Ubuntu/Debian. Стек: **API (Kotlin/Ktor) + Meilisearch +
PostgreSQL**. Текущий прод — IHC.ru, IP `217.144.103.16`, режим **по IP, порт 80**
(IHC фильтрует нестандартный 8080, поэтому используем 80). Домен + HTTPS — внизу.

## Шаг 1. Установить Docker

```bash
docker --version || curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
```

## Шаг 2. Получить код (приватный репозиторий)

```bash
# вариант с токеном (read-only Contents fine-grained token на GitHub):
git clone https://<ВАШ_ТОКЕН>@github.com/slavenski01/hookahguide.git hookahguide
cd hookahguide
# либо: sudo apt install -y gh && gh auth login && gh repo clone slavenski01/hookahguide
```

## Шаг 3. Запустить

Есть два пути. **Если на сервере нет `docker compose` (плагина)** — используйте скрипт
(он сам создаёт `.env` с секретами):

```bash
chmod +x deploy-no-compose.sh
API_PORT=80 ./deploy-no-compose.sh
```

**Если `docker compose` есть** — через compose (по IP):

```bash
cp .env.example .env
# сгенерируйте и впишите секреты:
#   MEILI_MASTER_KEY   = openssl rand -base64 32
#   POSTGRES_PASSWORD  = openssl rand -base64 24
#   JWT_SECRET         = openssl rand -base64 48
API_PORT=80 docker compose -f docker-compose.ip.yml up -d --build
```

Первый запуск собирает образ (несколько минут). Поднимаются контейнеры:
`hookahguide-api` (порт 80), `hookahguide-meili`, `hookahguide-postgres` (оба внутренние).
API при старте ждёт Postgres и Meilisearch (retry), порядок запуска не важен.

## Шаг 4. Открыть порт 80

```bash
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw --force enable
```
И откройте порт **80** в панели провайдера (если там свой фаервол).

## Шаг 5. Проверить

```bash
docker ps --filter name=hookahguide --format 'table {{.Names}}\t{{.Status}}'
curl -s http://localhost/health; echo
# {"status":"ok","articles":37,"sections":13,"searchEngine":"meilisearch"}

docker logs hookahguide-api --tail 30 | grep -E 'БД|Meili'
# "БД подключена: jdbc:postgresql://…"  и  "Meilisearch проиндексирован: 37 статей"

# проверка аккаунтов:
curl -s -X POST http://localhost/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"secret123"}'; echo
```
Снаружи: `http://SERVER_IP/health`, `http://SERVER_IP/api/search?q=горчит`.

## Управление (без compose-плагина)

```bash
docker ps                              # статус
docker logs -f hookahguide-api         # логи
docker restart hookahguide-api         # переиндексировать статьи после git pull
API_PORT=80 ./deploy-no-compose.sh     # пересобрать после обновления кода
```

## Обновление

```bash
git pull
API_PORT=80 ./deploy-no-compose.sh     # код изменился → пересборка
# либо только контент: docker restart hookahguide-api
```

> Контент (статьи/справочники) монтируется read-only из репозитория и подхватывается
> при перезапуске `api`. Пользовательские данные — в Postgres (том `pg_data`), переживают
> пересборку и перезагрузку сервера.

## Секреты (.env)

`deploy-no-compose.sh` генерит их автоматически при первом запуске. Файл `.env` хранит
`MEILI_MASTER_KEY`, `POSTGRES_PASSWORD`, `JWT_SECRET` — **не теряйте его и не коммитьте**.
Смена `JWT_SECRET` разлогинит всех пользователей.

## Переход на домен + HTTPS (позже)

Когда A-запись домена будет указывать на IP:
1. В `.env`: `DOMAIN=ваш.домен`, `ACME_EMAIL=...`.
2. Откройте порты 80 и 443.
3. Переключитесь на основной compose (добавляет Caddy с авто-TLS):
   ```bash
   docker compose -f docker-compose.ip.yml down
   docker compose up -d --build
   curl https://ваш.домен/health
   ```

## Диагностика

- **`searchEngine: "builtin"`** — API не достучался до Meili. Чаще всего разные docker-сети:
  `docker network connect hookah-net hookahguide-meili && docker restart hookahguide-api`.
- **api перезапускается** — `docker logs hookahguide-api --tail 40`. Проверьте, что
  `hookahguide-postgres` поднялся (`docker logs hookahguide-postgres --tail 20`).
- **Снаружи не открывается, локально работает** — закрыт порт (фаервол сервера/провайдера);
  у IHC открыт 80, а 8080 фильтруется.
