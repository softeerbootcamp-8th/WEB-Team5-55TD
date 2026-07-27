import { createFileRoute, Link } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { SectionHeader } from "@/components/domain/section-header";
import { AuctionCard } from "@/components/domain/auction-card";
import { CardThumb } from "@/components/domain/card-thumb";
import { StatusBadge } from "@/components/domain/status-badge";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { Button } from "@/components/ui/button";
import { auctionSummaries, featuredAuction } from "@/lib/mock/data";
import { AuctionStatus } from "@/lib/types";

export const Route = createFileRoute("/_buyer/home")({
  component: HomePage,
});

/** DESIGN.md · home.html — 대표 경매 + 진행 중(≤3) + 곧 시작(≤4) */
function HomePage() {
  const live = auctionSummaries
    .filter((a) => a.status === AuctionStatus.LIVE)
    .slice(0, 3);
  const upcoming = auctionSummaries
    .filter((a) => a.status === AuctionStatus.UPCOMING)
    .slice(0, 4);

  return (
    <PageContainer className="flex flex-col gap-12">
      {/* 대표 경매 hero */}
      <section className="grid gap-8 rounded-[var(--radius-lg)] border border-border bg-card p-6 md:grid-cols-[300px_1fr]">
        <CardThumb
          cardName={featuredAuction.cardName}
          grade={featuredAuction.grade}
          className="w-full"
        />
        <div className="flex flex-col justify-center gap-4">
          <div className="flex items-center gap-2">
            <StatusBadge status={featuredAuction.status} />
            <GradeBadge grade={featuredAuction.grade} />
            <span className="text-xs text-[var(--color-text-muted)]">
              관심 {featuredAuction.watchCount}
            </span>
          </div>
          <h1 className="text-3xl font-bold">{featuredAuction.cardName}</h1>
          <div className="flex items-end gap-8">
            <Price
              amount={featuredAuction.currentPrice}
              label="현재가"
              size="lg"
            />
            <div className="flex flex-col gap-0.5">
              <span className="text-xs text-[var(--color-text-muted)]">
                남은 시간
              </span>
              <Countdown to={featuredAuction.endsAt} />
            </div>
          </div>
          <div className="mt-2 flex gap-3">
            <Button size="lg" asChild>
              <Link
                to="/auctions/$auctionId/live"
                params={{ auctionId: featuredAuction.id }}
              >
                입찰 참여
              </Link>
            </Button>
            <Button size="lg" variant="secondary" asChild>
              <Link
                to="/auctions/$auctionId"
                params={{ auctionId: featuredAuction.id }}
              >
                상세 보기
              </Link>
            </Button>
          </div>
        </div>
      </section>

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
        <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
          {live.map((a) => (
            <AuctionCard key={a.id} auction={a} />
          ))}
        </div>
      </section>

      {/* 곧 시작 */}
      <section className="flex flex-col gap-5">
        <SectionHeader
          title="곧 시작하는 경매"
          description="시작 임박 순으로 보여드려요."
        />
        <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
          {upcoming.map((a) => (
            <AuctionCard key={a.id} auction={a} />
          ))}
        </div>
      </section>
    </PageContainer>
  );
}
