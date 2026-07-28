# PickUp FE 프로젝트 룰셋

> 프런트엔드 개발 규칙 문서. 신규 코드는 이 규칙을 따른다.
> 디자인 스펙은 [`DESIGN.md`](../DESIGN.md) 를 단일 출처(SSOT)로 삼는다.

---

## 1. 기술 스택

| 영역 | 선택 | 비고 |
| --- | --- | --- |
| 패키지 매니저 | **pnpm** | npm/yarn 사용 금지 |
| 빌드 | Vite | `@vitejs/plugin-react` |
| 언어 | TypeScript (strict) | 5.9.x 고정 (typescript-eslint 호환) |
| 라우팅 | **TanStack Router** | 파일 기반, `src/routes/**` |
| 서버 상태 | **TanStack Query** | 클라 상태와 분리 |
| API 클라이언트 | **Orval** | OpenAPI → 코드 생성 |
| HTTP | axios | 커스텀 인스턴스 경유 |
| 스타일 | **Tailwind v4** | CSS-first (`@theme`) |
| UI | **shadcn/ui** (new-york) | `src/components/ui` |
| 아이콘 | lucide-react | |

---

## 2. 폴더 구조

```
src/
├─ main.tsx              # 앱 엔트리 (Router + Query Provider)
├─ routeTree.gen.ts      # ⚙️ 자동 생성 (수정·커밋 금지)
├─ routes/               # 라우트 = 화면. 파일 기반 라우팅
│  ├─ __root.tsx         # 루트 레이아웃 + 라우트 컨텍스트
│  ├─ index.tsx          # "/" → /home 리다이렉트
│  ├─ login.tsx · register.tsx
│  ├─ _buyer/            # 구매자 레이아웃(파랑) — pathless
│  │  ├─ route.tsx       # GNB + data-role="buyer"
│  │  ├─ home.tsx · watchlist.tsx · mypage.tsx
│  │  └─ auctions/       # /auctions, /auctions/$id[/live|waiting|end]
│  └─ seller/            # 셀러 레이아웃(청록) — /seller
│     ├─ route.tsx       # GNB + data-role="seller"
│     ├─ index.tsx · sales.tsx · register.tsx
│     ├─ products/       # /seller/products[/$id]
│     ├─ apply.$productId.tsx
│     └─ auctions.$auctionId.tsx
├─ api/
│  ├─ generated/         # ⚙️ Orval 생성물 (수정·커밋 금지)
│  └─ mutator/           # axios 커스텀 인스턴스
├─ components/
│  ├─ ui/                # shadcn/ui 프리미티브 (CLI 관리)
│  ├─ domain/            # 도메인 컴포넌트 (auction-card, countdown, bid-list …)
│  └─ layout/            # gnb, page 컨테이너
├─ lib/
│  ├─ format.ts          # 금액·시간·마스킹 포맷
│  ├─ types.ts           # 도메인 타입(생성 모델 재노출 + UI 확장)
│  ├─ status.ts          # 상태→라벨/배지 매핑
│  ├─ query-client.ts · utils.ts
│  └─ mock/              # 🧪 목 데이터 (백엔드 연동 시 제거)
└─ styles/
   └─ globals.css        # 디자인 토큰 + Tailwind 진입점
```

> **모달**(입찰 확인·전체 입찰·입찰 실패)은 별도 라우트가 아니라 실시간 경매
> 화면(`live.tsx`) 내부의 `Dialog` 로 구현한다. **카드 등록 4단계**는 단일
> `register.tsx` 위저드에서 스텝 상태 + `StepIndicator` 로 처리한다.

> **목 데이터**: 백엔드 스펙 확정 전까지 `src/lib/mock` 을 사용한다. 데이터는
> Orval 생성 타입(`AuctionSummary`, `Bid` 등)을 만족하므로, 연동 시 mock import 를
> 생성 훅(`useListAuctions` 등) 호출로 바꾸면 컴포넌트는 그대로 재사용된다.

### 배치 규칙

- **화면 = 라우트 파일**. 화면 단위 컴포넌트/훅은 해당 라우트 옆(코로케이션)에 두고,
  2곳 이상에서 재사용될 때만 `components/`·`hooks/`·`lib/` 로 승격한다.
- `routeTree.gen.ts` 와 `api/generated/` 는 **생성물** 이다. 직접 수정하지 않으며
  `.gitignore` 대상이다. 필요 시 각 생성 명령으로 재생성한다.

