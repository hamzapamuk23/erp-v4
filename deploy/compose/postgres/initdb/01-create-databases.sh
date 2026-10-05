#!/usr/bin/env bash
# Runs once, on the first start of an empty data volume (official postgres image behaviour).
# Creates the platform DB and the Keycloak DB with their own least-privilege owners (doc §7.1).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v platform_pw="$ERP_PLATFORM_DB_PASSWORD" \
  -v keycloak_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
CREATE ROLE erp_platform LOGIN PASSWORD :'platform_pw';
CREATE DATABASE erp_platform OWNER erp_platform;
REVOKE CONNECT ON DATABASE erp_platform FROM PUBLIC;

CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_pw';
CREATE DATABASE keycloak OWNER keycloak;
REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC;
SQL
