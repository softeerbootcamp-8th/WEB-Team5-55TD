import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { Plus } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getMyConsignments } from "@/api/consignments";
import type { ApiConsignmentStatus, ConsignmentSummary } from "@/api/consignments";
import { ProductStatus } from "@/lib/types";
import { PRODUCT_STATUS_META } from "@/lib/status";

type ProductListTab = "registerable" | "upcoming" | "ongoing" | "sold";
const PRODUCT_LIST_TABS: ProductListTab[] = [
  "registerable",
  "upcoming",
  "ongoing",
  "sold",
];

export const Route = createFileRoute("/seller/products/")({
  validateSearch: (
    search: Record<string, unknown>,
  ): { tab?: ProductListTab } => ({
    tab: PRODUCT_LIST_TABS.includes(search.tab as ProductListTab)
      ? (search.tab as ProductListTab)
      : undefined,
  }),
  component: ProductListPage,
});

async function fetchAllByStatus(status: ApiConsignmentStatus) {
  const items: ConsignmentSummary[] = [];
  let cursor: number | undefined;
  do {
    const page = await getMyConsignments({ status, cursor });
    items.push(...page.items);
    cursor = page.hasNext ? page.cursor : undefined;
  } while (cursor !== undefined);
  return items;
}

async function fetchByStatuses(statuses: ApiConsignmentStatus[]) {
  const pages = await Promise.all(statuses.map(fetchAllByStatus));
  return pages.flat();
}

/** DESIGN.md · product list.html — 등록 가능 / 경매 예정 / 판매 완료 (검수·반려 없음) */
function ProductListPage() {
  const { tab } = Route.useSearch();
  const navigate = useNavigate();
  const activeTab = tab ?? "registerable";

  const registerableQuery = useQuery({
    queryKey: ["consignments", "my", "REGISTERABLE"],
    // 유찰(재신청 가능)도 이제 REGISTERABLE 하나로 합쳐져 함께 내려온다.
    queryFn: () => fetchByStatuses(["REGISTERABLE"]),
  });
  // 경매 예정/진행 중은 모두 IN_AUCTION이라 한 번에 조회하고, 연결된 경매 상태로 화면에서 나눈다.
  const inAuctionQuery = useQuery({
    queryKey: ["consignments", "my", "IN_AUCTION"],
    queryFn: () => fetchByStatuses(["IN_AUCTION"]),
  });
  const soldQuery = useQuery({
    queryKey: ["consignments", "my", "SOLD"],
    queryFn: () => fetchByStatuses(["SOLD"]),
  });

  const registerable = registerableQuery.data ?? [];
  const inAuction = inAuctionQuery.data ?? [];
  const upcoming = inAuction.filter(
    (item) => item.status === ProductStatus.AUCTION_UPCOMING,
  );
  const ongoing = inAuction.filter(
    (item) => item.status === ProductStatus.AUCTION_LIVE,
  );
  const sold = soldQuery.data ?? [];

  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">상품 목록</h1>
          <p className="text-sm text-[var(--color-text-sub)]">
            서비스는 검수를 제공하지 않으며, 셀러가 인증서로 자가 인증합니다.
          </p>
        </div>
        <Button asChild className="self-start">
          <Link to="/seller/register">
            <Plus /> 상품 등록
          </Link>
        </Button>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={(value) =>
          navigate({
            to: "/seller/products",
            search: { tab: value as ProductListTab },
            replace: true,
          })
        }
      >
        <TabsList>
          <TabsTrigger value="registerable">등록 가능</TabsTrigger>
          <TabsTrigger value="upcoming">경매 예정</TabsTrigger>
          <TabsTrigger value="ongoing">경매 진행 중</TabsTrigger>
          <TabsTrigger value="sold">판매 완료</TabsTrigger>
        </TabsList>
        <TabsContent value="registerable">
          <ProductGrid
            items={registerable}
            isLoading={registerableQuery.isPending}
            isError={registerableQuery.isError}
          />
        </TabsContent>
        <TabsContent value="upcoming">
          <ProductGrid
            items={upcoming}
            isLoading={inAuctionQuery.isPending}
            isError={inAuctionQuery.isError}
          />
        </TabsContent>
        <TabsContent value="ongoing">
          <ProductGrid
            items={ongoing}
            isLoading={inAuctionQuery.isPending}
            isError={inAuctionQuery.isError}
          />
        </TabsContent>
        <TabsContent value="sold">
          <ProductGrid
            items={sold}
            isLoading={soldQuery.isPending}
            isError={soldQuery.isError}
          />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function ProductGrid({
  items,
  isLoading,
  isError,
}: {
  items: ConsignmentSummary[];
  isLoading?: boolean;
  isError?: boolean;
}) {
  if (isLoading) return null;
  if (isError) {
    return (
      <EmptyState
        title="상품을 불러오지 못했습니다."
        description="잠시 후 다시 시도해 주세요."
      />
    );
  }
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
              <CardThumb
                cardName={p.cardName}
                grade={p.grade}
                imageUrl={p.thumbnailUrl}
              />
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
                인증서 {p.grade?.serial}
              </span>
              <Button size="sm" variant="secondary" asChild className="mt-1">
                {p.auctionId && p.status !== ProductStatus.REAPPLICABLE ? (
                  <Link
                    to="/seller/auctions/$auctionId"
                    params={{ auctionId: p.auctionId }}
                  >
                    {p.status === ProductStatus.SOLD ? "낙찰 상세" : "경매 상세"}
                  </Link>
                ) : (
                  <Link
                    to="/seller/products/$productId"
                    params={{ productId: p.id }}
                  >
                    상세 보기
                  </Link>
                )}
              </Button>
            </div>
          </div>
        );
      })}
    </div>
  );
}
