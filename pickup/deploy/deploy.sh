#!/usr/bin/env bash
#
# EC2 에서 실행되는 배포 스크립트.
# GitHub Actions 러너가 SSM Run Command 로 이 스크립트를 내려받아 실행한다.
#
#   사용법: deploy.sh s3://<bucket>/pickup/<sha>/app.jar <runtime-config-base64>
#
# 흐름: JAR·Datadog 설정 다운로드 → 기존 파일 백업 → 교체 → 재시작 → 헬스체크 → (실패 시) 롤백
#
set -euo pipefail

# 기본값은 프로덕션 값이다. 테스트에서만 환경변수로 덮어쓴다.
APP_DIR="${APP_DIR:-/home/ubuntu/pickup}"
SERVICE="${SERVICE:-pickup}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/healthcheck}"
ATTEMPTS="${ATTEMPTS:-30}"
INTERVAL="${INTERVAL:-3}"
LOCK="${LOCK:-/var/lock/pickup-deploy.lock}"
RUNTIME_ENV_DIR="${RUNTIME_ENV_DIR:-/etc/pickup}"
RUNTIME_ENV_FILE="${RUNTIME_ENV_FILE:-${RUNTIME_ENV_DIR}/image-storage.env}"
SYSTEMD_DROPIN_DIR="${SYSTEMD_DROPIN_DIR:-/etc/systemd/system/${SERVICE}.service.d}"
SYSTEMD_DROPIN_FILE="${SYSTEMD_DROPIN_FILE:-${SYSTEMD_DROPIN_DIR}/20-image-storage.conf}"
DATADOG_JMX_DIR="${DATADOG_JMX_DIR:-/opt/datadog/jmx}"
DATADOG_DROPIN_FILE="${DATADOG_DROPIN_FILE:-${SYSTEMD_DROPIN_DIR}/datadog.conf}"

DATADOG_CONFIG_NAMES=(
  hikaricp-jmx.yaml
  tomcat-jmx.yaml
  websocket-jmx.yaml
  pickup-datadog.conf
)
DATADOG_CONFIG_TARGETS=(
  "${DATADOG_JMX_DIR}/hikaricp.yaml"
  "${DATADOG_JMX_DIR}/tomcat.yaml"
  "${DATADOG_JMX_DIR}/websocket.yaml"
  "${DATADOG_DROPIN_FILE}"
)

JAR="${APP_DIR}/app.jar"
PREV="${APP_DIR}/app.jar.prev"

# SSM Run Command 는 최소 환경으로 실행되어 /snap/bin 이 PATH 에 없다.
# snap 으로 설치한 aws CLI 를 찾기 위해 보강한다.
export PATH="${PATH}:/snap/bin:/usr/local/bin"

log() { echo "[deploy] $*"; }

# 워크플로가 파싱하는 기계 판독용 결과 마커.
# 한국어 로그 문구를 grep 하면 문구를 다듬을 때 조용히 깨지므로 이 값으로만 판정한다.
#   success | rolled_back | rollback_unverified | rollback_failed
#   no_rollback_target | precondition_failed
result() { echo "[deploy] RESULT=$1"; }

die() {
  result precondition_failed
  echo "[deploy] ERROR: $*" >&2
  exit 1
}

# ---------------------------------------------------------------- 사전 점검

S3_URI="${1:-}"
RUNTIME_CONFIG_BASE64="${2:-}"
[ -n "${S3_URI}" ] \
  || die "S3 경로 인자가 없습니다. 사용법: $0 s3://bucket/key <runtime-config-base64>"
[ -n "${RUNTIME_CONFIG_BASE64}" ] || die "런타임 설정이 없습니다."
case "${S3_URI}" in
  s3://*) ;;
  *) die "s3:// 로 시작하는 경로가 필요합니다: ${S3_URI}" ;;
esac

if command -v aws >/dev/null 2>&1; then
  AWS=aws
elif [ -x /snap/bin/aws ]; then
  AWS=/snap/bin/aws
else
  die "aws CLI 를 찾을 수 없습니다. PATH=${PATH}"
fi

systemctl list-unit-files "${SERVICE}.service" --no-legend | grep -q "${SERVICE}.service" \
  || die "${SERVICE}.service 유닛이 없습니다."

