import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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

function pendingQuery() {
  return { isPending: true, isError: false, data: undefined };
}

let previewResult: Record<string, unknown> = pendingQuery();
let allResult: Record<string, unknown> = pendingQuery();

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
// 호출 순서가 아니라 queryKey로 구분한다 — "전체" 클릭 시 재렌더링되며 두 쿼리가
// 다시 호출되는데, 순서 기반 큐로는 재렌더링마다 스킵되는 값이 달라져 깨지기 쉽다.
vi.mock("@tanstack/react-query", () => ({
  useQuery: (options: { queryKey: unknown[] }) =>
    options.queryKey.includes("all") ? allResult : previewResult,
}));
vi.mock("@/lib/auth", () => ({ useNickname: () => nickname }));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  BID_PREVIEW_SIZE: 6,
  BID_MODAL_SIZE: 100,
}));

function makeBids(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: String(i),
    maskedNickname: "col***88",
    amount: 11000 - i,
    createdAt: new Date().toISOString(),
  }));
}

describe("셀러 경매 상세", () => {
  it("소유자의 경매 모니터링 정보와 최근 입찰 미리보기를 표시한다", async () => {
    previewResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(1), hasNext: false },
    };
    allResult = pendingQuery();
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "Mewtwo" })).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("입찰 내역")).toBeInTheDocument();
    expect(screen.getByText("col***88")).toBeInTheDocument();
    // 입찰 횟수는 목데이터가 아니라 미리보기로 조회된 입찰 내역 개수를 반영한다.
    expect(screen.getByText("1")).toBeInTheDocument();
    cleanup();

    nickname = "other";
    const guarded = render(<Component />);
    expect(
      guarded.getByText("본인 소유의 경매만 모니터링할 수 있습니다."),
    ).toBeInTheDocument();
    nickname = "seller";
  });

  it("미리보기 조회 실패 시 안내 문구를 보여준다", async () => {
    previewResult = { isPending: false, isError: true, data: undefined };
    allResult = pendingQuery();
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });

  it("미리보기가 잘렸으면 입찰 횟수에 +를 붙이고, 전체 클릭 시 모달에 전체 목록을 보여준다", async () => {
    previewResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(6), hasNext: true },
    };
    allResult = pendingQuery();
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("6+")).toBeInTheDocument();
    // 아직 모달을 열지 않았으니 전체 조회 결과(12건)는 반영되지 않는다.
    expect(screen.queryByText("12")).not.toBeInTheDocument();

    allResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(12), hasNext: false },
    };
    fireEvent.click(screen.getByRole("button", { name: "전체" }));
    expect(screen.getByRole("heading", { name: "전체 입찰 내역" })).toBeInTheDocument();
    // 모달을 열어 전체 조회가 끝나면 입찰 횟수도 더 정확한 값(12)으로 갱신된다.
    expect(screen.getByText("12")).toBeInTheDocument();
  });
});
