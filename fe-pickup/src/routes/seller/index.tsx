import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { SectionHeader } from "@/components/domain/section-header";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getMyConsignments } from "@/api/consignments";
import { getMySalesHistory } from "@/api/sales";
import { products } from "@/lib/mock/data";
import { ProductStatus } from "@/lib/types";
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

  const {
    data: liveProducts,
    isPending: isLiveLoading,
    isError: isLiveError,
  } = useQuery({
    queryKey: ["consignments", "my", "AUCTION_ONGOING"],
    queryFn: () =>
      getMyConsignments({ status: "AUCTION_ONGOING" }).then((r) => r.items),
  });

  const {
    data: recentSales,
    isPending: isSalesLoading,
    isError: isSalesError,
  } = useQuery({
    queryKey: ["sales", "my", "recent"],
    queryFn: () => getMySalesHistory({ size: 3 }).then((r) => r.items),
  });

  return (
    <PageContainer className="flex flex-col gap-10">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">셀러 홈</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            내 상품과 경매 현황을 한눈에 확인하세요.
          </p>
        </div>
        <Button asChild className="self-start">
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
        <SectionHeader
          title="진행 중인 경매"
          action={
            <Link
              to="/seller/products"
              search={{ tab: "ongoing" }}
              className="text-sm text-[var(--color-text-sub)] hover:text-primary"
            >
              더보기 ›
            </Link>
          }
        />
        {isLiveLoading ? null : isLiveError ? (
          <p className="text-sm text-[var(--color-text-muted)]">
            진행 중인 경매를 불러오지 못했습니다.
          </p>
        ) : liveProducts && liveProducts.length > 0 ? (
          <ul className="flex flex-col gap-3">
            {liveProducts.slice(0, 3).map((p) => (
              <li
                key={p.id}
                className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
              >
                <CardThumb
                  cardName={p.cardName}
                  imageUrl={p.thumbnailUrl}
                  aspect="aspect-square"
                  className="w-14"
                />
                <div className="flex flex-1 flex-col gap-1">
                  <GradeBadge grade={p.grade} />
                  <span className="text-sm font-semibold">{p.cardName}</span>
                </div>
                <Button size="sm" variant="secondary" asChild>
                  {p.auctionId ? (
                    <Link
                      to="/seller/auctions/$auctionId"
                      params={{ auctionId: p.auctionId }}
                    >
                      모니터링
                    </Link>
                  ) : (
                    <Link
                      to="/seller/products/$productId"
                      params={{ productId: p.id }}
                    >
                      상품 보기
                    </Link>
                  )}
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
        {isSalesLoading ? null : isSalesError ? (
          <p className="text-sm text-[var(--color-text-muted)]">
            최근 낙찰 상품을 불러오지 못했습니다.
          </p>
        ) : recentSales && recentSales.length > 0 ? (
          <ul className="flex flex-col gap-3">
            {recentSales.map((s) => {
              const won = s.resultType === "WON";
              return (
                <li
                  key={s.auctionId}
                  className="flex items-center gap-4 rounded-[var(--radius-lg)] border border-border bg-card p-4"
                >
                  <CardThumb
                    cardName={s.cardName}
                    imageUrl={s.thumbnailUrl}
                    aspect="aspect-square"
                    className="w-14"
                  />
                  <div className="flex flex-1 flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <GradeBadge grade={s.grade} />
                      <Badge variant={won ? "success" : "muted"}>
                        {won ? "낙찰" : "유찰"}
                      </Badge>
                    </div>
                    <span className="text-sm font-semibold">{s.cardName}</span>
                  </div>
                  <span
                    className={
                      "tabular text-sm font-semibold " +
                      (won
                        ? "text-[var(--color-price)]"
                        : "text-[var(--color-text-muted)]")
                    }
                  >
                    {won ? formatWon(s.finalPrice) : "유찰"}
                  </span>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="text-sm text-[var(--color-text-muted)]">
            최근 낙찰 상품이 없습니다.
          </p>
        )}
      </section>
    </PageContainer>
  );
}
