import { cleanup, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queries: Array<Record<string, unknown>> = [];
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => queries.shift() }));
vi.mock("@/api/auctions", () => ({
  getFeaturedAuction: vi.fn(),
  searchAuctions: vi.fn(),
}));
vi.mock("@/components/domain/auction-card", () => ({
  AuctionCard: ({ auction }: { auction: { cardName: string } }) => (
    <article>{auction.cardName}</article>
  ),
}));

const auction = {
  id: "1",
  cardName: "Mewtwo",
  status: "LIVE",
  currentPrice: 10000,
  watchCount: 2,
  endsAt: new Date(Date.now() + 3600000).toISOString(),
};
describe("구매자 홈", () => {
  beforeEach(() => {
    queries = [];
  });
  it("대표 경매와 진행·예정 목록을 표시한다", async () => {
    queries = [
      { isPending: false, data: auction },
      { isPending: false, data: { items: [auction] } },
      { isPending: false, data: { items: [auction] } },
    ];
    const { Route } = await import("@/routes/_buyer/home");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getAllByText("Mewtwo").length).toBeGreaterThan(0);
    expect(screen.getByText("진행 중인 경매")).toBeInTheDocument();
    expect(screen.getByText("곧 시작하는 경매")).toBeInTheDocument();
  });
  it("대표 경매에 title이 있으면 카드 이름 대신 title을 표시한다", async () => {
    cleanup();
    queries = [
      { isPending: false, data: { ...auction, title: "뮤츠 1급 감정 경매" } },
      { isPending: false, data: { items: [auction] } },
      { isPending: false, data: { items: [auction] } },
    ];
    const { Route } = await import("@/routes/_buyer/home");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("뮤츠 1급 감정 경매")).toBeInTheDocument();
  });
  it("목록이 비어 있으면 빈 상태를 표시한다", async () => {
    cleanup();
    queries = [
      { isPending: false, data: null },
      { isPending: false, data: { items: [] } },
      { isPending: false, data: { items: [] } },
    ];
    const { Route } = await import("@/routes/_buyer/home");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("진행 중인 경매가 없습니다.")).toBeInTheDocument();
    expect(
      screen.getByText("시작 예정인 경매가 없습니다."),
    ).toBeInTheDocument();
  });
});
