#!/usr/bin/env bash
# Запуск HookahGuide API без docker compose — на «голом» docker.
# Поднимает Meilisearch + API в одной docker-сети. Идемпотентно: можно
# запускать повторно для обновления (пересоберёт образ и перезапустит API).
#
#   chmod +x deploy-no-compose.sh
#   ./deploy-no-compose.sh
#
set -euo pipefail
cd "$(dirname "$0")"

NET=hookah-net
API_PORT="${API_PORT:-8080}"

# 1. .env с секретами (создаётся/дополняется при первом запуске)
[ -f .env ] || touch .env
grep -q '^MEILI_MASTER_KEY=' .env || echo "MEILI_MASTER_KEY=$(openssl rand -base64 32)" >> .env
grep -q '^POSTGRES_PASSWORD=' .env || echo "POSTGRES_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')" >> .env
grep -q '^JWT_SECRET=' .env || echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '/+=')" >> .env
set -a; . ./.env; set +a

# 2. Docker-сеть (чтобы контейнеры видели друг друга по имени)
docker network inspect "$NET" >/dev/null 2>&1 || docker network create "$NET"

# 3. Meilisearch (внутренний, наружу не публикуется)
if docker ps -a --format '{{.Names}}' | grep -q '^hookahguide-meili$'; then
  docker start hookahguide-meili >/dev/null
else
  docker run -d --name hookahguide-meili --network "$NET" --restart unless-stopped \
    -e MEILI_MASTER_KEY="$MEILI_MASTER_KEY" \
    -e MEILI_ENV=production -e MEILI_NO_ANALYTICS=true \
    -v meili_data:/meili_data \
    getmeili/meilisearch:v1.10
fi

# 3b. PostgreSQL (внутренний)
if docker ps -a --format '{{.Names}}' | grep -q '^hookahguide-postgres$'; then
  docker start hookahguide-postgres >/dev/null
else
  docker run -d --name hookahguide-postgres --network "$NET" --restart unless-stopped \
    -e POSTGRES_DB=hookah -e POSTGRES_USER=hookah \
    -e POSTGRES_PASSWORD="$POSTGRES_PASSWORD" \
    -v pg_data:/var/lib/postgresql/data \
    postgres:16-alpine
fi

# 4. Сборка образа API (контекст — корень репозитория)
echo ">> Сборка образа API (первый раз — несколько минут)…"
docker build -f backend/Dockerfile -t hookahguide-api .

# 5. (Пере)запуск API
docker rm -f hookahguide-api >/dev/null 2>&1 || true
docker run -d --name hookahguide-api --network "$NET" --restart unless-stopped \
  -p "${API_PORT}:8080" \
  -e PORT=8080 -e HOST=0.0.0.0 -e CONTENT_ROOT=/content \
  -e MEILI_URL=http://hookahguide-meili:7700 \
  -e MEILI_MASTER_KEY="$MEILI_MASTER_KEY" \
  -e DATABASE_URL=jdbc:postgresql://hookahguide-postgres:5432/hookah \
  -e DB_USER=hookah -e DB_PASSWORD="$POSTGRES_PASSWORD" \
  -e JWT_SECRET="$JWT_SECRET" \
  -v "$PWD/knowledge:/content/knowledge:ro" \
  -v "$PWD/data:/content/data:ro" \
  hookahguide-api

echo ">> Запущено. Контейнеры:"
docker ps --filter name=hookahguide --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo ">> Проверка /health (подождите пару секунд, если API ещё стартует):"
sleep 3
curl -fsS "http://localhost:${API_PORT}/health" && echo || \
  echo "(API ещё поднимается — повторите: curl http://localhost:${API_PORT}/health)"
