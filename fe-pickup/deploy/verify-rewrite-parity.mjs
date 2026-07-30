/**
 * `/api` 접두사 제거 규칙이 개발·운영에서 동일하게 동작하는지 검증한다.
 *
 *   로컬 : deploy/strip-api-prefix.ts   (vite.config.ts 의 프록시 rewrite)
 *   운영 : deploy/cloudfront-function.js (CloudFront Function)
 *
 * CloudFront Functions 런타임에는 모듈 시스템이 없어 두 구현이 따로 존재한다.
 * 한쪽만 고치면 "로컬은 되는데 배포하면 404" 가 되고, CloudFront Function 은
 * 로컬에서 실행할 방법이 없어 배포 전에는 드러나지 않는다. 그래서 여기서 막는다.
 *
 * 실행: node deploy/verify-rewrite-parity.mjs   (의존성 없음. CI 에서 실행)
 *
 * 참고: .ts 를 직접 import 한다. Node 22.18+ 의 타입 스트리핑에 의존하며,
 *       CI 는 Node 24 로 고정되어 있다 (.github/workflows/frontend-ci.yml).
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));

/** 두 구현이 같은 결과를 내야 하는 입력들. */
const CASES = [
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

  // 접두사가 없는 경로는 그대로 (기본 동작으로 가지만 방어적으로 확인)
  ["/", "/"],
  ["/login", "/login"],
  ["/assets/index-a1b2c3d4.js", "/assets/index-a1b2c3d4.js"],

  // 접두사가 중간에 있는 경우
  ["/v1/api/things", "/v1/api/things"],
];

/**
 * CloudFront Function 파일을 실제 배포되는 형태 그대로 불러온다.
 * export 가 없는 파일이라 import 할 수 없으므로 함수 본문으로 감싸 평가한다.
 * (파일을 고쳐서 export 를 붙이면 CloudFront 가 업로드를 거부한다.)
 */
function loadCloudFrontHandler() {
  const source = readFileSync(join(HERE, "cloudfront-function.js"), "utf8");
  return new Function(`${source}\nreturn handler;`)();
}

async function main() {
  const handler = loadCloudFrontHandler();
  const { stripApiPrefix } = await import("./strip-api-prefix.ts");

  const failures = [];
  for (const [input, expected] of CASES) {
    const local = stripApiPrefix(input);
    // CloudFront 는 querystring 을 uri 와 분리해 전달하므로 uri 만 넘긴다.
    const edge = handler({ request: { uri: input } }).uri;

    if (local !== expected || edge !== expected) {
      failures.push({ input, expected, local, edge });
    }
  }

  if (failures.length > 0) {
    console.error("`/api` 접두사 제거 규칙이 일치하지 않습니다.\n");
    console.error(
      "  입력".padEnd(28) + "기대".padEnd(24) + "vite".padEnd(24) + "cloudfront",
    );
    for (const f of failures) {
      const mark = (actual) => (actual === f.expected ? " " : "✗");
      console.error(
        `  ${f.input}`.padEnd(28) +
          `${f.expected}`.padEnd(24) +
          `${mark(f.local)}${f.local}`.padEnd(24) +
          `${mark(f.edge)}${f.edge}`,
      );
    }
    console.error(
      "\n  deploy/strip-api-prefix.ts 와 deploy/cloudfront-function.js 를 함께 고치세요.",
    );
    process.exit(1);
  }

  console.log(`rewrite 규칙 일치 확인 (${CASES.length}개 케이스)`);
}

await main();
