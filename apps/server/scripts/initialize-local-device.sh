#!/usr/bin/env bash
# Local operator entrypoint for the offline two-stage enrollment runner.
# Never signs certificates, activates credentials, creates databases or sends data.
set -euo pipefail
umask 077
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $# == 1 && ( "$1" == prepare || "$1" == register ) ]] ||
    fail 'Usage: bash apps/server/scripts/initialize-local-device.sh prepare|register'
[[ -t 0 && -t 1 && ! -v CI && ! -v GITHUB_ACTIONS ]] ||
    fail 'An interactive local terminal is required; never execute in CI.'
[[ "$(id -u)" != 0 ]] || fail 'Do not run Java as root.'
command -v java >/dev/null || fail 'Java 25 is required.'
SERVER="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$SERVER/build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar"
[[ -f "$JAR" ]] || fail 'Build the reviewed main source first: cd apps/server && ./gradlew bootJar'
ACTION="$1"
read -rp 'Type PERSONAL DEVELOPMENT to confirm the existing lanveriq database is for local tests only: ' confirmation
[[ "$confirmation" == 'PERSONAL DEVELOPMENT' ]] || fail 'Cancelled.'

# Native Spring external config is the single local DB credential source.
LOCAL_CONFIG="$SERVER/config/application-local.yml"
[[ -f "$LOCAL_CONFIG" && ! -L "$LOCAL_CONFIG" && -O "$LOCAL_CONFIG" ]] ||
    fail 'Create your own config/application-local.yml from the example first.'
[[ "$(stat -c %a "$LOCAL_CONFIG")" == 600 ]] ||
    fail 'Local configuration must have mode 0600.'
# Remove duplicate DB password overrides in this child shell only.
unset LANVERIQ_DB_PASSWORD SPRING_DATASOURCE_PASSWORD SPRING_DATASOURCE_HIKARI_PASSWORD

opts=(
    --spring.main.web-application-type=none
    --spring.profiles.active=local
    "--spring.config.additional-location=file:$LOCAL_CONFIG"
    --spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/lanveriq
    --spring.datasource.username=lanveriq
    --spring.datasource.hikari.jdbc-url=jdbc:postgresql://127.0.0.1:5432/lanveriq
    --spring.datasource.hikari.username=lanveriq
    --spring.flyway.enabled=false
    --spring.sql.init.mode=never
    --spring.jpa.hibernate.ddl-auto=validate
    --lanveriq.bootstrap.enabled=false
    --lanveriq.local-enrollment.enabled=true
    --lanveriq.local-enrollment.personal-development=true
    "--lanveriq.local-enrollment.action=$ACTION"
)
if [[ "$ACTION" == register ]]; then
    read -rp 'Absolute independently verified public test client CA PEM path: ' public_ca
    [[ "$public_ca" == /* && -f "$public_ca" && -r "$public_ca" && ! -L "$public_ca" ]] ||
        fail 'Expected a readable absolute public CA PEM, never a private key.'
    opts+=(
        --lanveriq.client-cert-registration.enabled=true
        "--lanveriq.client-cert-registration.trusted-ca-file=$public_ca"
    )
else
    opts+=(--lanveriq.client-cert-registration.enabled=false)
fi

printf 'Offline local enrollment only; dedicated database, no TLS listeners or inventory.\n'
java -jar "$JAR" "${opts[@]}"
