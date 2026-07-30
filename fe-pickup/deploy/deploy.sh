#!/usr/bin/env bash
#
# 프론트엔드 배포 — S3 업로드 + CloudFront Function 동기화 + 캐시 무효화 + 스모크 테스트
#
# 백엔드의 pickup/deploy/deploy.sh 와 같은 컨벤션(배포 로직을 셸로 분리, RESULT= 마커로
# 결과 보고)을 따르지만 실행 위치가 다르다.
#
#   백엔드 : S3 에 올린 뒤 SSM 으로 EC2 에서 원격 실행
#   프론트 : GitHub Actions 러너에서 직접 실행 (원격 서버가 없다)
#
# 사용법
#   deploy/deploy.sh <dist 디렉터리>
#
# 필수 환경변수
#   FE_S3_BUCKET                 정적 파일을 올릴 버킷 (백엔드 아티팩트와 공용)
#   FE_S3_PREFIX                 버킷 내 프리픽스 (예: fe). CloudFront Origin Path 와 같아야 한다
#   CLOUDFRONT_DISTRIBUTION_ID   무효화 대상 배포 ID
#   CLOUDFRONT_FUNCTION_NAME     /api 접두사를 제거하는 함수 이름
#   CLOUDFRONT_DOMAIN            스모크 테스트 대상 (예: d111111abcdef8.cloudfront.net)
#
set -uo pipefail

log() { echo "[fe-deploy] $*"; }

# die <RESULT 마커> <메시지>
# 워크플로가 RESULT= 마커를 파싱해 Slack 에 실패 원인을 표시한다.
die() { local marker="$1"; shift; echo "[fe-deploy] ERROR: $*" >&2; echo "RESULT=${marker}"; exit 1; }

DIST_DIR="${1:-}"
FUNCTION_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/cloudfront-function.js"

# ── 1. 입력 검증 ──────────────────────────────────────────────────────
missing=()
[ -n "${FE_S3_BUCKET:-}" ]               || missing+=("FE_S3_BUCKET")
[ -n "${FE_S3_PREFIX:-}" ]               || missing+=("FE_S3_PREFIX")
[ -n "${CLOUDFRONT_DISTRIBUTION_ID:-}" ] || missing+=("CLOUDFRONT_DISTRIBUTION_ID")
[ -n "${CLOUDFRONT_FUNCTION_NAME:-}" ]   || missing+=("CLOUDFRONT_FUNCTION_NAME")
[ -n "${CLOUDFRONT_DOMAIN:-}" ]          || missing+=("CLOUDFRONT_DOMAIN")

