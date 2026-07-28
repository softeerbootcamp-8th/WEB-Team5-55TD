import { createFileRoute, Link } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { myBids, myWins } from "@/lib/mock/data";
import type { MyBidItem } from "@/lib/types";
import { MyBidStatus } from "@/lib/types";
import { formatWon } from "@/lib/format";

export const Route = createFileRoute("/_buyer/bids")({
  component: BidHistoryPage,
});

const STATUS_META: Record<
  MyBidStatus,
  { label: string; variant: "danger" | "success" | "warning" | "neutral" }
> = {
  OUTBID: { label: "추월됨", variant: "danger" },
  HIGHEST: { label: "최고가", variant: "success" },
  WON: { label: "낙찰", variant: "success" },
  LOST: { label: "미낙찰", variant: "neutral" },
};

/** DESIGN.md · mypage.html — 입찰 내역 / 낙찰 내역 */
function BidHistoryPage() {
  return (
    <PageContainer className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">입찰 / 낙찰 내역</h1>

      <Tabs defaultValue="bids">
        <TabsList>
          <TabsTrigger value="bids">입찰 내역</TabsTrigger>
          <TabsTrigger value="wins">낙찰 내역</TabsTrigger>
        </TabsList>

        <TabsContent value="bids">
          <BidTable items={myBids} />
        </TabsContent>
        <TabsContent value="wins">
          {myWins.length ? (
            <BidTable items={myWins} />
          ) : (
            <EmptyState title="낙찰 내역이 없습니다." />
          )}
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function BidTable({ items }: { items: MyBidItem[] }) {
  return (
    <ul className="flex flex-col gap-3">
      {items.map((it) => {
        const meta = STATUS_META[it.status];
        return (
          <li
            key={it.auctionId}
            className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
          >
            <CardThumb
              cardName={it.cardName}
              aspect="aspect-square"
              className="w-16 shrink-0"
            />
            <div className="flex flex-1 flex-col gap-1">
              <div className="flex items-center gap-2">
                <GradeBadge grade={it.grade} />
                <Badge variant={meta.variant}>{meta.label}</Badge>
              </div>
              <span className="text-sm font-semibold">{it.cardName}</span>
            </div>
            <div className="flex flex-col items-end gap-0.5">
              <span className="text-xs text-[var(--color-text-muted)]">
                내 입찰가
              </span>
              <span className="tabular text-sm font-semibold">
                {formatWon(it.myBid)}
              </span>
              <span className="tabular text-xs text-[var(--color-text-muted)]">
                현재가 {formatWon(it.currentPrice)}
              </span>
            </div>
            {it.live && (
              <Button size="sm" variant="secondary" asChild>
                <Link
                  to="/auctions/$auctionId/live"
                  params={{ auctionId: it.auctionId }}
                >
                  경매방 이동
                </Link>
              </Button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
