import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { PageContainer } from "@/components/layout/page";
import { SectionHeader, EmptyState } from "@/components/domain/section-header";
import { AuctionCard } from "@/components/domain/auction-card";
import { CardThumb } from "@/components/domain/card-thumb";
import { StatusBadge } from "@/components/domain/status-badge";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { Button } from "@/components/ui/button";
import { getFeaturedAuction, searchAuctions } from "@/api/auctions";

export const Route = createFileRoute("/_buyer/home")({
  component: HomePage,
});

/** DESIGN.md · home.html — 대표 경매 + 진행 중(≤3) + 곧 시작(≤4) */
function HomePage() {
  const featuredQuery = useQuery({
    queryKey: ["auctions", "featured"],
    queryFn: () => getFeaturedAuction(),
  });
  const liveQuery = useQuery({
    queryKey: ["auctions", "home-live"],
    queryFn: () =>
      searchAuctions({ status: ["ONGOING"], sort: "POPULAR", size: 3 }),
  });
  const upcomingQuery = useQuery({
    queryKey: ["auctions", "home-upcoming"],
    queryFn: () =>
      searchAuctions({ status: ["SCHEDULED"], sort: "STARTING_SOON", size: 4 }),
  });

  const live = liveQuery.data?.items ?? [];
  const upcoming = upcomingQuery.data?.items ?? [];
  // 대표 경매 API가 우선이며(진행 중 + 관심 최다), 진행 중인 경매가 없어 404면
  // 목록에서 가져온 진행 중/예정 경매로 대체한다.
  const featured = featuredQuery.isPending
    ? undefined
    : (featuredQuery.data ?? live[0] ?? upcoming[0]);

  return (
    <PageContainer className="flex flex-col gap-12">
      {/* 대표 경매 hero */}
      {featured && (
        <section className="grid gap-8 rounded-[var(--radius-lg)] border border-border bg-card p-4 md:p-6 md:grid-cols-[300px_1fr]">
          <CardThumb
            cardName={featured.cardName}
            grade={featured.grade}
            imageUrl={featured.thumbnailUrl}
            className="mx-auto w-full max-w-[280px] md:max-w-none"
          />
          <div className="flex flex-col justify-center gap-4">
            <div className="flex items-center gap-2">
              <StatusBadge status={featured.status} />
              <GradeBadge grade={featured.grade} />
              <span className="text-xs text-[var(--color-text-muted)]">
                관심 {featured.watchCount}
              </span>
            </div>
            <h1 className="text-2xl md:text-3xl font-bold">{featured.cardName}</h1>
            <div className="flex items-end gap-8">
              <Price amount={featured.currentPrice} label="현재가" size="lg" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  남은 시간
                </span>
                <Countdown to={featured.endsAt} />
              </div>
            </div>
            <div className="mt-2 flex gap-3">
              <Button size="lg" asChild>
                <Link
                  to="/auctions/$auctionId/live"
                  params={{ auctionId: featured.id }}
                >
                  입찰 참여
                </Link>
              </Button>
              <Button size="lg" variant="secondary" asChild>
                <Link to="/auctions/$auctionId" params={{ auctionId: featured.id }}>
                  상세 보기
                </Link>
              </Button>
            </div>
          </div>
        </section>
      )}

      {/* 진행 중 */}
      <section className="flex flex-col gap-5">
        <SectionHeader
          title="진행 중인 경매"
          description="지금 입찰할 수 있는 경매입니다."
          action={
            <Link
              to="/auctions"
              className="text-sm text-[var(--color-text-sub)] hover:text-primary"
            >
              전체 보기 ›
            </Link>
          }
        />
        {!liveQuery.isPending && live.length === 0 ? (
          <EmptyState
            title="진행 중인 경매가 없습니다."
            description="곧 새로운 경매가 시작될 예정이에요."
          />
        ) : (
          <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
            {live.map((a) => (
              <AuctionCard key={a.id} auction={a} />
            ))}
          </div>
        )}
      </section>

      {/* 곧 시작 */}
      <section className="flex flex-col gap-5">
        <SectionHeader
          title="곧 시작하는 경매"
          description="시작 임박 순으로 보여드려요."
        />
        {!upcomingQuery.isPending && upcoming.length === 0 ? (
          <EmptyState
            title="시작 예정인 경매가 없습니다."
            description="새로운 경매 등록을 기다려주세요."
          />
        ) : (
          <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
            {upcoming.map((a) => (
              <AuctionCard key={a.id} auction={a} />
            ))}
          </div>
        )}
      </section>
    </PageContainer>
  );
}