if [ ${#missing[@]} -gt 0 ]; then
  die precondition_failed "환경변수가 비어 있습니다: ${missing[*]}"
fi

command -v aws >/dev/null 2>&1 \
  || die precondition_failed "aws CLI 를 찾을 수 없습니다. GitHub Actions 러너에는 기본 설치되어 있습니다."

[ -n "${DIST_DIR}" ] || die precondition_failed "사용법: deploy/deploy.sh <dist 디렉터리>"
[ -d "${DIST_DIR}" ] || die precondition_failed "dist 디렉터리가 없습니다: ${DIST_DIR}"
[ -f "${DIST_DIR}/index.html" ] || die precondition_failed "index.html 이 없습니다: ${DIST_DIR}/index.html"
[ -f "${FUNCTION_SRC}" ] || die precondition_failed "CloudFront Function 소스가 없습니다: ${FUNCTION_SRC}"

# 해시 자산이 하나도 없으면 빌드가 잘못된 것이다. 빈 dist 를 올려 서비스를 지우는 사고를 막는다.
asset_count=$(find "${DIST_DIR}/assets" -type f 2>/dev/null | wc -l | tr -d ' ')
[ "${asset_count}" -gt 0 ] || die precondition_failed "dist/assets 에 파일이 없습니다. 빌드 산출물을 확인하세요."

S3_BASE="s3://${FE_S3_BUCKET}/${FE_S3_PREFIX}"
log "대상       : ${S3_BASE}/"
log "배포        : ${CLOUDFRONT_DISTRIBUTION_ID} (${CLOUDFRONT_DOMAIN})"
log "자산 파일 수 : ${asset_count}"

# ── 2. CloudFront Function 동기화 ─────────────────────────────────────
# 코드가 바뀐 경우에만 갱신한다. 매번 publish 하면 불필요한 전파 대기가 생긴다.
log "CloudFront Function 확인: ${CLOUDFRONT_FUNCTION_NAME}"
live_fn="$(mktemp)"
trap 'rm -f "${live_fn}"' EXIT

if ! aws cloudfront get-function \
      --name "${CLOUDFRONT_FUNCTION_NAME}" --stage LIVE "${live_fn}" >/dev/null 2>&1; then
  die function_failed "CloudFront 함수를 찾을 수 없습니다: ${CLOUDFRONT_FUNCTION_NAME} — 콘솔에서 먼저 생성하고 게시해야 합니다 (코드는 deploy/cloudfront-function.js)"
fi

if diff -q "${live_fn}" "${FUNCTION_SRC}" >/dev/null 2>&1; then
  log "  변경 없음 — 건너뜀"
else
  log "  코드가 다릅니다. 갱신합니다."
  etag=$(aws cloudfront describe-function \
          --name "${CLOUDFRONT_FUNCTION_NAME}" --query ETag --output text) \
    || die function_failed "describe-function 실패"

  new_etag=$(aws cloudfront update-function \
              --name "${CLOUDFRONT_FUNCTION_NAME}" \
              --if-match "${etag}" \
              --function-code "fileb://${FUNCTION_SRC}" \
              --function-config '{"Comment":"strip /api prefix for backend origin","Runtime":"cloudfront-js-2.0"}' \
              --query ETag --output text) \
    || die function_failed "update-function 실패"

  aws cloudfront publish-function \
      --name "${CLOUDFRONT_FUNCTION_NAME}" --if-match "${new_etag}" >/dev/null \
    || die function_failed "publish-function 실패"
  log "  갱신 완료"
fi

# ── 3. S3 업로드 ──────────────────────────────────────────────────────
# 순서가 중요하다.
#   ① 해시 자산 먼저 — index.html 이 아직 옛 버전이라 참조가 깨지지 않는다
#   ② index.html    — 이 시점부터 새 자산을 가리킨다
#   ③ 잔여물 삭제   — 새 index.html 이 이미 올라간 뒤라야 안전하다
#
# 자산 파일명에 콘텐츠 해시가 붙으므로 1년 immutable 로 둘 수 있다.
# index.html 만 매번 재검증시킨다.
log "S3 업로드 ① 해시 자산"
aws s3 sync "${DIST_DIR}/" "${S3_BASE}/" \
    --exclude "index.html" \
    --cache-control "public,max-age=31536000,immutable" \
    --only-show-errors \
  || die s3_failed "자산 업로드 실패"

log "S3 업로드 ② index.html"
aws s3 cp "${DIST_DIR}/index.html" "${S3_BASE}/index.html" \
    --cache-control "no-cache" \
    --content-type "text/html; charset=utf-8" \
    --only-show-errors \
  || die s3_failed "index.html 업로드 실패"

# 이미 올린 파일은 S3 쪽 타임스탬프가 더 최신이라 재업로드되지 않는다. 삭제만 일어난다.
# 주의: 직전 배포의 자산이 즉시 사라지므로, 그 사이 열려 있던 탭이 지연 로딩
#       청크를 가져오려 하면 404 가 날 수 있다. 새로고침하면 해소된다.
log "S3 업로드 ③ 이전 배포 잔여물 정리"
aws s3 sync "${DIST_DIR}/" "${S3_BASE}/" \
    --delete --exclude "index.html" \
    --cache-control "public,max-age=31536000,immutable" \
    --only-show-errors \
  || die s3_failed "잔여물 정리 실패"

# ── 4. 캐시 무효화 ────────────────────────────────────────────────────
# index.html 만 무효화해도 충분하지만(자산은 해시가 바뀜), 경로 1개당 과금이
# 같으므로 "/*" 로 단순하게 간다. 월 1000경로까지 무료다.
log "CloudFront 무효화 요청"
invalidation_id=$(aws cloudfront create-invalidation \
                    --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" \
                    --paths "/*" \
                    --query Invalidation.Id --output text) \
  || die invalidation_failed "create-invalidation 실패"

log "  ${invalidation_id} — 완료 대기"
aws cloudfront wait invalidation-completed \
    --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" \
    --id "${invalidation_id}" \
  || die invalidation_failed "무효화 완료 대기 실패 (id=${invalidation_id})"
log "  무효화 완료"

# ── 5. 스모크 테스트 ──────────────────────────────────────────────────
# 배포는 성공했는데 사이트는 깨져 있는 상황을 막는다.
# 특히 두 번째 검사가 CloudFront Function 이 /api 를 제대로 떼는지 확인한다.
BASE_URL="https://${CLOUDFRONT_DOMAIN}"

smoke() {
  local name="$1" url="$2" pattern="$3"
  local body
  for attempt in 1 2 3; do
    body=$(curl -fsS --max-time 10 "${url}" 2>/dev/null)
    if printf '%s' "${body}" | grep -q "${pattern}"; then
      log "  ✓ ${name}"
      return 0
    fi
    [ "${attempt}" -lt 3 ] && sleep 5
  done
  echo "[fe-deploy] ✗ ${name} — ${url}" >&2
  echo "[fe-deploy]   기대 패턴: ${pattern}" >&2
  echo "[fe-deploy]   실제 응답: $(printf '%s' "${body:-(응답 없음)}" | head -c 300)" >&2
  return 1
}

log "스모크 테스트"
smoke_failed=0
smoke "정적 파일 서빙"        "${BASE_URL}/"                  'id="root"'      || smoke_failed=1
smoke "/api/* 백엔드 연결"    "${BASE_URL}/api/healthcheck"   '"status":"OK"'  || smoke_failed=1
smoke "SPA fallback"          "${BASE_URL}/auctions/smoke"    'id="root"'      || smoke_failed=1

if [ "${smoke_failed}" -ne 0 ]; then
  die smoke_failed "배포는 끝났지만 사이트가 정상 동작하지 않습니다. 위 실패 항목을 확인하세요."
fi

log "배포 완료: ${BASE_URL}"
echo "RESULT=success"
