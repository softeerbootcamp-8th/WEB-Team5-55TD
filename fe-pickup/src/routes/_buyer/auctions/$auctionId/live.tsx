import { useCallback, useState } from "react";
import {
  createFileRoute,
  Link,
  notFound,
  useNavigate,
} from "@tanstack/react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { BidList } from "@/components/domain/bid-list";
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
import { getAuctionBids, getBidErrorMessage, placeBid } from "@/api/bids";
import { getGetMyPointBalanceQueryKey } from "@/api/generated/member/member";
import { useIsAuthenticated } from "@/lib/auth";
import { formatWon } from "@/lib/format";

const BID_PREVIEW_SIZE = 6;
const BID_MODAL_SIZE = 100;

export const Route = createFileRoute("/_buyer/auctions/$auctionId/live")({
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
  component: LiveAuctionPage,
});

/** DESIGN.md · live-auction.html — 실 입찰 API 연동, §5.9 최근 6건 + 전체 모달 */
function LiveAuctionPage() {
  const { auction } = Route.useLoaderData();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const isAuthenticated = useIsAuthenticated();
  const minUnit = auction.minBidUnit ?? 0;

  const [currentPrice, setCurrentPrice] = useState(
    auction.currentPrice ?? auction.startPrice ?? 0,
  );
  const [amount, setAmount] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [fail, setFail] = useState<string | null>(null);
  const [allBidsOpen, setAllBidsOpen] = useState(false);

  const minNext = currentPrice + minUnit;

  const previewBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "preview"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_PREVIEW_SIZE }),
  });
  const allBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "all"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_MODAL_SIZE }),
    enabled: allBidsOpen,
  });

  const bidMutation = useMutation({
    mutationFn: (bidPrice: number) => placeBid(auction.id, bidPrice),
  });

  const onBidClick = () => {
    const value = Number(amount.replace(/[^0-9]/g, ""));
    if (!value || value < minNext) {
      setFail("입찰가는 현재가 + 최소 입찰 단위 이상이어야 합니다.");
      return;
    }
    setConfirmOpen(true);
  };

  const confirmBid = useCallback(() => {
    const value = Number(amount.replace(/[^0-9]/g, ""));
    setConfirmOpen(false);
    bidMutation.mutate(value, {
      onSuccess: (placed) => {
        queryClient.invalidateQueries({
          queryKey: ["auction-bids", auction.id],
        });
        queryClient.invalidateQueries({
          queryKey: getGetMyPointBalanceQueryKey(),
        });
        setCurrentPrice(placed.bidPrice);
        setAmount("");
      },
      onError: (error) => setFail(getBidErrorMessage(error)),
    });
  }, [amount, auction.id, bidMutation, queryClient]);

  const goEnd = useCallback(() => {
    navigate({
      to: "/auctions/$auctionId/end",
      params: { auctionId: auction.id },
    });
  }, [auction.id, navigate]);

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
                <Countdown to={auction.endsAt} onEnd={goEnd} />
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
                disabled={bidMutation.isPending}
                className="shrink-0"
              >
                {bidMutation.isPending ? "입찰 중…" : "입찰하기"}
              </Button>
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

      {/* 우: 입찰 내역 (최근 6건 + 전체 모달) */}
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
          <BidList bids={previewBidsQuery.data?.items ?? []} />
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
                {formatWon(Number(amount.replace(/[^0-9]/g, "")) || 0)}
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
