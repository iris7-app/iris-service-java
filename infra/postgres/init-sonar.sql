-- Creates the SonarQube database and user on the shared PostgreSQL instance.
-- SonarQube manages its own schema; this script only provisions the database.
-- Executed automatically by postgres on first container start (docker-entrypoint-initdb.d/).
-- Note: the superuser is `demo` (configured via DB_USER env var), not `postgres`.
-- This script runs only on a fresh volume; if the volume already exists, run manually:
--   docker exec postgres-demo psql -U demo -d customer-service \
--     -c "CREATE USER sonar WITH PASSWORD 'sonar';"
--   docker exec postgres-demo psql -U demo -d customer-service \
--     -c "CREATE DATABASE sonar OWNER sonar ENCODING 'UTF8';"

CREATE USER sonar WITH PASSWORD 'sonar';
CREATE DATABASE sonar OWNER sonar ENCODING 'UTF8';
GRANT ALL PRIVILEGES ON DATABASE sonar TO sonar;
