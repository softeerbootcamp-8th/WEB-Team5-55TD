import { useState } from "react";
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { AxiosError } from "axios";
import { ChevronLeft, Lock, Radio } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { StatusBadge } from "@/components/domain/status-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
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
import { useNickname } from "@/lib/auth";

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

  // 실시간 경매 화면(live.tsx)과 같은 최근 N건 미리보기 + 전체보기 모달 구조를 쓴다.
  // 다만 이 화면은 종료된 경매도 보여주는 읽기 전용 모니터링이라 폴링·웹소켓 갱신은
  // 필요 없다고 판단해 넣지 않았다 — 한 번 조회하면 충분하다.
  const previewBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "preview"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_PREVIEW_SIZE }),
    enabled: isOwner,
  });
  const allBidsQuery = useQuery({
    queryKey: ["auction-bids", auction.id, "all"],
    queryFn: () => getAuctionBids(auction.id, { size: BID_MODAL_SIZE }),
    enabled: isOwner && allBidsOpen,
  });
  // 입찰 횟수는 모달을 한 번이라도 열어 더 큰 페이지를 받아온 뒤에는 그 값을 쓰고,
  // 그 전에는 미리보기(최근 6건) 결과를 쓴다. 두 경우 모두 hasNext가 true면
  // "6+"처럼 표시해 잘려서 안 보이는 입찰이 있다는 걸 숨기지 않는다.
  const bidCountQuery = allBidsQuery.data ? allBidsQuery : previewBidsQuery;
  const bidCount = bidCountQuery.data
    ? `${bidCountQuery.data.items.length}${bidCountQuery.data.hasNext ? "+" : ""}`
    : undefined;

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
  const currentPrice = auction.currentPrice ?? auction.startPrice;

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
              <span className="inline-flex items-center gap-1 text-xs text-[var(--color-success)]">
                <Radio className="size-3.5" /> 실시간 모니터링
              </span>
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
        {previewBidsQuery.isError ? (
          <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
            입찰 내역을 불러오지 못했습니다.
          </p>
        ) : previewBidsQuery.isPending ? (
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
          {allBidsQuery.isError ? (
            <p className="py-8 text-center text-sm text-[var(--color-text-muted)]">
              입찰 내역을 불러오지 못했습니다.
            </p>
          ) : allBidsQuery.isPending ? (
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
    </PageContainer>
  );
}
