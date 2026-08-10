ARG REDIS_IMAGE=redis:7.4.10-alpine3.21
FROM ${REDIS_IMAGE}

COPY redis.conf /usr/local/etc/redis/redis.conf
COPY docker-entrypoint.sh /usr/local/bin/smart-canteen-redis-entrypoint
RUN chmod 0755 /usr/local/bin/smart-canteen-redis-entrypoint

USER redis
ENTRYPOINT ["smart-canteen-redis-entrypoint"]

HEALTHCHECK --interval=10s --timeout=3s --retries=10 \
  CMD REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping | grep PONG || exit 1
