/**
 * CloudFront Function 두 개가 기대대로 동작하는지 검증한다.
 *
 * 이 함수들은 로컬에서 실행할 방법이 없다(공식 에뮬레이터 없음). 잘못 고쳐도
 * 배포하기 전에는 드러나지 않으므로, 실제 배포되는 파일을 그대로 불러와 CI 에서 돌린다.
 *
 *   ① strip-api-prefix — Vite 프록시(deploy/strip-api-prefix.ts)와 동작이 같아야 한다.
 *      브라우저는 개발·운영 모두 /api/... 로 요청하지만 백엔드는 /api 없이 라우팅한다.
 *      로컬은 Vite 가, 운영은 CloudFront 가 접두사를 뗀다. CloudFront Functions 런타임에
 *      모듈 시스템이 없어 규칙이 두 곳에 존재하므로, 한쪽만 고치면 여기서 잡힌다.
 *
 *   ② spa-fallback — 로컬 대응물이 없다(Vite 개발 서버가 자체 처리). 대신 기대 동작을
 *      케이스로 고정해 오타·논리 실수를 막는다.
 *
 * 실행: node deploy/verify-edge-functions.mjs   (의존성 없음. CI 에서 실행)
 *
 * 참고: .ts 를 직접 import 한다. Node 22.18+ 의 타입 스트리핑에 의존하며,
 *       CI 는 Node 24 로 고정되어 있다 (.github/workflows/frontend-ci.yml).
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));

/** ① strip-api-prefix — Vite 구현과 CloudFront 구현이 모두 만족해야 하는 표. */
const STRIP_API_CASES = [
  // 실제 호출 경로
  ["/api/auctions", "/auctions"],
  ["/api/auth", "/auth"],
  ["/api/auth/refresh", "/auth/refresh"],
  ["/api/members/me", "/members/me"],
  ["/api/members/me/points", "/members/me/points"],
  ["/api/healthcheck", "/healthcheck"],

  // 경계 — 빈 문자열이 되면 CloudFront 가 요청을 거부한다
  ["/api", "/"],
  ["/api/", "/"],

  // 접두사 오인식 — `/^\/api/` 로 자르면 "/foo" 가 되어버린다
  ["/apifoo", "/apifoo"],
  ["/apiary/bees", "/apiary/bees"],

  // 접두사가 없는 경로는 그대로
  ["/", "/"],
  ["/login", "/login"],
  ["/assets/index-a1b2c3d4.js", "/assets/index-a1b2c3d4.js"],
  ["/v1/api/things", "/v1/api/things"],
];

/** ② spa-fallback — 확장자 없는 경로만 index.html 로 돌린다. */
const SPA_FALLBACK_CASES = [
  // SPA 라우트 → index.html
  ["/", "/index.html"],
  ["/login", "/index.html"],
  ["/auctions/123", "/index.html"],
  ["/seller/products", "/index.html"],
  ["/seller/products/42", "/index.html"],

  // 실제 파일 → 그대로. 없는 자산까지 index.html 로 돌리면
  // 브라우저가 JS 대신 HTML 을 받아 "Unexpected token '<'" 이 난다.
  ["/index.html", "/index.html"],
  ["/favicon.ico", "/favicon.ico"],
  ["/assets/index-a1b2c3d4.js", "/assets/index-a1b2c3d4.js"],
  ["/assets/index-a1b2c3d4.css", "/assets/index-a1b2c3d4.css"],
  ["/assets/logo.svg", "/assets/logo.svg"],
];

/**
 * CloudFront Function 파일을 실제 배포되는 형태 그대로 불러온다.
 * export 가 없는 파일이라 import 할 수 없으므로 함수 본문으로 감싸 평가한다.
 * (파일에 export 를 붙이면 CloudFront 가 업로드를 거부한다.)
 */
function loadCloudFrontHandler(filename) {
  const source = readFileSync(join(HERE, filename), "utf8");
  return new Function(`${source}\nreturn handler;`)();
}

/** CloudFront 는 querystring 을 uri 와 분리해 전달하므로 uri 만 넘긴다. */
function runHandler(handler, uri) {
  return handler({ request: { uri } }).uri;
}

function printTable(headers, rows) {
  const widths = headers.map(([, width]) => width);
  console.error(
    "  " + headers.map(([label], i) => label.padEnd(widths[i])).join(""),
  );
  for (const row of rows) {
    console.error("  " + row.map((cell, i) => cell.padEnd(widths[i])).join(""));
  }
}

async function main() {
  const stripApiEdge = loadCloudFrontHandler("cloudfront-strip-api-prefix.js");
  const spaFallbackEdge = loadCloudFrontHandler("cloudfront-spa-fallback.js");
  const { stripApiPrefix } = await import("./strip-api-prefix.ts");

  let failed = false;

  // ① Vite ↔ CloudFront 일치
  const stripFailures = [];
  for (const [input, expected] of STRIP_API_CASES) {
    const local = stripApiPrefix(input);
    const edge = runHandler(stripApiEdge, input);
    if (local !== expected || edge !== expected) {
      const mark = (actual) => (actual === expected ? " " : "✗");
      stripFailures.push([
        input,
        expected,
        `${mark(local)}${local}`,
        `${mark(edge)}${edge}`,
      ]);
    }
  }
  if (stripFailures.length > 0) {
    failed = true;
    console.error("\n`/api` 접두사 제거 규칙이 일치하지 않습니다.\n");
    printTable(
      [
        ["입력", 28],
        ["기대", 24],
        ["vite", 24],
        ["cloudfront", 24],
      ],
      stripFailures,
    );
    console.error(
      "\n  deploy/strip-api-prefix.ts 와 deploy/cloudfront-strip-api-prefix.js 를 함께 고치세요.",
    );
  }

  // ② SPA fallback 기대 동작
  const spaFailures = [];
  for (const [input, expected] of SPA_FALLBACK_CASES) {
    const edge = runHandler(spaFallbackEdge, input);
    if (edge !== expected) {
      spaFailures.push([input, expected, `✗${edge}`]);
    }
  }
  if (spaFailures.length > 0) {
    failed = true;
    console.error("\nSPA fallback 동작이 기대와 다릅니다.\n");
    printTable(
      [
        ["입력", 32],
        ["기대", 24],
        ["실제", 24],
      ],
      spaFailures,
    );
    console.error("\n  deploy/cloudfront-spa-fallback.js 를 확인하세요.");
  }

  if (failed) {
    process.exit(1);
  }

  console.log(
    `edge 함수 검증 통과 — strip-api ${STRIP_API_CASES.length}건, spa-fallback ${SPA_FALLBACK_CASES.length}건`,
  );
}

await main();
