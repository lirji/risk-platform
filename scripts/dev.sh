#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNTIME_DIR="${REPO_ROOT}/.run/dev"
PID_DIR="${RUNTIME_DIR}/pids"
LOG_DIR="${RUNTIME_DIR}/logs"
ENV_FILE="${REPO_ROOT}/.env"
SERVICES="fraud-gateway risk-admin risk-console"
MONITORING=false

usage() {
  cat <<'EOF'
Usage: ./scripts/dev.sh [start|stop|status|logs]

  start   Start core Docker dependencies, build, and run the two APIs and console.
  stop    Stop only the application processes recorded by this script.
  status  Show the recorded application process state and core container state.
  logs    Follow application logs under .run/dev/logs.
EOF
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

pid_file() {
  printf '%s/%s.pid\n' "${PID_DIR}" "$1"
}

log_file() {
  printf '%s/%s.log\n' "${LOG_DIR}" "$1"
}

read_pid() {
  local file
  file="$(pid_file "$1")"
  [[ -f "${file}" ]] || return 1
  tr -d '[:space:]' < "${file}"
}

is_running() {
  local pid
  pid="$(read_pid "$1")" || return 1
  [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null
}

remove_stale_pid() {
  local name="$1"
  if [[ -f "$(pid_file "${name}")" ]] && ! is_running "${name}"; then
    rm -f "$(pid_file "${name}")"
  fi
}

stop_one() {
  local name="$1"
  local pid
  pid="$(read_pid "${name}")" || return 0

  if kill -0 "${pid}" 2>/dev/null; then
    printf 'Stopping %s (pid %s)\n' "${name}" "${pid}"
    kill -TERM "${pid}" 2>/dev/null || true
    local attempt=0
    while kill -0 "${pid}" 2>/dev/null && (( attempt < 20 )); do
      sleep 1
      attempt=$((attempt + 1))
    done
    if kill -0 "${pid}" 2>/dev/null; then
      printf 'Force stopping %s (pid %s)\n' "${name}" "${pid}" >&2
      kill -KILL "${pid}" 2>/dev/null || true
    fi
  fi
  rm -f "$(pid_file "${name}")"
}

stop_apps() {
  local name
  for name in ${SERVICES}; do
    stop_one "${name}"
  done
}

on_exit() {
  local code=$?
  if [[ "${MONITORING}" == true ]]; then
    trap - EXIT INT TERM
    stop_apps
  fi
  exit "${code}"
}

load_env() {
  [[ -f "${ENV_FILE}" ]] || die "${ENV_FILE} is missing; copy .env.example to .env and replace every placeholder"
  set -a
  # shellcheck disable=SC1090
  . "${ENV_FILE}"
  set +a

  # Spring's dev profile intentionally keeps H2 for persistence, but Redis and
  # Kafka still use the Compose services. Map their host-port overrides unless
  # the caller supplied application-specific connection settings.
  REDIS_HOST="${REDIS_HOST:-localhost}"
  REDIS_PORT="${REDIS_PORT:-${REDIS_HOST_PORT:-6379}}"
  KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:${KAFKA_HOST_PORT:-9092}}"
  export REDIS_HOST REDIS_PORT KAFKA_BOOTSTRAP
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${REPO_ROOT}/docker-compose.yml" "$@"
}

find_jar() {
  local module="$1"
  local jar=''
  local candidate
  for candidate in "${REPO_ROOT}/${module}"/target/*.jar; do
    [[ -f "${candidate}" ]] || continue
    [[ "${candidate}" == *.original ]] && continue
    if [[ -n "${jar}" ]]; then
      die "multiple runnable JARs found for ${module}; clean the module and retry"
    fi
    jar="${candidate}"
  done
  [[ -n "${jar}" ]] || die "no runnable JAR found for ${module}"
  printf '%s\n' "${jar}"
}

start_process() {
  local name="$1"
  local workdir="$2"
  shift 2

  remove_stale_pid "${name}"
  is_running "${name}" && die "${name} is already running; use status or stop first"

  mkdir -p "${PID_DIR}" "${LOG_DIR}"
  (
    cd "${workdir}"
    exec "$@"
  ) >"$(log_file "${name}")" 2>&1 &
  local pid=$!
  printf '%s\n' "${pid}" > "$(pid_file "${name}")"
  printf 'Started %s (pid %s, log %s)\n' "${name}" "${pid}" "$(log_file "${name}")"
}

start_apps() {
  require_command docker
  require_command java
  require_command node
  require_command npm
  [[ -x "${REPO_ROOT}/mvnw" ]] || die "Maven Wrapper is missing or not executable: ${REPO_ROOT}/mvnw"
  load_env

  local name
  for name in ${SERVICES}; do
    remove_stale_pid "${name}"
    is_running "${name}" && die "${name} is already running; use status or stop first"
  done

  printf 'Starting core infrastructure...\n'
  compose up -d mysql redis kafka elasticsearch kibana

  printf 'Building backend applications with the Maven Wrapper...\n'
  (
    cd "${REPO_ROOT}"
    ./mvnw -B -DskipTests -pl fraud-gateway,risk-admin -am package
  )

  printf 'Installing the locked frontend dependencies...\n'
  (
    cd "${REPO_ROOT}/risk-console"
    npm ci
  )

  local gateway_jar
  local admin_jar
  gateway_jar="$(find_jar fraud-gateway)"
  admin_jar="$(find_jar risk-admin)"

  start_process fraud-gateway "${REPO_ROOT}" java -jar "${gateway_jar}"
  start_process risk-admin "${REPO_ROOT}" java -jar "${admin_jar}"
  start_process risk-console "${REPO_ROOT}/risk-console" npm run dev -- --host 0.0.0.0

  MONITORING=true
  trap on_exit EXIT INT TERM

  printf '\nDevelopment stack is running:\n'
  printf '  Console:        http://localhost:5173\n'
  printf '  Fraud gateway:  http://localhost:8082\n'
  printf '  Management API: http://localhost:8083\n'
  printf 'Press Ctrl+C to stop application processes; infrastructure containers remain running.\n\n'

  while :; do
    for name in ${SERVICES}; do
      if ! is_running "${name}"; then
        printf '%s exited; stopping the remaining application processes. See %s\n' \
          "${name}" "$(log_file "${name}")" >&2
        return 1
      fi
    done
    sleep 2
  done
}

show_status() {
  mkdir -p "${PID_DIR}" "${LOG_DIR}"
  local name
  local pid
  local any_stopped=false
  for name in ${SERVICES}; do
    remove_stale_pid "${name}"
    if is_running "${name}"; then
      pid="$(read_pid "${name}")"
      printf '%-18s running (pid %s)\n' "${name}" "${pid}"
    else
      printf '%-18s stopped\n' "${name}"
      any_stopped=true
    fi
  done

  if command -v docker >/dev/null 2>&1 && [[ -f "${ENV_FILE}" ]]; then
    printf '\nCore containers:\n'
    compose ps mysql redis kafka elasticsearch kibana
  fi

  [[ "${any_stopped}" == false ]]
}

follow_logs() {
  mkdir -p "${LOG_DIR}"
  local found=false
  local name
  for name in ${SERVICES}; do
    if [[ -f "$(log_file "${name}")" ]]; then
      found=true
    fi
  done
  [[ "${found}" == true ]] || die "no application logs found under ${LOG_DIR}"
  tail -n 100 -F "${LOG_DIR}"/*.log
}

ACTION="${1:-start}"
case "${ACTION}" in
  start)
    start_apps
    ;;
  stop)
    stop_apps
    ;;
  status)
    show_status
    ;;
  logs)
    follow_logs
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
