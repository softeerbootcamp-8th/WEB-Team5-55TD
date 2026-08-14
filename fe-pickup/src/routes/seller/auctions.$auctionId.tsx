import { useCallback, useEffect, useState } from "react";
import {
  createFileRoute,
  Link,
  notFound,
  useNavigate,
} from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { ChevronLeft, Lock } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { ImageGallery } from "@/components/domain/image-gallery";
import { GradeBadge } from "@/components/domain/grade-badge";
import { StatusBadge } from "@/components/domain/status-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { ConnectionStatus } from "@/components/domain/connection-status";
import { BidList, RealtimeBidList } from "@/components/domain/bid-list";
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
import {
  mergeLatestBid,
  type AuctionBidsSnapshot,
} from "@/lib/auction-live-state";
import { dedupeBidsByBidder } from "@/lib/bids";
import { AuctionStatus } from "@/lib/types";

const AUCTION_STATUS_POLLING_INTERVAL_MILLIS = 15_000;

function laterEndTime(
  current: string | null | undefined,
  next: string | null | undefined,
): string | undefined {
  if (!next) return current ?? undefined;
  if (!current) return next;
  return Date.parse(next) >= Date.parse(current) ? next : current;
}

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
  const { auction: initialAuction } = Route.useLoaderData();
  const navigate = useNavigate();
  const myNickname = useNickname();
  const queryClient = useQueryClient();
  const auctionQuery = useQuery({
    queryKey: ["auction-detail", initialAuction.id],
    queryFn: () => getAuctionDetail(initialAuction.id),
    initialData: initialAuction,
    staleTime: 0,
    refetchInterval: (query) =>
      query.state.data?.status === AuctionStatus.LIVE &&
      document.visibilityState !== "hidden"
        ? AUCTION_STATUS_POLLING_INTERVAL_MILLIS
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const auction = auctionQuery.data;
  // 서버가 아직 소유자 검증을 하지 않아 프런트에서 우선 가드한다 — 진짜 인가는 백엔드에서 처리돼야 한다.
  const isOwner =
    !myNickname ||
    !auction.sellerNickname ||
    myNickname === auction.sellerNickname;
  const [allBidsOpen, setAllBidsOpen] = useState(false);
  const [realtimeSnapshot, setRealtimeSnapshot] = useState({
    auctionId: auction.id,
    price: auction.currentPrice ?? auction.startPrice ?? 0,
    endsAt: auction.endsAt,
  });

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
  const previewBids = dedupeBidsByBidder(bids).slice(0, BID_PREVIEW_SIZE);
  const latestBidId = bids[0] ? Number(bids[0].id) : undefined;
  // 100건을 넘겨 실제로 잘렸을 때만 "100+"처럼 표시한다.
  const bidCount = bidsQuery.data
    ? `${bids.length}${bidsQuery.data.hasNext ? "+" : ""}`
    : undefined;

  // 진행 중인 경매만 실시간으로 지켜본다 — 종료된 경매는 갱신될 일이 없다.
  const isLive = auction.status === AuctionStatus.LIVE;
  const refreshAuction = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["auction-detail", auction.id],
    });
  }, [auction.id, queryClient]);
  const refreshBids = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["auction-bids", auction.id],
    });
  }, [auction.id, queryClient]);
  const refreshSnapshot = useCallback(() => {
    refreshAuction();
    refreshBids();
  }, [refreshAuction, refreshBids]);
  const applyBidUpdate = useCallback(
    (message: AuctionBidUpdatedMessage) => {
      setRealtimeSnapshot((current) => ({
        auctionId: auction.id,
        price:
          current.auctionId === auction.id
            ? Math.max(current.price, message.currentPrice)
            : message.currentPrice,
        endsAt:
          current.auctionId === auction.id
            ? laterEndTime(current.endsAt, message.endedAt)
            : (message.endedAt ?? undefined),
      }));
      queryClient.setQueryData<AuctionBidsSnapshot | undefined>(
        ["auction-bids", auction.id],
        (snapshot) => {
          const isNewBid = !snapshot?.items.some(
            (bid) => bid.id === String(message.latestBid.bidId),
          );
          const isAtLimit = (snapshot?.items.length ?? 0) >= BID_MODAL_SIZE;
          const updated = mergeLatestBid(
            snapshot,
            message.latestBid,
            false,
            BID_MODAL_SIZE,
          );

          return updated && isNewBid && isAtLimit
            ? { ...updated, hasNext: true }
            : updated;
        },
      );
      refreshBids();
    },
    [auction.id, queryClient, refreshBids],
  );
  const connectionStatus = useAuctionBidUpdates({
    auctionId: auction.id,
    latestBidId,
    enabled: isOwner && isLive,
    onBidUpdated: applyBidUpdate,
    onSubscribed: refreshSnapshot,
  });

  useEffect(() => {
    if (auction.status !== AuctionStatus.ENDED) return;

    void navigate({
      to: "/auctions/$auctionId/end",
      params: { auctionId: auction.id },
    });
  }, [auction.id, auction.status, navigate]);

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
  // 개별 이미지가 없으면 썸네일 한 장으로라도 갤러리를 구성한다.
  const galleryImages =
    images.length > 0
      ? images
      : auction.thumbnailUrl
        ? [auction.thumbnailUrl]
        : [];
  const snapshotPrice = auction.currentPrice ?? auction.startPrice ?? 0;
  const currentPrice = Math.max(
    snapshotPrice,
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.price
      : snapshotPrice,
  );
  const endsAt = laterEndTime(
    auction.endsAt,
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.endsAt
      : auction.endsAt,
  );

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
          <ImageGallery
            images={galleryImages}
            cardName={auction.cardName}
            grade={auction.grade}
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
                {endsAt ? (
                  <Countdown
                    to={endsAt}
                    className="text-base"
                    onEnd={refreshAuction}
                  />
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
          <RealtimeBidList
            bids={previewBids}
            hasNext={false}
            isFetchingNextPage={false}
            onLoadMore={() => undefined}
          />
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
