import { useCallback, useState } from "react";
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { ChevronLeft, Lock } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { StatusBadge } from "@/components/domain/status-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { ConnectionStatus } from "@/components/domain/connection-status";
import { BidList } from "@/components/domain/bid-list";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { getAuctionDetail } from "@/api/auctions";
import { BID_MODAL_SIZE, BID_PREVIEW_SIZE, getAuctionBids } from "@/api/bids";
import {
  useAuctionBidUpdates,
  type AuctionBidUpdatedMessage,
} from "@/hooks/use-auction-bid-updates";
import { useNickname } from "@/lib/auth";
import { AuctionStatus } from "@/lib/types";

export const Route = createFileRoute("/seller/auctions/$auctionId")({
  loader: async ({ params }) => {
    try {
      return { auction: await getAuctionDetail(params.auctionId) };
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 404) {
        throw notFound();
      }
      throw error;
    }
  },
  component: SellerAuctionPage,
});

/** DESIGN.md · seller-auction.html — 입찰 기능 제외, 모니터링 전용 */
function SellerAuctionPage() {
  const { auction } = Route.useLoaderData();
  const myNickname = useNickname();
  // 서버가 아직 소유자 검증을 하지 않아 프런트에서 우선 가드한다 — 진짜 인가는 백엔드에서 처리돼야 한다.
  const isOwner =
    !myNickname || !auction.sellerNickname || myNickname === auction.sellerNickname;
  const [allBidsOpen, setAllBidsOpen] = useState(false);
  const [liveCurrentPrice, setLiveCurrentPrice] = useState<number>();
  const queryClient = useQueryClient();

  // 실시간 경매 화면(live.tsx)의 "최근 N건 미리보기 + 전체보기 모달" 구조를 쓰지만,
  // 여기서는 미리보기용으로 따로 6건만 조회하지 않고 모달과 같은 쿼리를 재사용한다 —
  // 6건만 받아오면 입찰 횟수도 6건을 넘는 순간 전부 "6+"처럼 뭉개져 표시되므로,
  // 한 번에 최대 100건을 받아 그중 앞 6건만 미리보기로 자른다.
  const bidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id],
    queryFn: () => getAuctionBids(auction.id, { size: BID_MODAL_SIZE }),
    enabled: isOwner,
  });
  const bids = bidsQuery.data?.items ?? [];
  // 100건을 넘겨 실제로 잘렸을 때만 "100+"처럼 표시한다.
  const bidCount = bidsQuery.data
    ? `${bids.length}${bidsQuery.data.hasNext ? "+" : ""}`
    : undefined;

  // 진행 중인 경매만 실시간으로 지켜본다 — 종료된 경매는 갱신될 일이 없다.
  const isLive = auction.status === AuctionStatus.LIVE;
  const refreshBids = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["auction-bids", auction.id],
    });
  }, [auction.id, queryClient]);
  const applyBidUpdate = useCallback(
    (message: AuctionBidUpdatedMessage) => {
      // 경매 상세는 라우터 로더가 한 번만 읽어오므로 현재가는 이벤트 값으로 덮어쓴다.
      setLiveCurrentPrice(message.currentPrice);
      refreshBids();
    },
    [refreshBids],
  );
  const connectionStatus = useAuctionBidUpdates({
    auctionId: auction.id,
    enabled: isOwner && isLive,
    onBidUpdated: applyBidUpdate,
    onSubscribed: refreshBids,
  });

  if (!isOwner) {
    return (
      <PageContainer className="flex flex-col gap-6">
        <EmptyState
          title="본인 소유의 경매만 모니터링할 수 있습니다."
          action={
            <Button variant="secondary" asChild>
              <Link to="/seller">셀러 홈으로</Link>
            </Button>
          }
        />
      </PageContainer>
    );
  }

  const images = auction.images ?? [];
  const currentPrice =
    liveCurrentPrice ?? auction.currentPrice ?? auction.startPrice;

  return (
    <PageContainer className="grid gap-8 md:grid-cols-[1fr_380px]">
      <div className="flex flex-col gap-6">
        <Link
          to="/seller"
          className="inline-flex items-center gap-1 text-sm text-[var(--color-text-sub)] hover:text-foreground"
        >
          <ChevronLeft className="size-4" /> 셀러 홈
        </Link>

        <div className="grid gap-6 sm:grid-cols-[220px_1fr]">
          <CardThumb
            cardName={auction.cardName}
            grade={auction.grade}
            imageUrl={images[0] ?? auction.thumbnailUrl}
          />
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <GradeBadge grade={auction.grade} />
              <StatusBadge status={auction.status} />
              {isLive && <ConnectionStatus status={connectionStatus} />}
            </div>
            <h1 className="text-2xl font-bold">{auction.cardName}</h1>

            <div className="mt-2 grid grid-cols-1 gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-5 sm:grid-cols-3 sm:gap-3">
              <Price amount={currentPrice} label="현재가" size="md" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  입찰 횟수
                </span>
                <span className="tabular text-lg font-bold">
                  {bidCount ?? "-"}
                </span>
              </div>
              <div className="flex flex-col items-start gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  남은 시간
                </span>
                {auction.endsAt ? (
                  <Countdown to={auction.endsAt} className="text-base" />
                ) : (
                  <span className="tabular text-base font-semibold">-</span>
                )}
              </div>
            </div>

            <p className="inline-flex items-center gap-1.5 text-xs text-[var(--color-text-muted)]">
              <Lock className="size-3.5" /> 진행 중인 경매는 수정·취소할 수
              없습니다.
            </p>
          </div>
        </div>
      </div>

      {/* 입찰 내역 (읽기 전용, 최근 6건 미리보기 + 전체보기 모달) */}
      <aside className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">입찰 내역</h2>
          <button
            type="button"
            onClick={() => setAllBidsOpen(true)}
            className="text-sm font-semibold text-primary hover:underline"
          >
            전체
          </button>
        </div>
        {bidsQuery.isError ? (
          <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
            입찰 내역을 불러오지 못했습니다.
          </p>
        ) : bidsQuery.isPending ? (
          <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
            불러오는 중입니다.
          </p>
        ) : (
          <BidList bids={bids.slice(0, BID_PREVIEW_SIZE)} />
        )}
      </aside>

      {/* 전체 입찰 모달 — 미리보기와 같은 쿼리 결과를 그대로 재사용한다. */}
      <Dialog open={allBidsOpen} onOpenChange={setAllBidsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>전체 입찰 내역</DialogTitle>
          </DialogHeader>
          {bidsQuery.isError ? (
            <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
              입찰 내역을 불러오지 못했습니다.
            </p>
          ) : bidsQuery.isPending ? (
            <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
              불러오는 중입니다.
            </p>
          ) : (
            <BidList bids={bids} className="max-h-96 overflow-y-auto" />
          )}
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
