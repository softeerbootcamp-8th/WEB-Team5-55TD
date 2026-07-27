import { defineConfig } from "orval";

/**
 * Orval — OpenAPI 스펙에서 TanStack Query 훅 + 타입을 생성한다.
 *
 * 입력: ./openapi.yaml (백엔드가 스펙 URL 을 제공하면 target 을 URL 로 교체)
 * 출력: src/api/generated  (커밋 대상 아님 — .gitignore 참조)
 *
 * 실행: pnpm gen:api
 */
export default defineConfig({
  cardian: {
    input: {
      target: "./openapi.yaml",
    },
    output: {
      mode: "tags-split", // 태그별 파일 분리
      target: "./src/api/generated",
      schemas: "./src/api/generated/model",
      client: "react-query",
      httpClient: "axios",
      clean: true,
      override: {
        mutator: {
          path: "./src/api/mutator/custom-instance.ts",
          name: "customInstance",
        },
        query: {
          useQuery: true,
          useInfinite: true,
          useInfiniteQueryParam: "page",
        },
      },
    },
    hooks: {
      afterAllFilesWrite: "prettier --write",
    },
  },
});
