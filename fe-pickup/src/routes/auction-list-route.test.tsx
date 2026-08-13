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
  { label: "인기순", apiSort: "POPULAR", tab: "진행 중", status: ["ONGOING"] },
  {
    label: "가격 낮은순",
    apiSort: "PRICE_ASC",
    tab: "진행 중",
    status: ["ONGOING"],
  },
  {
    label: "가격 높은순",
    apiSort: "PRICE_DESC",
    tab: "진행 중",
    status: ["ONGOING"],
  },
  {
    label: "종료 임박순",
    apiSort: "ENDING_SOON",
    tab: "진행 중",
    status: ["ONGOING"],
  },
  {
    label: "시작 임박순",
    apiSort: "STARTING_SOON",
    tab: "예정",
    status: ["SCHEDULED"],
  },
  { label: "최신순", apiSort: "RECENT", tab: "진행 중", status: ["ONGOING"] },
] as const;

const TAB_SORT_LABELS = [
  {
    tab: "진행 중",
    visible: ["인기순", "가격 낮은순", "가격 높은순", "종료 임박순", "최신순"],
    hidden: ["시작 임박순"],
  },
  {
    tab: "예정",
    visible: ["인기순", "가격 낮은순", "가격 높은순", "시작 임박순", "최신순"],
    hidden: ["종료 임박순"],
  },
  {
    tab: "종료",
    visible: ["인기순", "가격 낮은순", "가격 높은순", "최신순"],
    hidden: ["종료 임박순", "시작 임박순"],
  },
] as const;

async function renderAuctionListPage() {
  const { Route } = await import("@/routes/_buyer/auctions/index");
  const Component = Route.options.component as React.ComponentType;
  render(<Component />);
}

function openSortMenu(currentLabel: string) {
  fireEvent.keyDown(screen.getByRole("button", { name: currentLabel }), {
    key: "Enter",
  });
}

// Radix Tabs 는 click 이 아니라 mousedown 으로 탭을 전환한다.
function selectTab(name: string) {
  fireEvent.mouseDown(screen.getByRole("tab", { name }));
}

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
    await renderAuctionListPage();
    expect(screen.getByRole("heading", { name: "경매" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("카드명으로 검색")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "진행 중" })).toBeInTheDocument();
    expect(screen.getByText("경매를 불러오는 중입니다.")).toBeInTheDocument();
  });

  it.each(TAB_SORT_LABELS)(
    "$tab 탭에서는 $hidden 을 제외한 정렬 기준만 보여준다",
    async ({ tab, visible, hidden }) => {
      await renderAuctionListPage();

      selectTab(tab);
      openSortMenu("인기순");

      for (const label of visible) {
        expect(
          await screen.findByRole("menuitem", { name: label }),
        ).toBeInTheDocument();
      }
      for (const label of hidden) {
        expect(
          screen.queryByRole("menuitem", { name: label }),
        ).not.toBeInTheDocument();
      }
    },
  );

  it("고른 정렬이 새 탭에서 빠지면 인기순으로 되돌리고 다시 조회한다", async () => {
    await renderAuctionListPage();

    openSortMenu("인기순");
    fireEvent.click(await screen.findByRole("menuitem", { name: "종료 임박순" }));
    await waitFor(() =>
      expect(searchAuctionsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: "ENDING_SOON", status: ["ONGOING"] }),
      ),
    );

    selectTab("종료");

    await waitFor(() =>
      expect(searchAuctionsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: "POPULAR", status: ["WON", "PASSED"] }),
      ),
    );
    expect(screen.getByRole("button", { name: "인기순" })).toBeInTheDocument();
  });

  it("고른 정렬이 새 탭에도 있으면 그대로 유지한다", async () => {
    await renderAuctionListPage();

    openSortMenu("인기순");
    fireEvent.click(await screen.findByRole("menuitem", { name: "최신순" }));

    selectTab("예정");

    await waitFor(() =>
      expect(searchAuctionsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: "RECENT", status: ["SCHEDULED"] }),
      ),
    );
  });

  it.each(SORT_CASES)(
    "$tab 탭에서 $label 선택 시 $apiSort 정렬 · size 20으로 조회한다",
    async ({ label, apiSort, tab, status }) => {
      await renderAuctionListPage();

      if (tab !== "진행 중") {
        selectTab(tab);
      }
      if (label !== "인기순") {
        openSortMenu("인기순");
        fireEvent.click(await screen.findByRole("menuitem", { name: label }));
      }

      await waitFor(() =>
        expect(searchAuctionsMock).toHaveBeenLastCalledWith({
          q: undefined,
          status,
          sort: apiSort,
          cursor: undefined,
          size: 20,
        }),
      );
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
