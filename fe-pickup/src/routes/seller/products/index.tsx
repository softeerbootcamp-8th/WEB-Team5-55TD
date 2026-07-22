import { createFileRoute, Link } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { products } from "@/lib/mock/data";
import { ProductStatus } from "@/lib/types";
import type { Product } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";

export const Route = createFileRoute("/seller/products/")({
  component: ProductListPage,
});

/** DESIGN.md · product list.html — 등록 가능 / 경매 예정 / 판매 완료 (검수·반려 없음) */
function ProductListPage() {
  const registerable = products.filter(
    (p) => p.status === ProductStatus.REGISTERABLE,
  );
  const upcoming = products.filter(
    (p) =>
      p.status === ProductStatus.AUCTION_UPCOMING ||
      p.status === ProductStatus.AUCTION_LIVE,
  );
  const sold = products.filter((p) => p.status === ProductStatus.SOLD);

  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">상품 목록</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            서비스는 검수를 제공하지 않으며, 셀러가 인증서로 자가 인증합니다.
          </p>
        </div>
        <Button asChild>
          <Link to="/seller/register">카드 등록</Link>
        </Button>
      </div>

      <Tabs defaultValue="registerable">
        <TabsList>
          <TabsTrigger value="registerable">등록 가능</TabsTrigger>
          <TabsTrigger value="upcoming">경매 예정</TabsTrigger>
          <TabsTrigger value="sold">판매 완료</TabsTrigger>
        </TabsList>
        <TabsContent value="registerable">
          <ProductGrid items={registerable} />
        </TabsContent>
        <TabsContent value="upcoming">
          <ProductGrid items={upcoming} />
        </TabsContent>
        <TabsContent value="sold">
          <ProductGrid items={sold} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function ProductGrid({ items }: { items: Product[] }) {
  if (items.length === 0) {
    return <EmptyState title="해당 상태의 상품이 없습니다." />;
  }
  return (
    <div className="grid grid-cols-2 gap-5 md:grid-cols-3">
      {items.map((p) => {
        const meta = PRODUCT_STATUS_META[p.status];
        return (
          <div
            key={p.id}
            className="flex flex-col gap-3 rounded-[var(--radius-lg)] border border-border bg-card p-3"
          >
            <div className="relative">
              <CardThumb cardName={p.cardName} grade={p.grade} />
              <Badge variant={meta.variant} className="absolute top-2 left-2">
                {meta.label}
              </Badge>
            </div>
            <div className="flex flex-col gap-2 px-1">
              <GradeBadge grade={p.grade} />
              <h3 className="line-clamp-1 text-sm font-semibold">
                {p.cardName}
              </h3>
              <span className="tabular text-xs text-[var(--color-text-muted)]">
                인증서 {p.grade.serial}
              </span>
              <Button size="sm" variant="secondary" asChild className="mt-1">
                <Link
                  to="/seller/products/$productId"
                  params={{ productId: p.id }}
                >
                  상세 보기
                </Link>
              </Button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
