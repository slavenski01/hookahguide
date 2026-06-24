# Деплой HookahGuide API на сервер

Инструкция для VPS на Ubuntu/Debian. Текущий режим — **по IP, HTTP** (без домена).
Когда появится домен — см. раздел «Переход на домен + HTTPS» внизу.

## Шаг 1. Установить Docker (если ещё не установлен)

Проверка:
```bash
docker --version && docker compose version
```
Если команды не найдены — установить (официальный скрипт Docker):
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER     # чтобы запускать docker без sudo
# выйдите и зайдите снова (или: newgrp docker)
```

## Шаг 2. Получить код

```bash
git clone <URL_РЕПОЗИТОРИЯ> hookahguide
cd hookahguide
```
(URL появится после публикации на GitHub — см. отдельную инструкцию по пушу.)

## Шаг 3. Настроить переменные окружения

```bash
cp .env.example .env
nano .env
```
Для IP-режима достаточно задать **только** мастер-ключ Meilisearch:
```
MEILI_MASTER_KEY=<вставьте_случайный_ключ>
```
Сгенерировать ключ:
```bash
openssl rand -base64 32
```
(Поля `DOMAIN` и `ACME_EMAIL` в IP-режиме не используются — оставьте как есть.)

## Шаг 4. Запустить

```bash
docker compose -f docker-compose.ip.yml up -d --build
```
Первый запуск соберёт образ (несколько минут). Поднимутся два контейнера:
`hookahguide-api` (порт 8080) и `hookahguide-meili` (внутренний).

## Шаг 5. Открыть порт в фаерволе

Если используется ufw:
```bash
sudo ufw allow 22/tcp      # не потеряйте SSH!
sudo ufw allow 8080/tcp
sudo ufw enable
```
У облачных провайдеров (Timeweb, Selectel, Hetzner, AWS…) также откройте порт **8080**
в панели управления (Security Group / Firewall).

## Шаг 6. Проверить

На сервере:
```bash
curl http://localhost:8080/health
# {"status":"ok","articles":29,"sections":13,"searchEngine":"meilisearch"}

docker compose -f docker-compose.ip.yml logs api | grep Meili
# "Meilisearch проиндексирован: 29 статей"
```
Снаружи (с любого устройства), подставьте IP сервера:
```bash
curl http://SERVER_IP:8080/health
curl "http://SERVER_IP:8080/api/search?q=горчит"
```

## Управление

```bash
docker compose -f docker-compose.ip.yml ps        # статус
docker compose -f docker-compose.ip.yml logs -f   # логи
docker compose -f docker-compose.ip.yml down       # остановить
```

## Обновление контента / кода

```bash
git pull
docker compose -f docker-compose.ip.yml up -d --build   # пересобрать при изменении кода
# или только переиндексировать статьи без пересборки:
docker compose -f docker-compose.ip.yml restart api
```

## Переход на домен + HTTPS (позже)

Когда направите домен (A-запись) на IP сервера:
1. В `.env` задайте `DOMAIN=ваш.домен` и `ACME_EMAIL=...`.
2. Откройте порты 80 и 443 (и закройте 8080, если не нужен напрямую).
3. Переключитесь на основной compose (он добавляет Caddy с авто-HTTPS):
   ```bash
   docker compose -f docker-compose.ip.yml down
   docker compose up -d --build
   curl https://ваш.домен/health
   ```

## Диагностика

- **Контейнер api перезапускается** — `docker compose -f docker-compose.ip.yml logs api`.
  Частая причина: не задан `MEILI_MASTER_KEY` в `.env`.
- **`searchEngine: "builtin"` вместо `meilisearch`** — API не достучался до Meili.
  Проверьте `docker compose -f docker-compose.ip.yml logs meilisearch`. API сам
  переиндексирует при `restart api`.
- **Снаружи не открывается, локально работает** — закрыт порт 8080 (фаервол сервера
  или провайдера).
