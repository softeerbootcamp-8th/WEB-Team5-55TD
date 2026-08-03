import { createFileRoute, useNavigate } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getMySalesHistory } from "@/api/sales";
import type { ApiSalesResultType, SalesHistoryItem } from "@/api/sales";
import { formatWon } from "@/lib/format";

type SalesTab = "all" | "won" | "passed";
const SALES_TABS: SalesTab[] = ["all", "won", "passed"];
const TAB_STATUS: Record<SalesTab, ApiSalesResultType | undefined> = {
  all: undefined,
  won: "WON",
  passed: "PASSED",
};

export const Route = createFileRoute("/seller/sales")({
  validateSearch: (search: Record<string, unknown>): { tab?: SalesTab } => ({
    tab: SALES_TABS.includes(search.tab as SalesTab)
      ? (search.tab as SalesTab)
      : undefined,
  }),
  component: SalesPage,
});

async function fetchAllSales(status?: ApiSalesResultType) {
  const items: SalesHistoryItem[] = [];
  let cursor: string | undefined;
  do {
    const page = await getMySalesHistory({ status, cursor });
    items.push(...page.items);
    cursor = page.hasNext ? page.cursor : undefined;
  } while (cursor !== undefined);
  return items;
}

/** DESIGN.md · sales.html — 판매 내역: 전체 / 낙찰 / 유찰 */
function SalesPage() {
  const { tab } = Route.useSearch();
  const navigate = useNavigate();
  const activeTab = tab ?? "all";

  const allQuery = useQuery({
    queryKey: ["sales", "my", TAB_STATUS.all],
    queryFn: () => fetchAllSales(TAB_STATUS.all),
  });
  const wonQuery = useQuery({
    queryKey: ["sales", "my", TAB_STATUS.won],
    queryFn: () => fetchAllSales(TAB_STATUS.won),
  });
  const passedQuery = useQuery({
    queryKey: ["sales", "my", TAB_STATUS.passed],
    queryFn: () => fetchAllSales(TAB_STATUS.passed),
  });

  return (
    <PageContainer className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">판매 내역</h1>

      <Tabs
        value={activeTab}
        onValueChange={(value) =>
          navigate({
            to: "/seller/sales",
            search: { tab: value as SalesTab },
            replace: true,
          })
        }
      >
        <TabsList>
          <TabsTrigger value="all">전체</TabsTrigger>
          <TabsTrigger value="won">낙찰</TabsTrigger>
          <TabsTrigger value="passed">유찰</TabsTrigger>
        </TabsList>
        <TabsContent value="all">
          <SalesTable
            items={allQuery.data ?? []}
            isLoading={allQuery.isPending}
            isError={allQuery.isError}
          />
        </TabsContent>
        <TabsContent value="won">
          <SalesTable
            items={wonQuery.data ?? []}
            isLoading={wonQuery.isPending}
            isError={wonQuery.isError}
          />
        </TabsContent>
        <TabsContent value="passed">
          <SalesTable
            items={passedQuery.data ?? []}
            isLoading={passedQuery.isPending}
            isError={passedQuery.isError}
          />
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function SalesTable({
  items,
  isLoading,
  isError,
}: {
  items: SalesHistoryItem[];
  isLoading?: boolean;
  isError?: boolean;
}) {
  if (isLoading) return null;
  if (isError) {
    return (
      <EmptyState
        title="판매 내역을 불러오지 못했습니다."
        description="잠시 후 다시 시도해 주세요."
      />
    );
  }
  if (items.length === 0) {
    return <EmptyState title="판매 내역이 없습니다." />;
  }
  return (
    <ul className="flex flex-col gap-3">
      {items.map((s) => {
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
  );
}
