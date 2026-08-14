import { createFileRoute, Link, redirect } from "@tanstack/react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
import { PageContainer } from "@/components/layout/page";
import { AuctionCard } from "@/components/domain/auction-card";
import { EmptyState } from "@/components/domain/section-header";
import { Button } from "@/components/ui/button";
import {
  getGetMyWatchesQueryKey,
  getMyWatches,
} from "@/api/generated/member/member";
import type {
  WatchItemResponse,
  WatchItemResponseAuctionStatus,
} from "@/api/generated/model";
import { computeEndsAt } from "@/api/auctions";
import { isAuthenticated } from "@/lib/auth";
import { AuctionStatus, type AuctionSummary, type Grade } from "@/lib/types";
import { useLoadMoreSentinel } from "@/hooks/use-load-more-sentinel";

function toUiStatus(status?: WatchItemResponseAuctionStatus): AuctionStatus {
  if (status === "ONGOING") return AuctionStatus.LIVE;
  if (status === "WON" || status === "PASSED") return AuctionStatus.ENDED;
  return AuctionStatus.UPCOMING;
}

function parseGrade(value?: string | null): Grade | undefined {
  if (!value) return undefined;
  const [agency, ...score] = value.trim().split(/\s+/);
  if (!agency || score.length === 0) return undefined;
  return { agency: agency as Grade["agency"], score: score.join(" ") };
}

function toAuctionSummary(item: WatchItemResponse): AuctionSummary {
  return {
    id: String(item.auctionId),
    cardName: item.card?.cardName ?? "",
    thumbnailUrl: item.thumbnailUrl ?? item.card?.imageUrl ?? undefined,
    status: toUiStatus(item.auctionStatus),
    grade: parseGrade(item.grade),
    currentPrice: item.currentPrice ?? undefined,
    startPrice: item.startingPrice,
    endsAt: computeEndsAt(item),
    startsAt: item.startedAt ?? undefined,
    watchCount: item.watchCount,
    watched: item.watched,
  };
}

export const Route = createFileRoute("/_buyer/watchlist")({
  beforeLoad: () => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login" });
    }
  },
  component: WatchlistPage,
});

/** 관심 등록한 경매 목록 — 서버가 반환하는 진행 전·진행 중 경매를 모두 표시한다. */
function WatchlistPage() {
  const {
    data,
    isPending,
    isError,
    refetch,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useInfiniteQuery({
    queryKey: getGetMyWatchesQueryKey(),
    queryFn: ({ pageParam }) => getMyWatches({ cursor: pageParam, size: 20 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? (lastPage.cursor ?? undefined) : undefined,
  });
  const watchlist = (data?.pages.flatMap((page) => page.items ?? []) ?? []).map(
    toAuctionSummary,
  );

  const sentinelRef = useLoadMoreSentinel({
    enabled: Boolean(hasNextPage) && !isFetchingNextPage,
    onIntersect: fetchNextPage,
  });

  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">관심 목록</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          관심 등록한 경매입니다. 낙찰 시 자동으로 제거됩니다.
        </p>
      </div>

      {isPending ? (
        <p className="py-12 text-center text-sm text-[var(--color-text-sub)]">
          관심 경매를 불러오는 중입니다.
        </p>
      ) : isError ? (
        <EmptyState
          title="관심 경매를 불러오지 못했습니다."
          description="잠시 후 다시 시도해 주세요."
          action={
            <button
              type="button"
              onClick={() => refetch()}
              className="text-sm font-semibold text-primary hover:underline"
            >
              다시 시도
            </button>
          }
        />
      ) : watchlist.length === 0 ? (
        <EmptyState
          title="관심 등록한 경매가 없습니다."
          description="경매 목록에서 하트를 눌러 관심 경매를 모아보세요."
          action={
            <Button asChild>
              <Link to="/auctions">경매 둘러보기</Link>
            </Button>
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
            {watchlist.map((a) => (
              <AuctionCard key={a.id} auction={a} />
            ))}
          </div>
          {hasNextPage && (
            <div
              ref={sentinelRef}
              className="flex flex-col items-center gap-2 py-2"
            >
              <Button
                variant="secondary"
                disabled={isFetchingNextPage}
                onClick={() => fetchNextPage()}
              >
                {isFetchingNextPage ? "불러오는 중" : "더 보기"}
              </Button>
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}
