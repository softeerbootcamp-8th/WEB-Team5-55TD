import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { ChevronLeft, Lock, Radio } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Price } from "@/components/domain/price";
import { Countdown } from "@/components/domain/countdown";
import { BidList } from "@/components/domain/bid-list";
import { Badge } from "@/components/ui/badge";
import { products } from "@/lib/mock/data";
import type { Bid } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";

export const Route = createFileRoute("/seller/auctions/$auctionId")({
  loader: ({ params }) => {
    const product = products.find((p) => p.id === params.auctionId);
    if (!product) throw notFound();
    return { product };
  },
  component: SellerAuctionPage,
});

// 모니터링용 샘플 입찰 (읽기 전용)
const now = Date.now();
const SAMPLE_BIDS: Bid[] = [
  {
    id: "m1",
    maskedNickname: "col***88",
    amount: 620_000,
    createdAt: new Date(now - 40_000).toISOString(),
  },
  {
    id: "m2",
    maskedNickname: "psa***01",
    amount: 590_000,
    createdAt: new Date(now - 120_000).toISOString(),
  },
  {
    id: "m3",
    maskedNickname: "kan***12",
    amount: 560_000,
    createdAt: new Date(now - 300_000).toISOString(),
  },
  {
    id: "m4",
    maskedNickname: "bid***23",
    amount: 530_000,
    createdAt: new Date(now - 600_000).toISOString(),
  },
];

/** DESIGN.md · seller-auction.html — 입찰 기능 제외, 모니터링 전용 */
function SellerAuctionPage() {
  const { product } = Route.useLoaderData();
  const meta = PRODUCT_STATUS_META[product.status];
  const currentPrice = SAMPLE_BIDS[0].amount;
  const endsAt = new Date(now + 27 * 60_000).toISOString();

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
          <CardThumb cardName={product.cardName} grade={product.grade} />
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2">
              <GradeBadge grade={product.grade} />
              <Badge variant={meta.variant}>{meta.label}</Badge>
              <span className="inline-flex items-center gap-1 text-xs text-[var(--color-success)]">
                <Radio className="size-3.5" /> 실시간 모니터링
              </span>
            </div>
            <h1 className="text-2xl font-bold">{product.cardName}</h1>

            <div className="mt-2 grid grid-cols-3 gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
              <Price amount={currentPrice} label="현재가" size="md" />
              <div className="flex flex-col gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  입찰 횟수
                </span>
                <span className="tabular text-lg font-bold">
                  {SAMPLE_BIDS.length}
                </span>
              </div>
              <div className="flex flex-col items-start gap-0.5">
                <span className="text-xs text-[var(--color-text-muted)]">
                  남은 시간
                </span>
                <Countdown to={endsAt} className="text-base" />
              </div>
            </div>

            <p className="inline-flex items-center gap-1.5 text-xs text-[var(--color-text-muted)]">
              <Lock className="size-3.5" /> 진행 중인 경매는 수정·취소할 수
              없습니다.
            </p>
          </div>
        </div>
      </div>

      {/* 입찰 내역 (읽기 전용) */}
      <aside className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-5">
        <h2 className="text-base font-semibold">입찰 내역</h2>
        <BidList bids={SAMPLE_BIDS} />
      </aside>
    </PageContainer>
  );
}
