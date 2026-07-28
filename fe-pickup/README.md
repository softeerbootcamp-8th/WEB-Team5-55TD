# PickUp · Frontend

> **검증된 카드, 안심 경매** — 인증기관 감정 정보 기반 TCG 카드 경매 플랫폼의 프런트엔드.

- 디자인 스펙: [`DESIGN.md`](./DESIGN.md)
- 개발 규칙: [`docs/RULESET.md`](./docs/RULESET.md)

## 기술 스택

Vite · React 19 · TypeScript · **TanStack Router / Query** · **Orval**(OpenAPI 코드 생성) · **Tailwind v4** · **shadcn/ui**(new-york, 다크 전용) · pnpm

## 시작하기

```bash
pnpm install       # 의존성 설치
pnpm gen:api       # OpenAPI(openapi.yaml) → src/api/generated 생성
pnpm dev           # 개발 서버 → http://localhost:5173
```

> `.env.example` 를 `.env` 로 복사해 `VITE_API_BASE_URL` 을 설정할 수 있습니다.
> 미설정 시 Vite 프록시(`/api` → `http://localhost:8080`)를 사용합니다.

## 명령어

| 명령 | 설명 |
| --- | --- |
| `pnpm dev` | 개발 서버 |
| `pnpm build` | 타입체크 + 프로덕션 빌드 (`dist/`) |
| `pnpm preview` | 빌드 미리보기 |
| `pnpm gen:api` | OpenAPI → API 클라이언트/훅 재생성 |
| `pnpm typecheck` | 타입 검사 |
| `pnpm lint` · `pnpm lint:fix` | ESLint |
| `pnpm format` | Prettier |

## 폴더 구조

```
src/
├─ routes/         # 화면 = 라우트 (파일 기반 라우팅)
├─ api/
│  ├─ generated/   # ⚙️ Orval 생성물
│  └─ mutator/     # axios 커스텀 인스턴스
├─ components/ui/  # shadcn/ui 프리미티브
├─ lib/            # 유틸 (cn, query-client)
└─ styles/         # globals.css (디자인 토큰)
```

자세한 규칙은 [`docs/RULESET.md`](./docs/RULESET.md) 참고.