# 배포가 겹치면 교체 중에 또 교체되어 서버 상태를 알 수 없게 된다.
exec 9>"${LOCK}"
flock -n 9 || die "다른 배포가 진행 중입니다."

mkdir -p "${APP_DIR}"

# ---------------------------------------------------------------- 함수

validate_runtime_config() {
  local line_count
  line_count=$(wc -l < "${IMAGE_ENV_TMP}" | tr -d ' ')
  [ "${line_count}" -eq 8 ] || return 1

  grep -qxE 'IMAGE_STORAGE_BUCKET=[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]' "${IMAGE_ENV_TMP}" \
    || return 1
  grep -qx 'IMAGE_STORAGE_REGION=ap-northeast-2' "${IMAGE_ENV_TMP}" || return 1
  grep -qxE 'IMAGE_MEDIA_BASE_URL=https://[A-Za-z0-9.-]+' "${IMAGE_ENV_TMP}" || return 1
  grep -qxE 'IMAGE_UPLOAD_URL_TTL=[1-9][0-9]*[smhd]' "${IMAGE_ENV_TMP}" || return 1
  grep -qxE 'WEBSOCKET_ALLOWED_ORIGINS=https://[A-Za-z0-9.-]+' "${IMAGE_ENV_TMP}" || return 1
  grep -qx 'DD_SERVICE=pickup-api' "${IMAGE_ENV_TMP}" || return 1
  grep -qx 'DD_ENV=production' "${IMAGE_ENV_TMP}" || return 1
  grep -qxE 'DD_VERSION=[0-9a-f]{40}' "${IMAGE_ENV_TMP}" || return 1
}

validate_datadog_configs() {
  grep -q 'alias: hikaricp.connections.active' "${DATADOG_CONFIG_TMPS[0]}" || return 1
  grep -q 'alias: tomcat.threads.busy' "${DATADOG_CONFIG_TMPS[1]}" || return 1
  grep -q 'bean: com.ootd.pickup.websocket:name=RealtimeWebSocketMetrics' \
    "${DATADOG_CONFIG_TMPS[2]}" || return 1
  grep -qx 'Environment="DD_JMXFETCH_CONFIG=hikaricp.yaml,tomcat.yaml,websocket.yaml"' \
    "${DATADOG_CONFIG_TMPS[3]}" || return 1
}

backup_runtime_config() {
  if [ -f "${RUNTIME_ENV_FILE}" ]; then
    cp -p "${RUNTIME_ENV_FILE}" "${RUNTIME_ENV_BACKUP}" || return 1
    HAVE_RUNTIME_ENV=1
  fi
  if [ -f "${SYSTEMD_DROPIN_FILE}" ]; then
    cp -p "${SYSTEMD_DROPIN_FILE}" "${SYSTEMD_DROPIN_BACKUP}" || return 1
    HAVE_SYSTEMD_DROPIN=1
  fi
  local index
  for index in "${!DATADOG_CONFIG_TARGETS[@]}"; do
    if [ -f "${DATADOG_CONFIG_TARGETS[${index}]}" ]; then
      cp -p "${DATADOG_CONFIG_TARGETS[${index}]}" \
        "${DATADOG_CONFIG_BACKUPS[${index}]}" || return 1
      DATADOG_CONFIG_EXISTED[${index}]=1
    fi
  done
  RUNTIME_CONFIG_BACKED_UP=1
}

install_runtime_config() {
  install -d -o root -g root -m 0755 \
    "${RUNTIME_ENV_DIR}" "${SYSTEMD_DROPIN_DIR}" "${DATADOG_JMX_DIR}" || return 1
  install -o root -g root -m 0644 "${IMAGE_ENV_TMP}" "${RUNTIME_ENV_FILE}.new" || return 1
  mv "${RUNTIME_ENV_FILE}.new" "${RUNTIME_ENV_FILE}" || return 1

  printf '[Service]\nEnvironmentFile=%s\n' "${RUNTIME_ENV_FILE}" > "${SYSTEMD_DROPIN_TMP}" \
    || return 1
  install -o root -g root -m 0644 "${SYSTEMD_DROPIN_TMP}" \
    "${SYSTEMD_DROPIN_FILE}.new" || return 1
  mv "${SYSTEMD_DROPIN_FILE}.new" "${SYSTEMD_DROPIN_FILE}" || return 1

  local index
  for index in "${!DATADOG_CONFIG_TARGETS[@]}"; do
    install -o root -g root -m 0644 "${DATADOG_CONFIG_TMPS[${index}]}" \
      "${DATADOG_CONFIG_TARGETS[${index}]}.new" || return 1
    mv "${DATADOG_CONFIG_TARGETS[${index}]}.new" \
      "${DATADOG_CONFIG_TARGETS[${index}]}" || return 1
  done
  systemctl daemon-reload || return 1
}

