import { createFileRoute } from "@tanstack/react-router";
import { useHealthCheck } from "@/api/generated/health/health";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/health")({
  component: HealthPage,
});

/**
 * 백엔드 연동 시험 페이지 — GET /healthcheck 를 호출해 서버 상태를 표시한다.
 * (프론트 baseURL `/api` → vite 프록시 rewrite → 백엔드 `/healthcheck`)
 */
function HealthPage() {
  const { data, error, isLoading, isFetching, refetch } = useHealthCheck({
    query: { retry: 0, refetchOnWindowFocus: false },
  });

  const isOk = data?.status === "OK";

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-md flex-col justify-center gap-6 px-8">
      <div className="flex flex-col gap-1 text-center">
        <h1 className="text-2xl font-bold">백엔드 연동 시험</h1>
        <p className="text-sm text-[var(--color-text-sub)]">GET /healthcheck</p>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between gap-2">
            <CardTitle>서버 상태</CardTitle>
            {isLoading ? (
              <Badge variant="muted">로딩 중…</Badge>
            ) : error ? (
              <Badge variant="danger">연결 실패</Badge>
            ) : isOk ? (
              <Badge variant="success">정상 (OK)</Badge>
            ) : (
              <Badge variant="warning">응답: {data?.status}</Badge>
            )}
          </div>
          <CardDescription>
            프론트엔드에서 백엔드 헬스체크 API를 호출한 결과입니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <pre className="overflow-x-auto rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-3 text-xs text-[var(--color-text-sub)]">
            {error
              ? String((error as Error).message)
              : JSON.stringify(data ?? null, null, 2)}
          </pre>
          <Button
            variant="secondary"
            onClick={() => refetch()}
            disabled={isFetching}
          >
            {isFetching ? "확인 중…" : "다시 확인"}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
