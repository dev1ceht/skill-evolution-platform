ARG MYSQL_IMAGE=mysql:8.4.11
FROM ${MYSQL_IMAGE}

COPY my.cnf /etc/mysql/conf.d/smart-canteen.cnf

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
  CMD mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent || exit 1
