import { Link, useRouter } from "@tanstack/react-router";
import { SearchX } from "lucide-react";
import { Button } from "@/components/ui/button";

/** 404 — 존재하지 않는 경로 접근 시 표시. 감정 등급 카드 슬랩 모티프 + EmptyState와 동일한 중립 톤. */
export function NotFoundPage() {
  const router = useRouter();

  return (
    <main
      data-role="buyer"
      className="mx-auto flex min-h-dvh w-full max-w-sm flex-col items-center justify-center gap-8 px-8 text-center"
    >
      <div className="flex aspect-[3/4] w-40 flex-col items-center justify-center gap-2 overflow-hidden rounded-[var(--radius-lg)] border border-border bg-[var(--color-surface-2)]">
        <SearchX className="size-9 text-primary" />
        <span className="text-4xl font-black text-primary">404</span>
        <span className="rounded-[var(--radius-pill)] bg-primary px-2 py-0.5 text-[10px] tracking-widest text-white uppercase">
          Not Found
        </span>
      </div>

      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold">페이지를 찾을 수 없습니다</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          요청하신 페이지가 삭제되었거나<br/> 주소가 변경되었을 수 있습니다.
        </p>
      </div>

      <div className="flex w-full gap-3">
        <Button
          type="button"
          variant="secondary"
          className="flex-1"
          onClick={() => router.history.back()}
        >
          이전 페이지
        </Button>
        <Button asChild className="flex-1">
          <Link to="/home">홈으로 가기</Link>
        </Button>
      </div>
    </main>
  );
}
