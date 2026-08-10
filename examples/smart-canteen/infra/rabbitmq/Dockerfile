ARG RABBITMQ_IMAGE=rabbitmq:4.3.4-management-alpine
FROM ${RABBITMQ_IMAGE}

COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 \
  CMD rabbitmq-diagnostics -q ping || exit 1
