#!/bin/sh
set -eu

if [ -z "${REDIS_PASSWORD:-}" ]; then
  echo "REDIS_PASSWORD must not be empty" >&2
  exit 1
fi

exec docker-entrypoint.sh redis-server /usr/local/etc/redis/redis.conf \
  --requirepass "$REDIS_PASSWORD"
