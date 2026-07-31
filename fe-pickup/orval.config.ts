import { defineConfig } from "orval";

/**
 * Orval — OpenAPI 스펙에서 TanStack Query 훅 + 타입을 생성한다.
 *
 * 입력: ./openapi.yaml (저장소에 커밋된 API 계약서)
 * 출력: src/api/generated  (커밋 대상 아님 — .gitignore 참조)
 *
 * 실행: pnpm gen:api
 *
 * target 을 서버 URL(예: http://localhost:8080/v3/api-docs)로 바꾸지 말 것.
 * CI 러너에는 백엔드가 없어 빌드가 반드시 실패하고, 로컬에서도 각자 띄운
 * 백엔드 버전에 따라 생성 결과가 달라져 빌드를 재현할 수 없게 된다.
 * 백엔드 API 가 바뀌면 서버의 /v3/api-docs.yaml 을 보고 openapi.yaml 을
 * 갱신해 함께 커밋한다.
 */
export default defineConfig({
  pickup: {
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
