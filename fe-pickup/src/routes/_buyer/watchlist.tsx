import { createFileRoute, Link, redirect } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { AuctionCard } from "@/components/domain/auction-card";
import { EmptyState } from "@/components/domain/section-header";
import { Button } from "@/components/ui/button";
import { watchlist } from "@/lib/mock/data";
import { isAuthenticated } from "@/lib/auth";

export const Route = createFileRoute("/_buyer/watchlist")({
  beforeLoad: ({ location }) => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login", search: { redirect: location.href } });
    }
  },
  component: WatchlistPage,
});

/** DESIGN.md · watchlist.html — 관심 등록한 예정 경매만 (최신순) */
function WatchlistPage() {
  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">관심 목록</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          관심 등록한 예정 경매입니다. 낙찰 시 자동으로 제거됩니다.
        </p>
      </div>

      {watchlist.length === 0 ? (
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
        <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
          {watchlist.map((a) => (
            <AuctionCard key={a.id} auction={a} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}
