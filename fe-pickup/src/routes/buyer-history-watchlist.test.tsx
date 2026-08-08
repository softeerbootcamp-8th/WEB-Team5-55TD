import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queryResults: Array<Record<string, unknown>> = [];
let watches: Record<string, unknown> = {
  data: { items: [] },
  isPending: true,
  isError: false,
  refetch: vi.fn(),
};
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => queryResults.shift(),
}));
vi.mock("@/api/bids", () => ({ getMyBids: vi.fn(), getMyWins: vi.fn() }));
vi.mock("@/api/generated/member/member", () => ({
  useGetMyWatches: () => watches,
}));
vi.mock("@/components/domain/auction-card", () => ({
  AuctionCard: ({ auction }: { auction: { cardName: string } }) => (
    <article>{auction.cardName}</article>
  ),
}));

const bid = {
  auctionId: "3",
  cardName: "Pikachu",
  myBid: 10000,
  currentPrice: 12000,
  status: "HIGHEST",
  live: true,
  grade: { agency: "PSA", score: "10" },
};

describe("구매자 입찰·관심 목록", () => {
  beforeEach(() => {
    queryResults = [];
    watches = {
      data: { items: [] },
      isPending: true,
      isError: false,
      refetch: vi.fn(),
    };
  });

  it("입찰 내역의 로딩·오류·목록과 낙찰 탭을 표시한다", async () => {
    queryResults = [
      { isPending: true, isError: false },
      { isPending: true, isError: false },
    ];
    const bids = await import("@/routes/_buyer/bids");
    const Component = bids.Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오는 중입니다."),
    ).toBeInTheDocument();

    cleanup();
    queryResults = [
      { isPending: false, isError: true, refetch: vi.fn() },
      { isPending: false, isError: false, data: { items: [] } },
    ];
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
    cleanup();
    queryResults = [
      { isPending: false, isError: false, data: { items: [bid] } },
      { isPending: false, isError: false, data: { items: [] } },
    ];
    render(<Component />);
    expect(screen.getAllByText("Pikachu").length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("tab", { name: "낙찰 내역" }));
    expect(screen.getByRole("tab", { name: "낙찰 내역" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("관심 목록의 오류·빈 상태·카드 목록을 표시한다", async () => {
    const watch = await import("@/routes/_buyer/watchlist");
    const Component = watch.Route.options.component as ComponentType;
    watches = {
      data: { items: [] },
      isPending: false,
      isError: true,
      refetch: vi.fn(),
    };
    render(<Component />);
    expect(
      screen.getByText("관심 경매를 불러오지 못했습니다."),
    ).toBeInTheDocument();
    cleanup();
    watches = {
      data: { items: [] },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    };
    render(<Component />);
    expect(
      screen.getByText("관심 등록한 경매가 없습니다."),
    ).toBeInTheDocument();
    cleanup();
    watches = {
      data: {
        items: [
          {
            auctionId: 9,
            card: { cardName: "Mewtwo" },
            auctionStatus: "ONGOING",
            startingPrice: 1000,
            currentPrice: 2000,
            grade: "BGS 9",
            watchCount: 1,
            watched: true,
          },
        ],
      },
      isPending: false,
      isError: false,
      refetch: vi.fn(),
    };
    render(<Component />);
    expect(screen.getByText("Mewtwo")).toBeInTheDocument();
  });
});
