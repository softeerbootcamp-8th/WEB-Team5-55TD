import { useCallback, useEffect, useRef, useState } from "react";
import {
  createFileRoute,
  Link,
  notFound,
  useNavigate,
} from "@tanstack/react-router";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { AxiosError } from "axios";
import { toast } from "sonner";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { BidList, RealtimeBidList } from "@/components/domain/bid-list";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { getAuctionDetail } from "@/api/auctions";
import {
  BID_MODAL_SIZE,
  createBidRequest,
  getAuctionBids,
  getBidErrorMessage,
  REALTIME_BID_PAGE_SIZE,
} from "@/api/bids";
import { getGetMyPointBalanceQueryKey } from "@/api/generated/member/member";
import { refreshAccessToken } from "@/api/mutator/custom-instance";
import {
  useAuctionBidUpdates,
  type AuctionBidUpdatedMessage,
  type BidRequestFailedMessage,
} from "@/hooks/use-auction-bid-updates";
import { isAuthenticated, useIsAuthenticated } from "@/lib/auth";
import { formatWon } from "@/lib/format";
import { AuctionStatus } from "@/lib/types";

const ACTIVE_POLLING_INTERVAL_MILLIS = 15_000;
const POLLING_JITTER_MILLIS = 3_000;
const BID_REQUEST_RESULT_TIMEOUT_MILLIS = 10_000;

function pollingInterval() {
  return (
    ACTIVE_POLLING_INTERVAL_MILLIS +
    Math.floor(Math.random() * POLLING_JITTER_MILLIS)
  );
}

function laterEndTime(
  current: string | null | undefined,
  next: string | null | undefined,
): string | undefined {
  if (!next) return current ?? undefined;
  if (!current) return next;
  return Date.parse(next) >= Date.parse(current) ? next : current;
}

export const Route = createFileRoute("/_buyer/auctions/$auctionId/live")({
  loader: async ({ params }) => {
    if (isAuthenticated()) {
      // 실시간 입찰 도중 access-token 만료(401 → 재발급 → 원 요청 재시도) 왕복 지연이
      // 끼는 걸 줄이기 위해, 경매 참여 화면 진입 시 한 번 선제로 갱신해 둔다.
      // 실패해도 무시한다 — 기존 access-token이 여전히 유효할 수 있고, 실제로 만료된
      // 경우엔 요청 인터셉터의 리액티브 재발급이 안전망으로 남아 있다.
      void refreshAccessToken().catch(() => {});
    }
    try {
      return { auction: await getAuctionDetail(params.auctionId) };
    } catch (error) {
      if (error instanceof AxiosError && error.response?.status === 404) {
        throw notFound();
      }
      throw error;
    }
  },
  component: LiveAuctionPage,
});

