#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

usage() {
  cat <<'HELP'
Uso: ./run.sh [--build] [--no-db]
  --build  Recompila el JAR (omite las pruebas de integración).
  --no-db  Usa PostgreSQL existente según DB_URL, DB_USER y DB_PASSWORD.
  --help   Muestra esta ayuda.
Por defecto inicia PostgreSQL con Docker Compose y usa el perfil dev.
Lee .env, si existe, como configuración Bash local. Ctrl+C detiene Java;
PostgreSQL y Mailpit permanecen activos. Para detenerlos: docker compose stop db mailpit.
HELP
}
build=false
start_db=true
for option in "$@"; do
  case "$option" in
    --build) build=true ;;
    --no-db) start_db=false ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Opción desconocida: $option" >&2; usage; exit 2 ;;
  esac
done
if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi
fail() { echo "Error: $*" >&2; exit 1; }
command -v java >/dev/null || fail 'Instala Java 21 o posterior.'
java_version="$(java -version 2>&1)"
if [[ "$java_version" =~ version\ \"([0-9]+) ]]; then
  (( BASH_REMATCH[1] >= 21 )) || fail 'Se necesita Java 21 o posterior.'
else
  fail 'No pude identificar la versión de Java.'
fi
[[ -n "${DB_PASSWORD:-}" ]] || fail 'Falta DB_PASSWORD. Copia .env.example a .env y configura las claves.'
DEV_PASSWORD="${DEV_PASSWORD:-}"
[[ ${#DEV_PASSWORD} -ge 12 ]] || fail 'DEV_PASSWORD debe tener al menos 12 caracteres (perfil dev).'
export DB_PASSWORD DEV_PASSWORD
export DB_PORT="${DB_PORT:-5433}"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] || fail "DB_PORT debe ser numérico."
(( 10#$DB_PORT >= 1 && 10#$DB_PORT <= 65535 )) || fail "DB_PORT fuera de rango."
export DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:${DB_PORT}/cazaobjetos}"
export DB_USER="${DB_USER:-cazaobjetos}"
export SPRING_PROFILES_ACTIVE=dev
export SERVER_PORT="${SERVER_PORT:-8080}"
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"

jar=target/cazaobjetos-0.1.0-SNAPSHOT.jar
if [[ ! -f "$jar" ]]; then build=true; fi
if [[ -f "$jar" ]] && { [[ pom.xml -nt "$jar" ]] || [[ -n "$(find src/main -type f -newer "$jar" -print -quit)" ]]; }; then
  build=true
fi
if $build; then
  command -v mvn >/dev/null || fail 'Se necesita Maven para compilar el proyecto.'
  maven_args=(-DskipTests package)
  if [[ -n "${MAVEN_REPO_LOCAL:-}" ]]; then maven_args+=("-Dmaven.repo.local=$MAVEN_REPO_LOCAL"); fi
  echo 'Compilando el backend (sin ejecutar pruebas de integración)...'
  mvn "${maven_args[@]}"
fi
[[ -f "$jar" ]] || fail "No se generó $jar."
if $start_db; then
  [[ ( "$DB_URL" == "jdbc:postgresql://127.0.0.1:${DB_PORT}/cazaobjetos" || "$DB_URL" == "jdbc:postgresql://localhost:${DB_PORT}/cazaobjetos" ) && "$DB_USER" == 'cazaobjetos' ]] || fail 'Para otra base o usuario usa --no-db.'
  command -v docker >/dev/null || fail 'Docker no está instalado. Usa --no-db si ya tienes PostgreSQL.'
  docker compose version >/dev/null 2>&1 || fail 'Falta Docker Compose.'
  docker info >/dev/null 2>&1 || fail 'No hay acceso a Docker. Revisa que esté iniciado y que tu usuario tenga permisos; no es necesario ejecutar Java con sudo.'
  echo 'Iniciando PostgreSQL; esperando hasta 60 segundos...'
  docker compose up -d --wait --wait-timeout 60 db mailpit || fail 'No arrancaron los servicios. Revisa: docker compose logs db mailpit.'
  export LOCAL_MAIL_ENABLED=true
  export MAIL_SMTP_PORT="${MAIL_SMTP_PORT:-1025}"
  echo "Buzón de pruebas: http://localhost:${MAIL_UI_PORT:-8025} (los mensajes no salen a Internet)."
fi
printf 'Iniciando API local en http://%s:%s (perfil dev). Ctrl+C para detener.\n' "$SERVER_ADDRESS" "$SERVER_PORT"
echo 'No hay interfaz en /. Sigue postman/README.md para probar los endpoints.'
exec java -jar "$jar"
