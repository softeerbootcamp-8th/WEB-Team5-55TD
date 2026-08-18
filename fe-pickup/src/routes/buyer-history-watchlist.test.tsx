import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queryResults: Array<Record<string, unknown>> = [];
let watches: Record<string, unknown> = {
  data: { pages: [{ items: [] }] },
  isPending: true,
  isError: false,
  refetch: vi.fn(),
  hasNextPage: false,
  isFetchingNextPage: false,
  fetchNextPage: vi.fn(),
};
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useInfiniteQuery: () => queryResults.shift(),
}));
vi.mock("@/api/bids", () => ({ getMyBids: vi.fn(), getMyWins: vi.fn() }));
vi.mock("@/api/generated/member/member", () => ({
  getMyWatches: vi.fn(),
  getGetMyWatchesQueryKey: () => ["my-watches"],
}));
vi.mock("@/components/domain/auction-card", () => ({
  AuctionCard: ({
    auction,
  }: {
    auction: { title?: string; cardName: string };
  }) => (
    <article>
      <strong>{auction.title ?? auction.cardName}</strong>
      {auction.title && <span>{auction.cardName}</span>}
    </article>
  ),
}));

function watchesResult(overrides: Record<string, unknown>) {
  return {
    isPending: false,
    isError: false,
    refetch: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    ...overrides,
  };
}

const bid = {
  auctionId: "3",
  title: "피카츄 특별 경매",
  cardName: "Pikachu",
  myBid: 10000,
  currentPrice: 12000,
  status: "HIGHEST",
  live: true,
  grade: { agency: "PSA", score: "10" },
};

function bidsResult(overrides: Record<string, unknown>) {
  return {
    isPending: false,
    isError: false,
    refetch: vi.fn(),
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    ...overrides,
  };
}

describe("구매자 입찰·관심 목록", () => {
  beforeEach(() => {
    queryResults = [];
    watches = {
      data: { pages: [{ items: [] }] },
      isPending: true,
      isError: false,
      refetch: vi.fn(),
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage: vi.fn(),
    };
  });

  it("입찰 내역의 로딩·오류·목록과 낙찰 탭을 표시한다", async () => {
    queryResults = [
      bidsResult({ isPending: true, data: undefined }),
      bidsResult({ isPending: true, data: undefined }),
    ];
    const bids = await import("@/routes/_buyer/bids");
    const Component = bids.Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오는 중입니다."),
    ).toBeInTheDocument();

    cleanup();
    queryResults = [
      bidsResult({ isError: true }),
      bidsResult({ data: { pages: [{ items: [] }] } }),
    ];
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    cleanup();
    queryResults = [
      bidsResult({ data: { pages: [{ items: [bid] }] } }),
      bidsResult({ data: { pages: [{ items: [] }] } }),
    ];
    render(<Component />);
    expect(screen.getByText("피카츄 특별 경매")).toBeInTheDocument();
    expect(screen.getAllByText("Pikachu").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("tab", { name: "낙찰 내역" }));
    expect(screen.getByRole("tab", { name: "낙찰 내역" })).toBeInTheDocument();
    cleanup();

    // 다음 페이지가 있으면 "더 보기" 버튼으로 커서 기반 다음 페이지를 불러온다.
    const fetchNextPage = vi.fn();
    queryResults = [
      bidsResult({
        data: { pages: [{ items: [bid] }] },
        hasNextPage: true,
        fetchNextPage,
      }),
      bidsResult({ data: { pages: [{ items: [] }] } }),
    ];
    render(<Component />);
    fireEvent.click(screen.getByRole("button", { name: "더 보기" }));
    expect(fetchNextPage).toHaveBeenCalledTimes(1);
  });

  it("관심 목록의 오류·빈 상태·카드 목록을 표시한다", async () => {
    const watch = await import("@/routes/_buyer/watchlist");
    const Component = watch.Route.options.component as ComponentType;
    watches = watchesResult({
      data: { pages: [{ items: [] }] },
      isError: true,
    });
    queryResults = [watches];
    render(<Component />);
    expect(
      screen.getByText("관심 경매를 불러오지 못했습니다."),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    cleanup();
    queryResults = [watchesResult({ data: { pages: [{ items: [] }] } })];
    render(<Component />);
    expect(
      screen.getByText("관심 등록한 경매가 없습니다."),
    ).toBeInTheDocument();
    cleanup();
    queryResults = [
      watchesResult({
        data: {
          pages: [
            {
              items: [
                {
                  auctionId: 9,
                  title: "뮤츠 특별 경매",
                  card: { cardName: "Mewtwo" },
                  auctionStatus: "ONGOING",
                  startingPrice: 1000,
                  currentPrice: 2000,
                  grade: "BGS 9",
                  watchCount: 1,
                  watched: true,
                },
                {
                  auctionId: 10,
                  card: { cardName: "Eevee" },
                  auctionStatus: "SCHEDULED",
                  startingPrice: 500,
                  grade: "",
                  watchCount: 1,
                  watched: true,
                },
                {
                  auctionId: 11,
                  card: { cardName: "Gengar" },
                  auctionStatus: "PASSED",
                  startingPrice: 700,
                  grade: "CGC 8",
                  watchCount: 0,
                  watched: true,
                },
              ],
            },
          ],
        },
      }),
    ];
    render(<Component />);
    expect(screen.getByText("뮤츠 특별 경매")).toBeInTheDocument();
    expect(screen.getByText("Mewtwo")).toBeInTheDocument();
    expect(screen.getByText("Eevee")).toBeInTheDocument();
    expect(screen.getByText("Gengar")).toBeInTheDocument();
    cleanup();

    // 다음 페이지가 있으면 "더 보기" 버튼으로 커서 기반 다음 페이지를 불러온다.
    const fetchNextPage = vi.fn();
    queryResults = [
      watchesResult({
        data: {
          pages: [
            {
              items: [
                {
                  auctionId: 12,
                  card: { cardName: "Charizard" },
                  auctionStatus: "ONGOING",
                  startingPrice: 1000,
                  currentPrice: 2000,
                  grade: "PSA 10",
                  watchCount: 1,
                  watched: true,
                },
              ],
            },
          ],
        },
        hasNextPage: true,
        fetchNextPage,
      }),
    ];
    render(<Component />);
    fireEvent.click(screen.getByRole("button", { name: "더 보기" }));
    expect(fetchNextPage).toHaveBeenCalledTimes(1);
  });
});