/** DESIGN.md · live-auction.html — 실 입찰 API 연동, §5.9 최근 6건 + 전체 모달 */
function LiveAuctionPage() {
  const { auction: initialAuction } = Route.useLoaderData();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAuthenticated = useIsAuthenticated();
  const auctionQuery = useQuery({
    queryKey: ["auction-detail", initialAuction.id],
    queryFn: () => getAuctionDetail(initialAuction.id),
    initialData: initialAuction,
    staleTime: 0,
    refetchInterval: (query) =>
      query.state.data?.status === AuctionStatus.LIVE &&
      document.visibilityState !== "hidden"
        ? pollingInterval()
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const auction = auctionQuery.data;
  const minUnit = auction.minBidUnit ?? 0;

  const [realtimeSnapshot, setRealtimeSnapshot] = useState({
    auctionId: auction.id,
    price: auction.currentPrice ?? auction.startPrice ?? 0,
    endsAt: auction.endsAt,
  });
  const [amount, setAmount] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [fail, setFail] = useState<string | null>(null);
  const [allBidsOpen, setAllBidsOpen] = useState(false);
  const [isBidRequestPending, setIsBidRequestPending] = useState(false);

  const snapshotPrice = auction.currentPrice ?? auction.startPrice ?? 0;
  const realtimePrice =
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.price
      : snapshotPrice;
  const realtimeEndsAt =
    realtimeSnapshot.auctionId === auction.id
      ? realtimeSnapshot.endsAt
      : auction.endsAt;
  const currentPrice = Math.max(realtimePrice, snapshotPrice);
  const endsAt = laterEndTime(auction.endsAt, realtimeEndsAt);
  const minNext = currentPrice + minUnit;
  const recommendedAmounts = [
    minNext,
    minNext + minUnit,
    minNext + minUnit * 2,
  ];
  const parsedAmount = (() => {
    const normalized = amount.trim().replaceAll(",", "");
    if (!/^\d+$/.test(normalized)) return null;
    const value = Number(normalized);
    return Number.isSafeInteger(value) && value >= minNext ? value : null;
  })();

  // 실시간 입찰 목록 — 개수 제한 없이 최신순으로 이어서 불러온다(스크롤 페이지네이션).
  // 입찰자별 중복 제거(같은 회원의 최신 입찰만 표시)는 RealtimeBidList가 담당한다.
  const previewBidsQuery = useInfiniteQuery({
    queryKey: ["auction-bids", auction.id, "preview"],
    queryFn: ({ pageParam }: { pageParam?: string }) =>
      getAuctionBids(auction.id, {
        size: REALTIME_BID_PAGE_SIZE,
        cursor: pageParam,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasNext ? lastPage.cursor : undefined,
    staleTime: 0,
    refetchInterval: () =>
      auction.status === AuctionStatus.LIVE &&
      document.visibilityState !== "hidden"
        ? pollingInterval()
        : false,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });
  const previewBidItems =
    previewBidsQuery.data?.pages.flatMap((page) => page.items) ?? [];
  const allBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "all"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_MODAL_SIZE }),
    enabled: allBidsOpen,
  });
  const latestBidId = previewBidItems[0]
    ? Number(previewBidItems[0].id)
    : undefined;

  // 실시간 화면에서 추월당했는지 판단하려면 "내가 최고 입찰자였는지"를 알아야 한다.
  // 페이지를 새로 열었을 때는 입찰 내역에서, 직접 입찰했을 때는 그 결과에서 채운다.
  const myHighestBidRef = useRef<{ bidId: number; price: number } | null>(null);
  // 방금 접수한 입찰 요청의 id. 성공 브로드캐스트가 이 id와 일치하면 "내 요청"으로 판단해
  // 성공 토스트를 띄운다 — 이 화면은 서버로부터 자신의 memberId를 알 방법이 없다.
  const pendingBidRequestIdRef = useRef<number | null>(null);
  const topPreviewBid = previewBidItems[0];
  useEffect(() => {
    if (topPreviewBid?.isMine) {
      myHighestBidRef.current = {
        bidId: Number(topPreviewBid.id),
        price: topPreviewBid.amount,
      };
    }
  }, [topPreviewBid]);

  const refreshSnapshot = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: ["auction-detail", auction.id],
    });
    void queryClient.invalidateQueries({
      queryKey: ["auction-bids", auction.id],
    });
  }, [auction.id, queryClient]);

  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        refreshSnapshot();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () =>
      document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [refreshSnapshot]);

  const applyBidUpdate = useCallback(
    (message: AuctionBidUpdatedMessage) => {
      if (
        message.bidRequestId !== null &&
        message.bidRequestId === pendingBidRequestIdRef.current
      ) {
        pendingBidRequestIdRef.current = null;
        setIsBidRequestPending(false);
        myHighestBidRef.current = {
          bidId: message.latestBid.bidId,
          price: message.currentPrice,
        };
        queryClient.invalidateQueries({
          queryKey: getGetMyPointBalanceQueryKey(),
        });
        toast.success("입찰 성공", {
          description: `${formatWon(message.currentPrice)}에 입찰했습니다.`,
        });
        setAmount("");
      } else {
        const myHighestBid = myHighestBidRef.current;
        if (
          myHighestBid &&
          message.latestBid.bidId !== myHighestBid.bidId &&
          message.currentPrice > myHighestBid.price
        ) {
          myHighestBidRef.current = null;
          toast.warning("추월당했습니다", {
            description: `다른 회원이 ${formatWon(message.currentPrice)}에 입찰했습니다.`,
          });
        }
      }

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

      void queryClient.invalidateQueries({
        queryKey: ["auction-bids", auction.id],
      });
    },
    [auction.id, queryClient],
  );

  const handleBidRequestFailed = useCallback(
    (message: BidRequestFailedMessage) => {
      if (message.bidRequestId !== pendingBidRequestIdRef.current) return;
      pendingBidRequestIdRef.current = null;
      setIsBidRequestPending(false);
      toast.error("입찰 실패", { description: message.failureMessage });
    },
    [],
  );

  useAuctionBidUpdates({
    auctionId: auction.id,
    latestBidId,
    onBidUpdated: applyBidUpdate,
    onBidRequestFailed: handleBidRequestFailed,
    onSubscribed: refreshSnapshot,
  });

  useEffect(() => {
    if (!isBidRequestPending) return;

    const timeoutId = window.setTimeout(() => {
      if (pendingBidRequestIdRef.current === null) return;
      pendingBidRequestIdRef.current = null;
      setIsBidRequestPending(false);
      refreshSnapshot();
      toast.error("입찰 결과 확인 지연", {
        description:
          "처리 결과를 받지 못했습니다. 포인트와 입찰 내역을 확인해 주세요.",
      });
    }, BID_REQUEST_RESULT_TIMEOUT_MILLIS);

    return () => window.clearTimeout(timeoutId);
  }, [isBidRequestPending, refreshSnapshot]);

  const bidMutation = useMutation({
    mutationFn: (bidPrice: number) => createBidRequest(auction.id, bidPrice),
  });

  const onBidClick = () => {
    if (parsedAmount === null) {
      setFail("입찰가는 현재가 + 최소 입찰 단위 이상이어야 합니다.");
      return;
    }
    setConfirmOpen(true);
  };

  const confirmBid = useCallback(() => {
    if (parsedAmount === null) return;
    setConfirmOpen(false);
    bidMutation.mutate(parsedAmount, {
      onSuccess: (placed) => {
        // 접수만 된 상태다 — 실제 처리 결과(성공/실패)는 WebSocket으로 비동기 도착한다.
        pendingBidRequestIdRef.current = placed.bidRequestId;
        setIsBidRequestPending(true);
      },
      onError: (error) => setFail(getBidErrorMessage(error)),
    });
  }, [bidMutation, parsedAmount]);

  const goEnd = useCallback(() => {
    navigate({
      to: "/auctions/$auctionId/end",
      params: { auctionId: auction.id },
    });
  }, [auction.id, navigate]);

  useEffect(() => {
    if (auction.status === AuctionStatus.ENDED) {
      goEnd();
    }
  }, [auction.status, goEnd]);

  return (
    <PageContainer className="grid gap-8 md:grid-cols-[1fr_380px]">
      {/* 좌: 카드 + 현재가/타이머 */}
      <div className="flex flex-col gap-6">
        <div className="grid gap-6 sm:grid-cols-[220px_1fr]">
          <CardThumb
            cardName={auction.cardName}
            grade={auction.grade}
            imageUrl={auction.thumbnailUrl}
          />
          <div className="flex flex-col gap-3">
            <GradeBadge grade={auction.grade} />
            <h1 className="text-2xl font-bold">{auction.cardName}</h1>
            <p className="text-sm text-[var(--color-text-sub)]">
              판매자 · {auction.sellerNickname || "검증된 위탁 상품"}
            </p>
            <div className="mt-2 flex items-end justify-between rounded-[var(--radius-lg)] border border-border bg-card p-5">
              <Price amount={currentPrice} label="현재가" size="lg" />
              <div className="flex flex-col items-end gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  남은 시간
                </span>
                <Countdown to={endsAt} onEnd={goEnd} />
              </div>
            </div>
          </div>
        </div>

        {/* 입찰 입력 */}
        {isAuthenticated ? (
          <div className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <div className="flex items-center justify-between text-sm">
              <span className="text-[var(--color-text-sub)]">
                최소 다음 입찰가
              </span>
              <span className="tabular font-semibold text-foreground">
                {formatWon(minNext)}
              </span>
            </div>
            <div className="flex gap-2">
              <Input
                inputMode="numeric"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder={`${minNext.toLocaleString("ko-KR")} 이상`}
                className="tabular"
              />
              <Button
                onClick={onBidClick}
                disabled={
                  parsedAmount === null ||
                  bidMutation.isPending ||
                  isBidRequestPending
                }
                className="shrink-0"
              >
                {bidMutation.isPending || isBidRequestPending
                  ? "처리 중…"
                  : "입찰하기"}
              </Button>
            </div>
            <div className="flex flex-wrap gap-2" aria-label="추천 입찰 금액">
              {recommendedAmounts.map((recommended, index) => (
                <Button
                  key={`${recommended}-${index}`}
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => setAmount(String(recommended))}
                >
                  {formatWon(recommended)}
                </Button>
              ))}
            </div>
            <p className="text-xs text-[var(--color-text-muted)]">
              입찰은 취소할 수 없습니다.
            </p>
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-[var(--radius-lg)] border border-border bg-card p-5">
            <p className="text-sm text-[var(--color-text-sub)]">
              입찰하려면 로그인이 필요합니다.
            </p>
            <Button asChild size="sm">
              <Link to="/login">로그인</Link>
            </Button>
          </div>
        )}
      </div>

      {/* 우: 실시간 입찰 목록(입찰자별 최신 입찰만, 스크롤로 이어서 로드) + 전체 모달 */}
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
        {previewBidsQuery.isPending ? (
          <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
            불러오는 중입니다.
          </p>
        ) : (
          <RealtimeBidList
            bids={previewBidItems}
            hasNext={previewBidsQuery.hasNextPage}
            isFetchingNextPage={previewBidsQuery.isFetchingNextPage}
            onLoadMore={() => void previewBidsQuery.fetchNextPage()}
          />
        )}
      </aside>

      {/* 전체 입찰 모달 */}
      <Dialog open={allBidsOpen} onOpenChange={setAllBidsOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>전체 입찰 내역</DialogTitle>
          </DialogHeader>
          {allBidsQuery.isPending ? (
            <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
              불러오는 중입니다.
            </p>
          ) : (
            <BidList
              bids={allBidsQuery.data?.items ?? []}
              className="max-h-96 overflow-y-auto"
            />
          )}
        </DialogContent>
      </Dialog>

      {/* 입찰 확인 모달 */}
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>입찰 확인</DialogTitle>
            <DialogDescription>
              입찰은 취소할 수 없습니다. 금액을 확인해 주세요.
            </DialogDescription>
          </DialogHeader>
          <dl className="flex flex-col gap-2 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">현재가</dt>
              <dd className="tabular">{formatWon(currentPrice)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">입찰 금액</dt>
              <dd className="tabular font-bold text-primary">
                {formatWon(parsedAmount ?? 0)}
              </dd>
            </div>
          </dl>
          <DialogFooter>
            <Button
              variant="secondary"
              className="flex-1"
              onClick={() => setConfirmOpen(false)}
            >
              취소
            </Button>
            <Button
              className="flex-1"
              onClick={confirmBid}
              disabled={bidMutation.isPending}
            >
              입찰하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 입찰 실패 모달 */}
      <Dialog open={fail != null} onOpenChange={(o) => !o && setFail(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="text-[var(--color-danger)]">
              입찰 실패
            </DialogTitle>
            <DialogDescription>{fail}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button className="w-full" onClick={() => setFail(null)}>
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
