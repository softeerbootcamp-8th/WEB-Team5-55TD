import { useMemo, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { ChevronDown, Search } from "lucide-react";
import { PageContainer } from "@/components/layout/page";
import { AuctionCard } from "@/components/domain/auction-card";
import { EmptyState } from "@/components/domain/section-header";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { auctionSummaries } from "@/lib/mock/data";
import { AuctionStatus } from "@/lib/types";
import type { AuctionSummary } from "@/lib/types";

export const Route = createFileRoute("/_buyer/auctions/")({
  component: AuctionListPage,
});

type Filter = "LIVE" | "UPCOMING" | "ENDED";
type Sort = "popular" | "priceAsc" | "priceDesc" | "endingSoon";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  priceAsc: "가격 낮은순",
  priceDesc: "가격 높은순",
  endingSoon: "종료 임박순",
};

function priceOf(a: AuctionSummary) {
  return a.currentPrice ?? a.startPrice ?? 0;
}

/** DESIGN.md · auction list.html — 검색 · 정렬 · 진행/예정/종료 필터 */
function AuctionListPage() {
  const [filter, setFilter] = useState<Filter>("LIVE");
  const [sort, setSort] = useState<Sort>("popular");
  const [query, setQuery] = useState("");

  const list = useMemo(() => {
    let items = auctionSummaries.filter((a) => a.status === filter);
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      items = items.filter((a) => a.cardName.toLowerCase().includes(q));
    }
    const sorted = [...items];
    switch (sort) {
      case "priceAsc":
        sorted.sort((a, b) => priceOf(a) - priceOf(b));
        break;
      case "priceDesc":
        sorted.sort((a, b) => priceOf(b) - priceOf(a));
        break;
      case "endingSoon":
        sorted.sort(
          (a, b) =>
            new Date(a.endsAt ?? a.startsAt ?? 0).getTime() -
            new Date(b.endsAt ?? b.startsAt ?? 0).getTime(),
        );
        break;
      default:
        sorted.sort((a, b) => (b.watchCount ?? 0) - (a.watchCount ?? 0));
    }
    return sorted;
  }, [filter, sort, query]);

  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">경매</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          카드셋 · 카드명 · 언어로 경매를 탐색하세요.
        </p>
      </div>

      {/* 검색 */}
      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--color-text-muted)]" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="카드명으로 검색"
          className="pl-10"
        />
      </div>

      {/* 필터 + 정렬 */}
      <div className="flex items-center justify-between">
        <Tabs value={filter} onValueChange={(v) => setFilter(v as Filter)}>
          <TabsList>
            <TabsTrigger value={AuctionStatus.LIVE}>진행 중</TabsTrigger>
            <TabsTrigger value={AuctionStatus.UPCOMING}>예정</TabsTrigger>
            <TabsTrigger value={AuctionStatus.ENDED}>종료</TabsTrigger>
          </TabsList>
        </Tabs>

        <DropdownMenu>
          <DropdownMenuTrigger className="flex items-center gap-1.5 rounded-[var(--radius-sm)] border border-border px-3 py-2 text-sm outline-none hover:bg-[var(--color-surface-2)]">
            {SORT_LABEL[sort]}
            <ChevronDown className="size-4 text-[var(--color-text-muted)]" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {(Object.keys(SORT_LABEL) as Sort[]).map((key) => (
              <DropdownMenuItem key={key} onSelect={() => setSort(key)}>
                {SORT_LABEL[key]}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 결과 */}
      {list.length === 0 ? (
        <EmptyState
          title="조건에 맞는 경매가 없습니다."
          description="검색어나 필터를 바꿔보세요."
        />
      ) : (
        <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
          {list.map((a) => (
            <AuctionCard key={a.id} auction={a} />
          ))}
        </div>
      )}
    </PageContainer>
  );
}
