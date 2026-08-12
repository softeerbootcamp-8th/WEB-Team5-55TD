#!/usr/bin/env bash
#
# EC2 에서 실행되는 배포 스크립트. GitHub Actions 러너가 SSM Run Command 로 내려받아 실행한다.
#
#   사용법: deploy.sh s3://<bucket>/pickup/<sha>
#   흐름:   다운로드(jar·runtime.env·JMX 설정) → 백업 → 교체 → 재시작 → 헬스체크 → 실패 시 롤백
#
# 고칠 때 알아야 할 것
#
# - 상단 변수는 프로덕션 기본값이다. 테스트에서만 환경변수로 덮어쓴다.
# - 애플리케이션 환경변수는 runtime.env 하나로만 들어온다. 값의 형식은 워크플로가 이미
#   검증했으므로 여기서는 구조만 본다. 값은 시크릿을 포함하므로 로그에 출력하지 않는다.
# - SYSTEMD_DROPIN_FILE 은 사전순 마지막이어야 한다. systemd 는 드롭인을 파일명 순으로
#   병합하고 나중에 읽은 값이 이긴다. 숫자 접두사는 ASCII 에서 소문자보다 앞이라 쓸 수 없다.
# - MANAGED_PATHS 에 적힌 파일만 백업·복구 대상이다. 만들거나 지우는 파일을 추가할 때
#   여기 함께 넣지 않으면 롤백이 반쪽이 된다.
# - /etc/pickup/pickup.env 는 지우지 않는다. 유닛 본문이 참조하고 있어 참조가 남은 채
#   지우면 서비스가 기동하지 못한다. 유닛 수정과 함께 수동으로 정리한다.
# - result() 와 rollback() 이 찍는 RESULT= / FAILED_APP_LOG= 마커는 워크플로가 파싱해
#   Slack 실패 사유로 쓴다. 값을 바꾸면 backend-cd.yml 의 wait 스텝도 함께 고친다.
#   RESULT 값: success | rolled_back | rollback_unverified | rollback_failed |
#              no_rollback_target | precondition_failed
# - rollback() 은 재시작 전에 journalctl 을 먼저 캡처한다. 순서를 바꾸면 실패 원인이 사라진다.
#
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/pickup}"
SERVICE="${SERVICE:-pickup}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/healthcheck}"
ATTEMPTS="${ATTEMPTS:-30}"
INTERVAL="${INTERVAL:-3}"
LOCK="${LOCK:-/var/lock/pickup-deploy.lock}"
ENV_FILE="${ENV_FILE:-${APP_DIR}/.env}"
SYSTEMD_DROPIN_DIR="${SYSTEMD_DROPIN_DIR:-/etc/systemd/system/${SERVICE}.service.d}"
SYSTEMD_DROPIN_FILE="${SYSTEMD_DROPIN_FILE:-${SYSTEMD_DROPIN_DIR}/zz-pickup-env.conf}"
DATADOG_JMX_DIR="${DATADOG_JMX_DIR:-/opt/datadog/jmx}"
LEGACY_ENV_FILE="${LEGACY_ENV_FILE:-/etc/pickup/image-storage.env}"

JMX_CONFIG_NAMES=(
  hikaricp-jmx.yaml
  tomcat-jmx.yaml
  websocket-jmx.yaml
)
JMX_CONFIG_TARGETS=(
  "${DATADOG_JMX_DIR}/hikaricp.yaml"
  "${DATADOG_JMX_DIR}/tomcat.yaml"
  "${DATADOG_JMX_DIR}/websocket.yaml"
)

OBSOLETE_PATHS=(
  "${SYSTEMD_DROPIN_DIR}/20-image-storage.conf"
  "${SYSTEMD_DROPIN_DIR}/datadog.conf"
  "${SYSTEMD_DROPIN_DIR}/loadtest-slack-off.conf"
  "${LEGACY_ENV_FILE}"
)

MANAGED_PATHS=(
  "${ENV_FILE}"
  "${SYSTEMD_DROPIN_FILE}"
  "${JMX_CONFIG_TARGETS[@]}"
  "${OBSOLETE_PATHS[@]}"
)

JAR="${APP_DIR}/app.jar"
PREV="${APP_DIR}/app.jar.prev"

