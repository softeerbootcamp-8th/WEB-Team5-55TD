import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const auction = {
  id: "4",
  cardName: "Mewtwo",
  status: "LIVE",
  currentPrice: 12000,
  startPrice: 10000,
  sellerNickname: "seller",
  images: ["front.jpg"],
  grade: { agency: "PSA", score: "10" },
  endsAt: new Date(Date.now() + 3600000).toISOString(),
};
let nickname = "seller";
let bidsQueryResult: Record<string, unknown> = {
  isPending: false,
  isError: false,
  data: { items: [], hasNext: false },
};
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useLoaderData: () => ({ auction }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  notFound: () => new Error("not found"),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => bidsQueryResult,
}));
vi.mock("@/lib/auth", () => ({ useNickname: () => nickname }));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({ getAuctionBids: vi.fn() }));

describe("셀러 경매 상세", () => {
  it("소유자의 경매 모니터링 정보와 실제 입찰 내역을 표시한다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: {
        items: [
          {
            id: "1",
            maskedNickname: "col***88",
            amount: 11000,
            createdAt: new Date().toISOString(),
          },
        ],
        hasNext: false,
      },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "Mewtwo" })).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("입찰 내역")).toBeInTheDocument();
    expect(screen.getByText("col***88")).toBeInTheDocument();
    // 입찰 횟수는 목데이터가 아니라 조회된 입찰 내역 개수를 반영한다.
    expect(screen.getByText("1")).toBeInTheDocument();
    cleanup();

    nickname = "other";
    const guarded = render(<Component />);
    expect(
      guarded.getByText("본인 소유의 경매만 모니터링할 수 있습니다."),
    ).toBeInTheDocument();
    nickname = "seller";
  });

  it("입찰 내역 조회 실패 시 안내 문구를 보여준다", async () => {
    bidsQueryResult = { isPending: false, isError: true, data: undefined };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });
});
