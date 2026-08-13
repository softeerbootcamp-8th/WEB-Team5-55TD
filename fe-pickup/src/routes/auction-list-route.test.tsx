import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const { searchAuctionsMock } = vi.hoisted(() => ({
  searchAuctionsMock: vi.fn(),
}));

let infiniteQueryResult: Record<string, unknown>;
const fetchNextPage = vi.fn();
let observedCallback: IntersectionObserverCallback | undefined;
const observe = vi.fn();
const disconnect = vi.fn();

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useInfiniteQuery: ({
    queryFn,
  }: {
    queryFn: (context: { pageParam: unknown }) => unknown;
  }) => {
    queryFn({ pageParam: undefined });
    return infiniteQueryResult;
  },
}));
vi.mock("@/api/auctions", () => ({ searchAuctions: searchAuctionsMock }));
vi.mock("@/components/domain/auction-card", () => ({
  AuctionCard: ({ auction }: { auction: { cardName: string } }) => (
    <article>{auction.cardName}</article>
  ),
}));

class MockIntersectionObserver implements IntersectionObserver {
  readonly root = null;
  readonly rootMargin = "";
  readonly thresholds = [];
  constructor(callback: IntersectionObserverCallback) {
    observedCallback = callback;
  }
  observe = observe;
  disconnect = disconnect;
  unobserve = vi.fn();
  takeRecords = vi.fn(() => []);
}
vi.stubGlobal("IntersectionObserver", MockIntersectionObserver);

const SORT_CASES = [
  { label: "인기순", apiSort: "POPULAR" },
  { label: "가격 낮은순", apiSort: "PRICE_ASC" },
  { label: "가격 높은순", apiSort: "PRICE_DESC" },
  { label: "종료 임박순", apiSort: "ENDING_SOON" },
  { label: "시작 임박순", apiSort: "STARTING_SOON" },
  { label: "최신순", apiSort: "RECENT" },
] as const;

describe("경매 목록 라우트", () => {
  beforeEach(() => {
    searchAuctionsMock.mockClear();
    fetchNextPage.mockClear();
    observe.mockClear();
    disconnect.mockClear();
    observedCallback = undefined;
    infiniteQueryResult = {
      data: undefined,
      isPending: true,
      isError: false,
      refetch: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage,
    };
  });

  it("검색·정렬·필터 UI와 로딩 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "경매" })).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText("경매명 · 카드명 · 판매자로 검색"),
    ).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "진행 중" })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole("button", { name: /인기순/ }), {
      key: "Enter",
    });
    for (const { label } of SORT_CASES) {
      expect(screen.getByRole("menuitem", { name: label })).toBeInTheDocument();
    }
    expect(screen.getByText("경매를 불러오는 중입니다.")).toBeInTheDocument();
  });

  it.each(SORT_CASES)(
    "$label 선택 시 $apiSort 정렬 · size 20으로 조회한다",
    async ({ label, apiSort }) => {
      const { Route } = await import("@/routes/_buyer/auctions/index");
      const Component = Route.options.component as React.ComponentType;
      render(<Component />);

      if (label !== "인기순") {
        const trigger = screen.getByRole("button", { name: /인기순/ });
        fireEvent.keyDown(trigger, { key: "Enter" });
        fireEvent.click(await screen.findByRole("menuitem", { name: label }));
      }

      await waitFor(() =>
        expect(searchAuctionsMock).toHaveBeenLastCalledWith({
          q: undefined,
          searchField: "ALL",
          status: ["ONGOING"],
          sort: apiSort,
          cursor: undefined,
          size: 20,
        }),
      );
    },
  );

  it.each([
    { label: "경매명", apiField: "AUCTION_TITLE", placeholder: "경매명으로 검색" },
    { label: "카드명", apiField: "CARD_NAME", placeholder: "카드명으로 검색" },
    { label: "판매자", apiField: "SELLER", placeholder: "판매자로 검색" },
    {
      label: "통합",
      apiField: "ALL",
      placeholder: "경매명 · 카드명 · 판매자로 검색",
    },
  ])(
    "$label 선택 시 $apiField 조건으로 조회하고 안내 문구를 바꾼다",
    async ({ label, apiField, placeholder }) => {
      const { Route } = await import("@/routes/_buyer/auctions/index");
      const Component = Route.options.component as React.ComponentType;
      render(<Component />);

      if (label !== "통합") {
        fireEvent.keyDown(screen.getByRole("button", { name: /통합/ }), {
          key: "Enter",
        });
        fireEvent.click(await screen.findByRole("menuitem", { name: label }));
      }

      await waitFor(() =>
        expect(searchAuctionsMock).toHaveBeenLastCalledWith(
          expect.objectContaining({ searchField: apiField }),
        ),
      );
      expect(screen.getByPlaceholderText(placeholder)).toBeInTheDocument();
    },
  );

  it("다음 페이지가 있으면 스크롤 감시 영역을 관찰하고, 화면에 보이면 다음 페이지를 요청한다", async () => {
    const auction = {
      id: "1",
      cardName: "Mewtwo",
      status: "LIVE",
      currentPrice: 10000,
      watchCount: 2,
      endsAt: new Date(Date.now() + 3600000).toISOString(),
    };
    infiniteQueryResult = {
      data: { pages: [{ items: [auction] }] },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
      hasNextPage: true,
      isFetchingNextPage: false,
      fetchNextPage,
    };
    const { Route } = await import("@/routes/_buyer/auctions/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);

    expect(screen.getByText("Mewtwo")).toBeInTheDocument();
    // "더 보기" 버튼 없이 스크롤 감시(IntersectionObserver)만으로 다음 페이지를 트리거한다.
    expect(screen.queryByText("경매 더 보기")).not.toBeInTheDocument();
    expect(observe).toHaveBeenCalledTimes(1);

    expect(fetchNextPage).not.toHaveBeenCalled();
    observedCallback?.(
      [{ isIntersecting: true } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    );
    expect(fetchNextPage).toHaveBeenCalledTimes(1);
  });

  it("다음 페이지가 없으면 스크롤 감시 영역을 렌더링하지 않는다", async () => {
    infiniteQueryResult = {
      data: { pages: [{ items: [] }] },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage,
    };
    const { Route } = await import("@/routes/_buyer/auctions/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(observe).not.toHaveBeenCalled();
  });
});