---

## 3. 라우팅 (TanStack Router)

- 라우트는 `src/routes/**` 파일로 정의하며 Vite 플러그인이 `routeTree.gen.ts` 를
  자동 생성한다 (`pnpm dev`/`build` 시).
- 파일 규약: `index.tsx` → `/`, `login.tsx` → `/login`,
  `auctions/$auctionId.tsx` → `/auctions/:auctionId`, `_layout.tsx` → 레이아웃 라우트.
- 각 라우트는 `Route` 를 named export 한다.
- 데이터 프리페치는 라우트 `loader` 에서 `context.queryClient` 로 수행한다.

---

## 4. API · 데이터 페칭

### Orval 워크플로

1. 백엔드 스펙(`openapi.yaml` 또는 스펙 URL)이 갱신되면 `pnpm gen:api` 실행.
2. 생성된 **TanStack Query 훅**(`useListAuctions` 등)만 사용한다. axios 직접 호출 금지.
3. 생성 코드는 절대 수정하지 않는다. 동작 변경이 필요하면 스펙 또는
   `orval.config.ts` / mutator 를 고친다.

### TanStack Query 규칙

- 서버 상태는 전부 Query 로 관리한다. `useState` 에 서버 데이터를 복제하지 않는다.
- 쿼리 키는 Orval 생성 키를 사용한다.
- 실시간 화면(실시간 경매 등)은 개별 쿼리에서 `refetchInterval`/폴링 또는
  소켓 갱신을 설정한다. 전역 기본값은 `src/lib/query-client.ts` 참조.
- 인증 토큰은 `api/mutator/custom-instance.ts` 인터셉터에서 주입한다.

---

## 5. 스타일 · 디자인 토큰

- 색·간격·반경·타이포는 **`src/styles/globals.css` 의 CSS 변수** 를 단일 출처로 한다.
  하드코딩 hex/px 금지 — 토큰 없으면 `globals.css` 에 먼저 추가한다.
- 값 매핑 근거는 `DESIGN.md §3`.
- **다크 모드 단일**. 라이트 테마 대응은 하지 않는다.
- **역할 액센트**: 화면 루트에 `data-role="buyer" | "seller"` 를 지정하면
  `primary`/`ring` 이 파랑(구매자)/청록(셀러)으로 전환된다 (DESIGN.md §3.1).
- 금액·타이머 등 숫자는 `.tabular` (tabular-nums) 를 적용한다 (DESIGN.md §3.2).
- shadcn 컴포넌트는 CLI 로 추가: `pnpm dlx shadcn@latest add <name>`.
  variant 색은 시맨틱 토큰(`bg-primary`, `text-destructive` 등)을 따른다.

---

## 6. 코드 컨벤션

- **파일명**: 컴포넌트/라우트 `PascalCase` 함수 + `kebab-case`·라우트 규약 파일명,
  유틸/훅은 `camelCase`(`useXxx`). shadcn UI 파일은 CLI 규약(`button.tsx`) 유지.
- **경로 별칭**: 앱 내부 import 는 `@/…` 사용 (상대경로 `../../` 지양).
- **타입**: `strict` 준수. `any` 지양, 불가피하면 사유 주석. props 는 `interface` 또는
  `React.ComponentProps` 확장.
- **import type**: 타입 전용 import 는 `import type` (verbatimModuleSyntax 활성).
- 커밋 전 로컬에서 `pnpm typecheck && pnpm lint && pnpm format` 통과 확인.

---

## 7. 커밋 · 브랜치

- 커밋 메시지: Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`,
  `style:`, `test:`).
- 브랜치: `feature/<요약>`, `fix/<요약>`.
- 생성물(`routeTree.gen.ts`, `api/generated/`)·`dist/`·`.env` 는 커밋하지 않는다.

---

## 8. 명령어

| 명령 | 설명 |
| --- | --- |
| `pnpm dev` | 개발 서버 (http://localhost:5173) |
| `pnpm build` | 타입체크 + 프로덕션 빌드 |
| `pnpm preview` | 빌드 결과 미리보기 |
| `pnpm gen:api` | OpenAPI → `src/api/generated` 재생성 |
| `pnpm typecheck` | 타입 검사 |
| `pnpm lint` / `pnpm lint:fix` | ESLint |
| `pnpm format` | Prettier 포맷 |
