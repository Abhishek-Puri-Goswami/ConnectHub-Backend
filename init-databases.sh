#!/bin/bash
# Creates all service databases and the application user.
# Runs automatically when the MySQL container starts for the first time.
# MYSQL_ROOT_PASSWORD, MYSQL_USER, and MYSQL_PASSWORD are injected by docker-compose.
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE DATABASE IF NOT EXISTS connecthub_auth;
CREATE DATABASE IF NOT EXISTS connecthub_room;
CREATE DATABASE IF NOT EXISTS connecthub_message;
CREATE DATABASE IF NOT EXISTS connecthub_media;
CREATE DATABASE IF NOT EXISTS connecthub_notification;
CREATE DATABASE IF NOT EXISTS connecthub_payment;

CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';

GRANT ALL PRIVILEGES ON connecthub_auth.*         TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON connecthub_room.*         TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON connecthub_message.*      TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON connecthub_media.*        TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON connecthub_notification.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON connecthub_payment.*      TO '${MYSQL_USER}'@'%';

FLUSH PRIVILEGES;
EOSQL
