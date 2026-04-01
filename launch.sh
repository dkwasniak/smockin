#!/bin/bash

APP_VERSION="2.21.0"

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-9092}
DB_USERNAME=${DB_USERNAME:-smockin}
DB_PASSWORD=${DB_PASSWORD:-smockin}
DB_PATH=${DB_PATH:-./smockin_db}
SERVER_PORT=8000
MULTI_USER_MODE=${MULTI_USER_MODE:-FALSE}
AUTO_START_REST=${AUTO_START_REST:-FALSE}

DB_URL="jdbc:h2:tcp://${DB_HOST}:${DB_PORT}/${DB_PATH}"

# Wait for H2 TCP port to be available
echo "Waiting for H2 database at ${DB_HOST}:${DB_PORT}..."
while ! (echo > /dev/tcp/${DB_HOST}/${DB_PORT}) 2>/dev/null; do
    sleep 1
done
echo "H2 database port is open."
sleep 2

if [ "$AUTO_START_REST" = "TRUE" ]; then
    echo "Enabling REST mock server auto-start..."
    java -cp /app/h2.jar org.h2.tools.Shell -url "$DB_URL" -user "$DB_USERNAME" -password "$DB_PASSWORD" -sql "UPDATE SERVER_CONFIG SET AUTO_START = TRUE WHERE SERVER_TYPE = 0;" < /dev/null > /dev/null 2>&1
fi

echo "Starting smockin application..."
exec java -Dspring.profiles.active=production \
    -Dserver.port=$SERVER_PORT \
    -Dspring.datasource.url=$DB_URL \
    -Dspring.datasource.username=$DB_USERNAME \
    -Dspring.datasource.password=$DB_PASSWORD \
    -Dspring.datasource.maximumPoolSize=10 \
    -Dspring.datasource.minimumIdle=10 \
    -Duser.timezone=UTC \
    -Dapp.version=$APP_VERSION \
    -Dlogging.file=/app/log/smockin.log \
    -Dmulti.user.mode=$MULTI_USER_MODE \
    -jar smockin-$APP_VERSION.jar
