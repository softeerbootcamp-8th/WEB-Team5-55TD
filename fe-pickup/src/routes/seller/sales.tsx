import { createFileRoute } from "@tanstack/react-router";
import { PageContainer } from "@/components/layout/page";
import { CardThumb } from "@/components/domain/card-thumb";
import { GradeBadge } from "@/components/domain/grade-badge";
import { EmptyState } from "@/components/domain/section-header";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { sales } from "@/lib/mock/data";
import type { SaleItem } from "@/lib/types";
import { ProductStatus } from "@/lib/types";
import { SETTLEMENT_STATUS_META } from "@/lib/status";
import { formatWon } from "@/lib/format";

export const Route = createFileRoute("/seller/sales")({
  component: SalesPage,
});

/** DESIGN.md · sales.html — 판매 내역 / 정산 예정 */
function SalesPage() {
  const settlements = sales; // 정산 탭도 동일 목록, 정산 상태만 표시

  return (
    <PageContainer className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold">판매 내역</h1>

      <Tabs defaultValue="sales">
        <TabsList>
          <TabsTrigger value="sales">판매 내역</TabsTrigger>
          <TabsTrigger value="settlement">정산</TabsTrigger>
        </TabsList>

        <TabsContent value="sales">
          <SalesTable items={sales} />
        </TabsContent>

        <TabsContent value="settlement">
          {settlements.length ? (
            <ul className="flex flex-col gap-3">
              {settlements.map((s) => {
                const meta = SETTLEMENT_STATUS_META[s.settlement];
                return (
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
                      <span className="text-sm font-semibold">
                        {s.cardName}
                      </span>
                    </div>
                    <Badge variant={meta.variant}>{meta.label}</Badge>
                  </li>
                );
              })}
            </ul>
          ) : (
            <EmptyState title="정산 내역이 없습니다." />
          )}
        </TabsContent>
      </Tabs>
    </PageContainer>
  );
}

function SalesTable({ items }: { items: SaleItem[] }) {
  if (items.length === 0) {
    return <EmptyState title="판매 내역이 없습니다." />;
  }
  return (
    <ul className="flex flex-col gap-3">
      {items.map((s) => {
        const won = s.status === ProductStatus.SOLD;
        return (
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
