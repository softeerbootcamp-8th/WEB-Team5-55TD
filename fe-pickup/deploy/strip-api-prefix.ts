/**
 * `/api` 접두사 제거 규칙 — 개발 환경과 운영 환경의 단일 출처.
 *
 * 브라우저는 두 환경 모두 `/api/auctions` 로 요청하지만, 백엔드는 `/api` 접두사
 * 없이 라우팅한다(예: `GET /healthcheck`). 그래서 중간에서 접두사를 떼야 한다.
 *
 *   로컬 : Vite 개발 서버 프록시 (vite.config.ts 가 이 함수를 그대로 쓴다)
 *   운영 : CloudFront Function (deploy/cloudfront-function.js)
 *
 * CloudFront Functions 런타임에는 모듈 시스템이 없어 이 파일을 직접 쓸 수 없다.
 * 그래서 규칙이 두 곳에 존재하게 되는데, deploy/verify-rewrite-parity.mjs 가
 * 두 구현의 동작이 같은지 CI 에서 검증한다. 한쪽만 고치면 CI 가 실패한다.
 */
export function stripApiPrefix(uri: string): string {
  // `/api` 정확히 일치. CloudFront 는 uri 가 "/" 로 시작하지 않으면 거부하므로
  // 빈 문자열이 되지 않도록 "/" 를 돌려준다.
  if (uri === "/api") {
    return "/";
  }
  // `/api/` 로 시작할 때만 제거한다.
  // `/^\/api/` 같은 단순 정규식은 `/apifoo` 를 `/foo` 로 잘못 자른다.
  if (uri.indexOf("/api/") === 0) {
    return uri.substring(4);
  }
  return uri;
}
