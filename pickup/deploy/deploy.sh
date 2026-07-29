#!/usr/bin/env bash
#
# EC2 에서 실행되는 배포 스크립트.
# GitHub Actions 러너가 SSM Run Command 로 이 스크립트를 내려받아 실행한다.
#
#   사용법: deploy.sh s3://<bucket>/pickup/<sha>/app.jar
#
# 흐름: 백업 → 다운로드 → 교체 → 재시작 → 헬스체크 → (실패 시) 롤백
#
set -euo pipefail

# 기본값은 프로덕션 값이다. 테스트에서만 환경변수로 덮어쓴다.
APP_DIR="${APP_DIR:-/home/ubuntu/pickup}"
SERVICE="${SERVICE:-pickup}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/healthcheck}"
ATTEMPTS="${ATTEMPTS:-30}"
INTERVAL="${INTERVAL:-3}"
LOCK="${LOCK:-/var/lock/pickup-deploy.lock}"

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
[ -n "${S3_URI}" ] || die "S3 경로 인자가 없습니다. 사용법: $0 s3://bucket/key"
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

# 실패 시 직전 버전으로 되돌린다. 항상 exit 1 로 끝난다.
rollback() {
  if [ "${HAVE_PREV}" -eq 1 ] && [ -f "${PREV}" ]; then
    log "롤백: 직전 버전으로 복구"
    cp -p "${PREV}" "${JAR}"
    chown "${OWNER}" "${JAR}"
    chmod "${MODE}" "${JAR}"
    local restart_ok=1
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

  log "최근 서비스 로그:"
  journalctl -u "${SERVICE}" -n 50 --no-pager || true
  exit 1
}

# ---------------------------------------------------------------- 배포

log "대상 리비전: ${S3_URI}"

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
TMP=$(mktemp /tmp/app.jar.XXXXXX)
trap 'rm -f "${TMP}"' EXIT

"${AWS}" s3 cp "${S3_URI}" "${TMP}" --quiet || die "S3 다운로드 실패: ${S3_URI}"
[ -s "${TMP}" ] || die "다운로드한 파일이 비어 있습니다."

mv "${TMP}" "${JAR}"
chown "${OWNER}" "${JAR}"
chmod "${MODE}" "${JAR}"
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
