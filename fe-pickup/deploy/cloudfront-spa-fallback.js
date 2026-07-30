// CloudFront Function (뷰어 요청) — SPA 라우트를 index.html 로 돌린다.
//
// CloudFront 배포 구성
//   Default (*)  -> S3 오리진 (Origin Path /fe)   이 함수가 연결된 동작
//   /api/*       -> EC2 백엔드 오리진              (다른 함수)
//
// 왜 필요한가
//   TanStack Router 는 클라이언트 사이드 라우팅이라 빌드 산출물에 HTML 이 하나뿐이다.
//   앱 안에서 이동할 때는 서버 요청이 없지만, 주소창 직접 입력·새로고침·공유 링크는
//   실제 GET 요청이 된다. S3 에는 fe/auctions/123 같은 객체가 없어 403 이 난다.
//   (버킷 정책이 s3:GetObject 만 허용하므로 404 가 아니라 403 이다.)
//
// 왜 "사용자 지정 오류 응답"을 쓰지 않는가
//   403/404 -> index.html(200) 매핑은 배포 전체에 적용되어 /api/* 응답까지 바꾼다.
//   백엔드는 MEMBER_NOT_FOUND 등 404 와 CONSIGNMENT_ACCESS_DENIED(403)를 실제로 쓰는데,
//   그게 index.html + 200 으로 바뀌면 axios 가 성공으로 판단해 에러 처리가 조용히 죽는다.
//   함수는 동작별로 붙고 "요청" 단계에서만 실행되므로 응답을 건드릴 수 없다.
//
// 배포: deploy/deploy.sh 가 코드가 바뀐 경우에만 update-function + publish-function 한다.

function handler(event) {
  var request = event.request;
  var uri = request.uri;

  // 마지막 경로 조각에 점이 있으면 실제 파일 요청이다 (index-a1b2.js, favicon.ico).
  // 없는 자산까지 index.html 로 돌리면 브라우저가 JS 대신 HTML 을 받아
  // "Unexpected token '<'" 같은 엉뚱한 에러를 낸다. 404/403 이 그대로 나가야 한다.
  var lastSegment = uri.substring(uri.lastIndexOf("/") + 1);
  if (lastSegment.indexOf(".") !== -1) {
    return request;
  }

  // 확장자가 없으면 SPA 라우트다 (/auctions/123, /login).
  // 알려진 한계: 경로에 점이 들어간 라우트(/users/john.doe)는 파일로 오인한다.
  request.uri = "/index.html";

  return request;
}
