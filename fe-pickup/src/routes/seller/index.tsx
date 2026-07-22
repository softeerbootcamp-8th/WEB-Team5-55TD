import { createFileRoute, Link } from "@tanstack/react-router";
import { Plus } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { SectionHeader } from "@/components/domain/section-header";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { products, sales } from "@/lib/mock/data";
import { ProductStatus } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";
import { formatWon } from "@/lib/format";

export const Route = createFileRoute("/seller/")({
  component: SellerHome,
});

/** DESIGN.md · seller home.html — 요약 통계 + 진행 중 경매 + 최근 낙찰 */
function SellerHome() {
  const count = (s: ProductStatus) =>
    products.filter((p) => p.status === s).length;

  const stats = [
    { label: "등록 상품", value: products.length },
    { label: "경매 예정", value: count(ProductStatus.AUCTION_UPCOMING) },
    { label: "진행 중", value: count(ProductStatus.AUCTION_LIVE) },
    { label: "판매 완료", value: count(ProductStatus.SOLD) },
  ];

  const liveProducts = products.filter(
    (p) => p.status === ProductStatus.AUCTION_LIVE,
  );
  const recentSold = sales.filter((s) => s.status === ProductStatus.SOLD);

  return (
    <PageContainer className="flex flex-col gap-10">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">셀러 홈</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            내 상품과 경매 현황을 한눈에 확인하세요.
          </p>
        </div>
        <Button asChild>
          <Link to="/seller/register">
            <Plus /> 카드 등록
          </Link>
        </Button>
      </div>

      {/* 통계 타일 */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {stats.map((s) => (
          <div
            key={s.label}
            className="flex flex-col gap-1 rounded-[var(--radius-lg)] border border-border bg-card p-5"
          >
            <span className="text-xs text-[var(--color-text-muted)]">
              {s.label}
            </span>
            <span className="tabular text-2xl font-bold">{s.value}</span>
          </div>
        ))}
      </div>

      {/* 진행 중 경매 */}
      <section className="flex flex-col gap-4">
        <SectionHeader title="진행 중인 경매" />
        {liveProducts.length ? (
          <ul className="flex flex-col gap-3">
            {liveProducts.map((p) => (
              <li
                key={p.id}
                className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
              >
                <CardThumb
                  cardName={p.cardName}
                  aspect="aspect-square"
                  className="w-14"
                />
                <div className="flex flex-1 flex-col gap-1">
                  <div className="flex items-center gap-2">
                    <GradeBadge grade={p.grade} />
                    <Badge variant={PRODUCT_STATUS_META[p.status].variant}>
                      {PRODUCT_STATUS_META[p.status].label}
                    </Badge>
                  </div>
                  <span className="text-sm font-semibold">{p.cardName}</span>
                </div>
                <Button size="sm" variant="secondary" asChild>
                  <Link
                    to="/seller/auctions/$auctionId"
                    params={{ auctionId: p.id }}
                  >
                    모니터링
                  </Link>
                </Button>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-[var(--color-text-muted)]">
            진행 중인 경매가 없습니다.
          </p>
        )}
      </section>

      {/* 최근 낙찰 상품 */}
      <section className="flex flex-col gap-4">
        <SectionHeader
          title="최근 낙찰 상품"
          action={
            <Link
              to="/seller/sales"
              className="text-sm text-[var(--color-text-sub)] hover:text-primary"
            >
              판매 내역 ›
            </Link>
          }
        />
        <ul className="flex flex-col gap-3">
          {recentSold.map((s) => (
            <li
              key={s.id}
              className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
            >
              <CardThumb
                cardName={s.cardName}
                aspect="aspect-square"
                className="w-14"
              />
              <div className="flex flex-1 flex-col gap-1">
                <GradeBadge grade={s.grade} />
                <span className="text-sm font-semibold">{s.cardName}</span>
              </div>
              <span className="tabular text-sm font-semibold text-[var(--color-price)]">
                {formatWon(s.finalPrice)}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </PageContainer>
  );
}