restore_runtime_config() {
  [ "${RUNTIME_CONFIG_BACKED_UP}" -eq 1 ] || return 0

  if [ "${HAVE_RUNTIME_ENV}" -eq 1 ]; then
    install -o root -g root -m 0644 "${RUNTIME_ENV_BACKUP}" "${RUNTIME_ENV_FILE}" || return 1
  else
    rm -f "${RUNTIME_ENV_FILE}" || return 1
  fi

  if [ "${HAVE_SYSTEMD_DROPIN}" -eq 1 ]; then
    install -o root -g root -m 0644 "${SYSTEMD_DROPIN_BACKUP}" \
      "${SYSTEMD_DROPIN_FILE}" || return 1
  else
    rm -f "${SYSTEMD_DROPIN_FILE}" || return 1
  fi

  local index
  for index in "${!DATADOG_CONFIG_TARGETS[@]}"; do
    if [ "${DATADOG_CONFIG_EXISTED[${index}]}" -eq 1 ]; then
      install -o root -g root -m 0644 "${DATADOG_CONFIG_BACKUPS[${index}]}" \
        "${DATADOG_CONFIG_TARGETS[${index}]}" || return 1
    else
      rm -f "${DATADOG_CONFIG_TARGETS[${index}]}" || return 1
    fi
  done
  systemctl daemon-reload || return 1
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

# 실패한 버전의 로그에서 근본 원인 한 줄을 뽑는다.
# 스택트레이스의 마지막 "Caused by" 가 보통 진짜 원인이다.
extract_failure_reason() {
  local r=""
  r=$(printf '%s\n' "${FAILED_LOG}" | grep 'Caused by:' | tail -1)
  [ -z "${r}" ] && r=$(printf '%s\n' "${FAILED_LOG}" \
    | grep -m1 -E 'APPLICATION FAILED TO START|Application run failed|Validate failed')
  [ -z "${r}" ] && r=$(printf '%s\n' "${FAILED_LOG}" | grep -m1 -E 'ERROR|Exception')
  [ -z "${r}" ] && r="원인 로그를 찾지 못했습니다 (EC2 에서 journalctl 확인 필요)"
  # 워크플로·Slack 을 거치므로 개행과 인용부호를 제거하고 길이를 제한한다.
  printf '%s' "${r}" | tr '\n\r\t' '   ' | tr -d '"'"'"'`$\\' | cut -c1-300
}

# 실패 시 직전 버전으로 되돌린다. 항상 exit 1 로 끝난다.
rollback() {
  # 롤백 재시작을 먼저 하면 실패한 버전의 로그가 새 로그에 밀려나 원인을 알 수 없게 된다.
  # 그래서 무엇보다 먼저 캡처한다.
  FAILED_LOG=$(journalctl -u "${SERVICE}" -n 300 --no-pager 2>/dev/null || echo "")

  # 워크플로가 파싱해 Slack 실패 사유로 쓴다.
  log "FAILED_APP_LOG=$(extract_failure_reason)"

  local config_restore_ok=1
  if ! restore_runtime_config; then
    log "이전 런타임·Datadog 설정 복구 실패"
    config_restore_ok=0
  fi

  echo "───── 실패한 버전의 로그 (에러 관련) ─────"
  printf '%s\n' "${FAILED_LOG}" \
    | grep -iE 'error|exception|caused by|failed|flyway|migrat' | tail -25 || true
  # SSM 이 수집하는 출력은 24,000자에서 잘리므로 맥락용으로만 짧게 남긴다.
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
        # restart 는 실패했는데 응답은 온다. 교체 전 프로세스가 남아 있을 수 있으므로
        # 지금 도는 것이 어느 버전인지 보장할 수 없다.
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

  # 실패한 버전의 로그는 이미 위에서 찍었다. 여기서는 롤백 후 상태만 짧게 남긴다.
  echo "───── 롤백 후 서비스 상태 ─────"
  systemctl is-active "${SERVICE}" || true
  journalctl -u "${SERVICE}" -n 10 --no-pager || true
  exit 1
}

# ---------------------------------------------------------------- 배포

JAR_TMP=$(mktemp /tmp/app.jar.XXXXXX)
IMAGE_ENV_TMP=$(mktemp /tmp/pickup-image-env.XXXXXX)
SYSTEMD_DROPIN_TMP=$(mktemp /tmp/pickup-systemd-dropin.XXXXXX)
RUNTIME_ENV_BACKUP=$(mktemp /tmp/pickup-image-env-backup.XXXXXX)
SYSTEMD_DROPIN_BACKUP=$(mktemp /tmp/pickup-systemd-dropin-backup.XXXXXX)
DATADOG_CONFIG_TMPS=()
DATADOG_CONFIG_BACKUPS=()
DATADOG_CONFIG_EXISTED=()
for index in "${!DATADOG_CONFIG_NAMES[@]}"; do
  DATADOG_CONFIG_TMPS+=("$(mktemp /tmp/pickup-datadog-config.XXXXXX)")
  DATADOG_CONFIG_BACKUPS+=("$(mktemp /tmp/pickup-datadog-backup.XXXXXX)")
  DATADOG_CONFIG_EXISTED+=(0)
done

cleanup() {
  rm -f "${JAR_TMP}" "${IMAGE_ENV_TMP}" "${SYSTEMD_DROPIN_TMP}" \
    "${RUNTIME_ENV_BACKUP}" "${SYSTEMD_DROPIN_BACKUP}" \
    "${DATADOG_CONFIG_TMPS[@]}" "${DATADOG_CONFIG_BACKUPS[@]}"
}
trap cleanup EXIT

HAVE_RUNTIME_ENV=0
HAVE_SYSTEMD_DROPIN=0
RUNTIME_CONFIG_BACKED_UP=0

printf '%s' "${RUNTIME_CONFIG_BASE64}" | base64 --decode > "${IMAGE_ENV_TMP}" \
  || die "런타임 설정을 디코딩하지 못했습니다."
validate_runtime_config || die "런타임 설정의 형식이 올바르지 않습니다."
log "런타임 설정 검증 완료"

log "대상 리비전: ${S3_URI}"

ARTIFACT_BASE_URI="${S3_URI%/app.jar}"
[ "${ARTIFACT_BASE_URI}" != "${S3_URI}" ] \
  || die "배포 JAR 경로는 app.jar 로 끝나야 합니다: ${S3_URI}"
for index in "${!DATADOG_CONFIG_NAMES[@]}"; do
  "${AWS}" s3 cp "${ARTIFACT_BASE_URI}/${DATADOG_CONFIG_NAMES[${index}]}" \
    "${DATADOG_CONFIG_TMPS[${index}]}" --quiet \
    || die "Datadog 설정 다운로드 실패: ${DATADOG_CONFIG_NAMES[${index}]}"
done
validate_datadog_configs || die "Datadog 설정의 형식이 올바르지 않습니다."
log "Datadog 설정 검증 완료"

# 교체 전 소유권·권한을 기록해 그대로 복원한다.
# systemd 유닛의 User= 를 스크립트에 하드코딩하지 않기 위한 것이다.
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

# 대상 경로에 직접 받으면 다운로드 실패 시 깨진 파일이 남는다.
"${AWS}" s3 cp "${S3_URI}" "${JAR_TMP}" --quiet || die "S3 다운로드 실패: ${S3_URI}"
[ -s "${JAR_TMP}" ] || die "다운로드한 파일이 비어 있습니다."

backup_runtime_config || die "기존 런타임·Datadog 설정을 백업하지 못했습니다."
if ! install_runtime_config; then
  restore_runtime_config || true
  die "런타임·Datadog 설정을 설치하지 못했습니다."
fi
log "런타임·Datadog 설정 적용 완료"

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
