import { useCallback, useEffect, useRef, useState } from "react";
import { createFileRoute, notFound, useNavigate } from "@tanstack/react-router";
import { AlertTriangle, Radio } from "lucide-react";
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
import { auctionDetails, bidsByAuction, currentUser } from "@/lib/mock/data";
import type { Bid } from "@/lib/types";
import { formatWon, maskNickname } from "@/lib/format";

export const Route = createFileRoute("/_buyer/auctions/$auctionId/live")({
  loader: ({ params }) => {
    const auction = auctionDetails[params.auctionId];
    if (!auction) throw notFound();
    return { auction };
  },
  component: LiveAuctionPage,
});

let bidSeq = 1000;

/** DESIGN.md · live-auction.html + bid-confirm / all-bids / bid-fail 모달 */
function LiveAuctionPage() {
  const { auction } = Route.useLoaderData();
  const navigate = useNavigate();
  const minUnit = auction.minBidUnit ?? 0;

  const [bids, setBids] = useState<Bid[]>(
    () => bidsByAuction[auction.id] ?? [],
  );
  const [currentPrice, setCurrentPrice] = useState(
    auction.currentPrice ?? auction.startPrice ?? 0,
  );
  const [amount, setAmount] = useState<string>("");
  const [overtaken, setOvertaken] = useState(false);

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [allBidsOpen, setAllBidsOpen] = useState(false);
  const [fail, setFail] = useState<string | null>(null);

  const minNext = currentPrice + minUnit;
  const iAmTop = bids[0]?.isMine ?? false;

  // 경쟁 입찰 시뮬레이션 (실시간 추월)
  const iAmTopRef = useRef(iAmTop);
  useEffect(() => {
    iAmTopRef.current = iAmTop;
  }, [iAmTop]);
  useEffect(() => {
    const id = window.setInterval(() => {
      if (Math.random() > 0.55) {
        setCurrentPrice((prev) => {
          const next = prev + minUnit;
          setBids((b) => [
            {
              id: `sim_${bidSeq++}`,
              maskedNickname: maskNickname("collector" + (bidSeq % 90)),
              amount: next,
              createdAt: new Date().toISOString(),
            },
            ...b,
          ]);
          return next;
        });
        if (iAmTopRef.current) setOvertaken(true);
      }
    }, 7000);
    return () => window.clearInterval(id);
  }, [minUnit]);

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
    if (value < currentPrice + minUnit) {
      setFail("이미 더 높은 입찰이 등록되었습니다.");
      return;
    }
    setBids((b) => [
      {
        id: `me_${bidSeq++}`,
        maskedNickname: currentUser.nickname,
        amount: value,
        createdAt: new Date().toISOString(),
        isMine: true,
      },
      ...b,
    ]);
    setCurrentPrice(value);
    setOvertaken(false);
    setAmount("");
  }, [amount, currentPrice, minUnit]);

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
          <CardThumb cardName={auction.cardName} grade={auction.grade} />
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <GradeBadge grade={auction.grade} />
              <span className="inline-flex items-center gap-1 text-xs text-[var(--color-success)]">
                <Radio className="size-3.5" /> 실시간 연결됨
              </span>
            </div>
            <h1 className="text-2xl font-bold">{auction.cardName}</h1>
            <p className="text-sm text-[var(--color-text-sub)]">
              판매자 · {auction.sellerNickname}
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

        {/* 추월 알림 */}
        {overtaken && (
          <div className="flex items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-danger)] bg-[color-mix(in_srgb,var(--color-danger)_12%,transparent)] px-4 py-3 text-sm text-[var(--color-danger)]">
            <AlertTriangle className="size-4" />
            추월당했습니다. 다시 입찰하려면 {formatWon(minNext)} 이상
            입력하세요.
          </div>
        )}

        {/* 입찰 입력 */}
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
            <Button onClick={onBidClick} className="shrink-0">
              입찰하기
            </Button>
          </div>
          <p className="text-xs text-[var(--color-text-muted)]">
            입찰은 취소할 수 없습니다.
          </p>
        </div>
      </div>

      {/* 우: 실시간 입찰 내역 (최근 6건 + 전체) */}
      <aside className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">입찰 내역</h2>
          <button
            onClick={() => setAllBidsOpen(true)}
            className="text-xs text-primary hover:underline"
          >
            전체
          </button>
        </div>
        <BidList bids={bids.slice(0, 6)} />
      </aside>

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
            <Button className="flex-1" onClick={confirmBid}>
              입찰하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 전체 입찰 모달 */}
      <Dialog open={allBidsOpen} onOpenChange={setAllBidsOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>전체 입찰 내역</DialogTitle>
            <DialogDescription>
              닉네임은 마스킹되며 본인 입찰은 “나”로 표시됩니다.
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[50vh] overflow-y-auto pr-1">
            <BidList bids={bids} />
          </div>
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
          <dl className="flex flex-col gap-2 rounded-[var(--radius-md)] bg-[var(--color-surface-2)] p-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">현재가</dt>
              <dd className="tabular">{formatWon(currentPrice)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-[var(--color-text-sub)]">최소 다음 입찰가</dt>
              <dd className="tabular font-bold text-foreground">
                {formatWon(minNext)}
              </dd>
            </div>
          </dl>
          <DialogFooter>
            <Button className="w-full" onClick={() => setFail(null)}>
              다시 입찰
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  );
}