export PATH="${PATH}:/snap/bin:/usr/local/bin"

log() { echo "[deploy] $*"; }

result() { echo "[deploy] RESULT=$1"; }

die() {
  result precondition_failed
  echo "[deploy] ERROR: $*" >&2
  exit 1
}

# ---------------------------------------------------------------- 사전 점검

ARTIFACT_BASE_URI="${1:-}"
[ -n "${ARTIFACT_BASE_URI}" ] \
  || die "S3 경로 인자가 없습니다. 사용법: $0 s3://<bucket>/pickup/<sha>"
case "${ARTIFACT_BASE_URI}" in
  s3://*) ;;
  *) die "s3:// 로 시작하는 경로가 필요합니다: ${ARTIFACT_BASE_URI}" ;;
esac
ARTIFACT_BASE_URI="${ARTIFACT_BASE_URI%/}"
JAR_URI="${ARTIFACT_BASE_URI}/app.jar"
ENV_URI="${ARTIFACT_BASE_URI}/runtime.env"

if command -v aws >/dev/null 2>&1; then
  AWS=aws
elif [ -x /snap/bin/aws ]; then
  AWS=/snap/bin/aws
else
  die "aws CLI 를 찾을 수 없습니다. PATH=${PATH}"
fi

systemctl list-unit-files "${SERVICE}.service" --no-legend | grep -q "${SERVICE}.service" \
  || die "${SERVICE}.service 유닛이 없습니다."

exec 9>"${LOCK}"
flock -n 9 || die "다른 배포가 진행 중입니다."

mkdir -p "${APP_DIR}"

# ---------------------------------------------------------------- 함수

validate_runtime_env() {
  [ -s "${ENV_TMP}" ] || return 1
  if grep -qvE '^[A-Z][A-Z0-9_]*=' "${ENV_TMP}"; then
    return 1
  fi
}

validate_jmx_configs() {
  grep -q 'alias: hikaricp.connections.active' "${JMX_CONFIG_TMPS[0]}" || return 1
  grep -q 'alias: tomcat.threads.busy' "${JMX_CONFIG_TMPS[1]}" || return 1
  grep -q 'bean: com.ootd.pickup.websocket:name=RealtimeWebSocketMetrics' \
    "${JMX_CONFIG_TMPS[2]}" || return 1
}

