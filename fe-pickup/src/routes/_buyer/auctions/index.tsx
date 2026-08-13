import { useDeferredValue, useEffect, useRef, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useInfiniteQuery } from "@tanstack/react-query";
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
import {
  searchAuctions,
  type AuctionSearchField,
  type AuctionSort,
} from "@/api/auctions";
import { AuctionStatus } from "@/lib/types";

export const Route = createFileRoute("/_buyer/auctions/")({
  component: AuctionListPage,
});

type Filter = "LIVE" | "UPCOMING" | "ENDED";
type Sort =
  | "popular"
  | "priceAsc"
  | "priceDesc"
  | "endingSoon"
  | "startingSoon"
  | "recent";

const SORT_LABEL: Record<Sort, string> = {
  popular: "인기순",
  priceAsc: "가격 낮은순",
  priceDesc: "가격 높은순",
  endingSoon: "종료 임박순",
  startingSoon: "시작 임박순",
  recent: "최신순",
};

const API_STATUS: Record<
  Filter,
  ("SCHEDULED" | "ONGOING" | "WON" | "PASSED")[]
> = {
  LIVE: ["ONGOING"],
  UPCOMING: ["SCHEDULED"],
  ENDED: ["WON", "PASSED"],
};

const API_SORT: Record<Sort, AuctionSort> = {
  popular: "POPULAR",
  priceAsc: "PRICE_ASC",
  priceDesc: "PRICE_DESC",
  endingSoon: "ENDING_SOON",
  startingSoon: "STARTING_SOON",
  recent: "RECENT",
};

const SEARCH_FIELD_LABEL: Record<AuctionSearchField, string> = {
  ALL: "통합",
  AUCTION_TITLE: "경매명",
  CARD_NAME: "카드명",
  SELLER: "판매자",
};

const SEARCH_PLACEHOLDER: Record<AuctionSearchField, string> = {
  ALL: "경매명 · 카드명 · 판매자로 검색",
  AUCTION_TITLE: "경매명으로 검색",
  CARD_NAME: "카드명으로 검색",
  SELLER: "판매자로 검색",
};

/** DESIGN.md · auction list.html — 검색 · 정렬 · 진행/예정/종료 필터 */
function AuctionListPage() {
  const [filter, setFilter] = useState<Filter>("LIVE");
  const [sort, setSort] = useState<Sort>("popular");
  const [query, setQuery] = useState("");
  const [searchField, setSearchField] = useState<AuctionSearchField>("ALL");
  const deferredQuery = useDeferredValue(query.trim());

  const { data, isPending, isError, refetch, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useInfiniteQuery({
      queryKey: ["auctions", filter, sort, deferredQuery, searchField],
      queryFn: ({ pageParam }) =>
        searchAuctions({
          q: deferredQuery || undefined,
          searchField,
          status: API_STATUS[filter],
          sort: API_SORT[sort],
          cursor: pageParam,
          size: 20,
        }),
      initialPageParam: undefined as string | undefined,
      getNextPageParam: (lastPage) =>
        lastPage.hasNext ? lastPage.cursor : undefined,
    });
  const list = data?.pages.flatMap((page) => page.items) ?? [];

  const sentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const sentinel = sentinelRef.current;
    if (!sentinel || !hasNextPage) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isFetchingNextPage) {
          fetchNextPage();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <PageContainer className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">경매</h1>
        <p className="text-sm text-[var(--color-text-sub)]">
          경매명 · 카드명 · 판매자로 경매를 탐색하세요.
        </p>
      </div>

      {/* 검색 */}
      <div className="flex gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger className="flex shrink-0 items-center gap-1.5 rounded-[var(--radius-sm)] border border-border px-3 py-2 text-sm outline-none hover:bg-[var(--color-surface-2)]">
            {SEARCH_FIELD_LABEL[searchField]}
            <ChevronDown className="size-4 text-[var(--color-text-muted)]" />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {(Object.keys(SEARCH_FIELD_LABEL) as AuctionSearchField[]).map(
              (key) => (
                <DropdownMenuItem
                  key={key}
                  onSelect={() => setSearchField(key)}
                >
                  {SEARCH_FIELD_LABEL[key]}
                </DropdownMenuItem>
              ),
            )}
          </DropdownMenuContent>
        </DropdownMenu>

        <div className="relative flex-1">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--color-text-muted)]" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={SEARCH_PLACEHOLDER[searchField]}
            className="pl-10"
          />
        </div>
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
      {isPending ? (
        <p className="py-12 text-center text-sm text-[var(--color-text-sub)]">
          경매를 불러오는 중입니다.
        </p>
      ) : isError ? (
        <EmptyState
          title="경매를 불러오지 못했습니다."
          description="잠시 후 다시 시도해 주세요."
          action={
            <button
              type="button"
              onClick={() => refetch()}
              className="text-sm font-semibold text-primary hover:underline"
            >
              다시 시도
            </button>
          }
        />
      ) : list.length === 0 ? (
        <EmptyState
          title="조건에 맞는 경매가 없습니다."
          description="검색어나 필터를 바꿔보세요."
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-5 md:grid-cols-4">
            {list.map((a) => (
              <AuctionCard key={a.id} auction={a} />
            ))}
          </div>
          {hasNextPage && (
            <div ref={sentinelRef} className="py-4 text-center">
              {isFetchingNextPage && (
                <p className="text-sm text-[var(--color-text-sub)]">
                  불러오는 중
                </p>
              )}
            </div>
          )}
        </>
      )}
    </PageContainer>
  );
}
