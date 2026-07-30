// CloudFront Function (뷰어 요청) — `/api` 접두사를 제거해 백엔드 오리진으로 넘긴다.
//
// CloudFront 배포 구성
//   Default (*)  -> S3 오리진 (Origin Path /fe)      정적 파일
//   /api/*       -> EC2 백엔드 오리진                 이 함수가 연결된 동작
//
// 동작 매칭은 원본 URI 기준으로 먼저 끝나고 그 다음 이 함수가 실행된다.
// 따라서 여기서 `/api` 를 떼도 기본 동작(S3)으로 다시 튕기지 않는다.
//
// ⚠️ 이 파일은 deploy/strip-api-prefix.ts 와 동작이 같아야 한다.
//    CloudFront Functions 런타임(cloudfront-js-2.0)에는 import/export 가 없어
//    공유 모듈을 쓸 수 없다. 대신 deploy/verify-rewrite-parity.mjs 가 CI 에서
//    두 구현을 같은 입력으로 돌려 결과가 일치하는지 검증한다.
//
// 배포: deploy/deploy.sh 가 코드가 바뀐 경우에만 update-function + publish-function 한다.

function handler(event) {
  var request = event.request;
  var uri = request.uri;

  // `/api` 정확히 일치. uri 는 반드시 "/" 로 시작해야 하므로 빈 문자열을 피한다.
  if (uri === "/api") {
    request.uri = "/";
    return request;
  }

  // `/api/` 로 시작할 때만 제거한다.
  // 단순히 앞 4글자를 검사하면 `/apifoo` 가 `/foo` 로 잘못 잘린다.
  if (uri.indexOf("/api/") === 0) {
    request.uri = uri.substring(4);
  }

  return request;
}