verify_jmx_config_reference() {
  local configured name
  configured=$(grep -m1 '^DD_JMXFETCH_CONFIG=' "${ENV_FILE}" | cut -d= -f2-) || return 1
  [ -n "${configured}" ] || return 1
  for name in ${configured//,/ }; do
    [ -f "${DATADOG_JMX_DIR}/${name}" ] || return 1
  done
}

backup_managed_files() {
  local path
  for path in "${MANAGED_PATHS[@]}"; do
    if [ -f "${path}" ]; then
      cp -p "${path}" "${BACKUP_DIR}/${path//\//_}" || return 1
    fi
  done
  MANAGED_BACKED_UP=1
}

install_runtime_config() {
  install -d -o root -g root -m 0755 "${SYSTEMD_DROPIN_DIR}" "${DATADOG_JMX_DIR}" || return 1

  install -m 0600 "${ENV_TMP}" "${ENV_FILE}.new" || return 1
  chown "${OWNER}" "${ENV_FILE}.new" || return 1
  mv "${ENV_FILE}.new" "${ENV_FILE}" || return 1

  printf '[Service]\nEnvironmentFile=%s\n' "${ENV_FILE}" > "${SYSTEMD_DROPIN_TMP}" || return 1
  install -o root -g root -m 0644 "${SYSTEMD_DROPIN_TMP}" "${SYSTEMD_DROPIN_FILE}.new" || return 1
  mv "${SYSTEMD_DROPIN_FILE}.new" "${SYSTEMD_DROPIN_FILE}" || return 1

  local index path
  for index in "${!JMX_CONFIG_TARGETS[@]}"; do
    install -o root -g root -m 0644 "${JMX_CONFIG_TMPS[${index}]}" \
      "${JMX_CONFIG_TARGETS[${index}]}.new" || return 1
    mv "${JMX_CONFIG_TARGETS[${index}]}.new" "${JMX_CONFIG_TARGETS[${index}]}" || return 1
  done

  for path in "${OBSOLETE_PATHS[@]}"; do
    if [ -f "${path}" ]; then
      rm -f "${path}" || return 1
      log "예전 설정 제거: ${path}"
    fi
  done

  systemctl daemon-reload || return 1
}

restore_managed_files() {
  [ "${MANAGED_BACKED_UP}" -eq 1 ] || return 0

  local path backup
  for path in "${MANAGED_PATHS[@]}"; do
    backup="${BACKUP_DIR}/${path//\//_}"
    if [ -f "${backup}" ]; then
      cp -p "${backup}" "${path}" || return 1
    else
      rm -f "${path}" || return 1
    fi
  done
  systemctl daemon-reload || return 1
}

warn_unmanaged_dropins() {
  local path name
  for path in "${SYSTEMD_DROPIN_DIR}"/*.conf; do
    [ -f "${path}" ] || continue
    name=$(basename "${path}")
    [ "${path}" = "${SYSTEMD_DROPIN_FILE}" ] && continue
    log "경고: 관리 대상이 아닌 드롭인이 있습니다 - ${name}"
  done
  return 0
}

wait_healthy() {
  local i
  for i in $(seq 1 "${ATTEMPTS}"); do
    if curl -fsS --max-time 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"OK"'; then
      return 0
    fi
    sleep "${INTERVAL}"
  done
  return 1
}

extract_failure_reason() {
  local r=""
  r=$(printf '%s\n' "${FAILED_LOG}" | grep 'Caused by:' | tail -1)
  [ -z "${r}" ] && r=$(printf '%s\n' "${FAILED_LOG}" \
    | grep -m1 -E 'APPLICATION FAILED TO START|Application run failed|Validate failed')
  [ -z "${r}" ] && r=$(printf '%s\n' "${FAILED_LOG}" | grep -m1 -E 'ERROR|Exception')
  [ -z "${r}" ] && r="원인 로그를 찾지 못했습니다 (EC2 에서 journalctl 확인 필요)"
  printf '%s' "${r}" | tr '\n\r\t' '   ' | tr -d '"'"'"'`$\\' | cut -c1-300
}

rollback() {
  FAILED_LOG=$(journalctl -u "${SERVICE}" -n 300 --no-pager 2>/dev/null || echo "")
  log "FAILED_APP_LOG=$(extract_failure_reason)"

  local config_restore_ok=1
  if ! restore_managed_files; then
    log "이전 환경변수·Datadog 설정 복구 실패"
    config_restore_ok=0
  fi

  echo "───── 실패한 버전의 로그 (에러 관련) ─────"
  printf '%s\n' "${FAILED_LOG}" \
    | grep -iE 'error|exception|caused by|failed|flyway|migrat' | tail -25 || true
  echo "───── 실패한 버전의 로그 (마지막 12줄) ─────"
  printf '%s\n' "${FAILED_LOG}" | tail -12 || true
  echo "──────────────────────────────────────────"

  if [ "${HAVE_PREV}" -eq 1 ] && [ -f "${PREV}" ]; then
    log "롤백: 직전 버전으로 복구"
    cp -p "${PREV}" "${JAR}"
    chown "${OWNER}" "${JAR}"
    chmod "${MODE}" "${JAR}"
    local restart_ok=1
    [ "${config_restore_ok}" -eq 1 ] || restart_ok=0
    systemctl restart "${SERVICE}" || restart_ok=0

    if wait_healthy; then
      if [ "${restart_ok}" -eq 1 ]; then
        log "롤백 완료 — 직전 버전으로 정상 동작 중"
        result rolled_back
      else
        log "롤백 재시작은 실패했으나 헬스체크는 통과 — 실행 중인 버전을 보장할 수 없습니다"
        result rollback_unverified
      fi
    else
      log "롤백했으나 헬스체크 실패 — 수동 확인이 필요합니다"
      result rollback_failed
    fi
  else
    log "롤백 대상 없음 (${PREV} 없음) — 서비스가 중단 상태일 수 있습니다"
    result no_rollback_target
  fi

  echo "───── 롤백 후 서비스 상태 ─────"
  systemctl is-active "${SERVICE}" || true
  journalctl -u "${SERVICE}" -n 10 --no-pager || true
  exit 1
}

# ---------------------------------------------------------------- 배포

JAR_TMP=$(mktemp /tmp/app.jar.XXXXXX)
ENV_TMP=$(mktemp /tmp/pickup-runtime-env.XXXXXX)
SYSTEMD_DROPIN_TMP=$(mktemp /tmp/pickup-systemd-dropin.XXXXXX)
BACKUP_DIR=$(mktemp -d /tmp/pickup-config-backup.XXXXXX)
JMX_CONFIG_TMPS=()
for index in "${!JMX_CONFIG_NAMES[@]}"; do
  JMX_CONFIG_TMPS+=("$(mktemp /tmp/pickup-jmx-config.XXXXXX)")
done

cleanup() {
  rm -f "${JAR_TMP}" "${ENV_TMP}" "${SYSTEMD_DROPIN_TMP}" "${JMX_CONFIG_TMPS[@]}"
  rm -rf "${BACKUP_DIR}"
}
trap cleanup EXIT

MANAGED_BACKED_UP=0

log "대상 리비전: ${ARTIFACT_BASE_URI}"

"${AWS}" s3 cp "${ENV_URI}" "${ENV_TMP}" --quiet || die "환경변수 파일 다운로드 실패: ${ENV_URI}"
validate_runtime_env || die "환경변수 파일이 KEY=값 형식이 아닙니다."
log "환경변수 $(wc -l < "${ENV_TMP}" | tr -d ' ')개 검증 완료"

for index in "${!JMX_CONFIG_NAMES[@]}"; do
  "${AWS}" s3 cp "${ARTIFACT_BASE_URI}/${JMX_CONFIG_NAMES[${index}]}" \
    "${JMX_CONFIG_TMPS[${index}]}" --quiet \
    || die "Datadog JMX 설정 다운로드 실패: ${JMX_CONFIG_NAMES[${index}]}"
done
validate_jmx_configs || die "Datadog JMX 설정의 형식이 올바르지 않습니다."
log "Datadog JMX 설정 검증 완료"

if [ -f "${JAR}" ]; then
  OWNER=$(stat -c '%U:%G' "${JAR}")
  MODE=$(stat -c '%a' "${JAR}")
  cp -p "${JAR}" "${PREV}"
  HAVE_PREV=1
  log "직전 버전 백업 완료 (소유권 ${OWNER}, 권한 ${MODE})"
else
  OWNER="ubuntu:ubuntu"
  MODE=644
  HAVE_PREV=0
  log "기존 ${JAR} 없음 — 첫 배포로 진행 (롤백 불가)"
fi

"${AWS}" s3 cp "${JAR_URI}" "${JAR_TMP}" --quiet || die "S3 다운로드 실패: ${JAR_URI}"
[ -s "${JAR_TMP}" ] || die "다운로드한 파일이 비어 있습니다."

backup_managed_files || die "기존 환경변수·Datadog 설정을 백업하지 못했습니다."
if ! install_runtime_config; then
  restore_managed_files || true
  die "환경변수·Datadog 설정을 설치하지 못했습니다."
fi
verify_jmx_config_reference \
  || log "경고: DD_JMXFETCH_CONFIG 가 가리키는 파일이 ${DATADOG_JMX_DIR} 에 없습니다"
warn_unmanaged_dropins
log "환경변수·Datadog 설정 적용 완료 (${ENV_FILE})"

if ! mv "${JAR_TMP}" "${JAR}" \
  || ! chown "${OWNER}" "${JAR}" \
  || ! chmod "${MODE}" "${JAR}"; then
  log "jar 교체 또는 권한 설정 실패"
  rollback
fi
log "jar 교체 완료 ($(stat -c '%s' "${JAR}") bytes)"

systemctl restart "${SERVICE}" || {
  log "서비스 재시작 실패"
  rollback
}

if wait_healthy; then
  log "배포 성공"
  result success
  exit 0
fi

log "헬스체크 실패 (${INTERVAL}초 간격 ${ATTEMPTS}회 = $((ATTEMPTS * INTERVAL))초 초과)"
rollback
