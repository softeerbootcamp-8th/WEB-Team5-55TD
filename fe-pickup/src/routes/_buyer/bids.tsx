import { createFileRoute, Link, redirect } from "@tanstack/react-router";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getMyBids, getMyWins } from "@/api/bids";
import type { MyBidItem } from "@/lib/types";
import { MyBidStatus } from "@/lib/types";
import { formatWon } from "@/lib/format";
import { isAuthenticated } from "@/lib/auth";

interface MyBidsPage {
  items: MyBidItem[];
  hasNext: boolean;
  cursor?: string;
}

export const Route = createFileRoute("/_buyer/bids")({
  beforeLoad: ({ location }) => {
    if (!isAuthenticated()) {
      throw redirect({ to: "/login", search: { redirect: location.href } });
    }
  },
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
  const bidsQuery = useQuery({
    queryKey: ["my-bids"],
    queryFn: () => getMyBids({ size: 100 }),
  });
  const winsQuery = useQuery({
    queryKey: ["my-wins"],
    queryFn: () => getMyWins({ size: 100 }),
  });

  return (
    <PageContainer className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">입찰 / 낙찰 내역</h1>

      <Tabs defaultValue="bids">
        <TabsList>
          <TabsTrigger value="bids">입찰 내역</TabsTrigger>
          <TabsTrigger value="wins">낙찰 내역</TabsTrigger>
        </TabsList>

        <TabsContent value="bids">
          <BidHistorySection
            query={bidsQuery}
            loadingLabel="입찰 내역을 불러오는 중입니다."
            errorTitle="입찰 내역을 불러오지 못했습니다."
            emptyTitle="입찰 내역이 없습니다."
          />
        </TabsContent>
        <TabsContent value="wins">
          <BidHistorySection
            query={winsQuery}
            loadingLabel="낙찰 내역을 불러오는 중입니다."
            errorTitle="낙찰 내역을 불러오지 못했습니다."
            emptyTitle="낙찰 내역이 없습니다."
          />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function BidHistorySection({
  query,
  loadingLabel,
  errorTitle,
  emptyTitle,
}: {
  query: UseQueryResult<MyBidsPage>;
  loadingLabel: string;
  errorTitle: string;
  emptyTitle: string;
}) {
  const { data, isPending, isError, refetch } = query;

  if (isPending) {
    return (
      <p className="py-12 text-center text-sm text-[var(--color-text-sub)]">
        {loadingLabel}
      </p>
    );
  }

  if (isError) {
    return (
      <EmptyState
        title={errorTitle}
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
    );
  }

  const items = data.items;
  return items.length ? (
    <BidTable items={items} />
  ) : (
    <EmptyState title={emptyTitle} />
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
            className="flex flex-wrap items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
          >
            <CardThumb
              cardName={it.cardName}
              imageUrl={it.thumbnailUrl}
              aspect="aspect-square"
              className="w-16 shrink-0"
            />
            <div className="flex min-w-0 flex-1 flex-col gap-1">
              <div className="flex items-center gap-2">
                <GradeBadge grade={it.grade} />
                <Badge variant={meta.variant}>{meta.label}</Badge>
              </div>
              <span className="text-sm font-semibold">{it.cardName}</span>
            </div>
            <div className="ml-auto flex flex-wrap items-center justify-end gap-3">
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
            </div>
          </li>
        );
      })}
    </ul>
  );
}
