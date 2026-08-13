import { fireEvent, render, screen } from "@testing-library/react";
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
const connectionStatus = "connected";
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
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: () => connectionStatus,
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
    nickname: "collector88",
    amount: 11000 - i,
    createdAt: new Date().toISOString(),
  }));
}

describe("셀러 경매 상세", () => {
  it("소유자의 경매 모니터링 정보와 최근 입찰 미리보기를 표시한다", () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(1), hasNext: false },
    };
    return import("@/routes/seller/auctions.$auctionId").then(({ Route }) => {
      const Component = Route.options.component as ComponentType;
      const { unmount } = render(<Component />);
      expect(
        screen.getByRole("heading", { name: "Mewtwo" }),
      ).toBeInTheDocument();
      expect(screen.getByText("12,000원")).toBeInTheDocument();
      expect(screen.getByText("입찰 내역")).toBeInTheDocument();
      expect(screen.getByText("collector88")).toBeInTheDocument();
      // 입찰 횟수는 목데이터가 아니라 조회된 입찰 내역 개수를 그대로 반영한다.
      expect(screen.getByText("1")).toBeInTheDocument();
      unmount();

      nickname = "other";
      const guarded = render(<Component />);
      expect(
        guarded.getByText("본인 소유의 경매만 모니터링할 수 있습니다."),
      ).toBeInTheDocument();
      nickname = "seller";
    });
  });

  it("조회 실패 시 안내 문구를 보여준다", async () => {
    bidsQueryResult = { isPending: false, isError: true, data: undefined };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(
      screen.getByText("입찰 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });

  it("미리보기는 6건까지만 보여주고, 조회된 입찰이 100건을 넘을 때만 입찰 횟수에 +를 붙인다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      // 6건 넘게 조회돼도(여기선 100건) hasNext가 false면 "+"를 붙이지 않는다 —
      // 미리보기 6건 제한 자체는 "+" 여부와 무관해야 한다.
      data: { items: makeBids(100), hasNext: false },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.queryByText("100+")).not.toBeInTheDocument();
    // 입찰 내역 아이템(가격 표시)은 미리보기 영역에 6개만 렌더된다. 나머지는
    // 아직 열지 않은 모달 안에 있으므로 화면에는 보이지 않는다.
    expect(screen.getAllByText("collector88")).toHaveLength(6);
  });

  it("조회 결과가 잘렸으면(100+) 입찰 횟수에 +를 붙이고, 전체 클릭 시 모달에서 전체 목록을 보여준다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(100), hasNext: true },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("100+")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "전체" }));
    expect(
      screen.getByRole("heading", { name: "전체 입찰 내역" }),
    ).toBeInTheDocument();
    // 모달에는 미리보기(6개)보다 훨씬 많은 100개가 그대로 표시된다.
    expect(screen.getAllByText("collector88")).toHaveLength(106);
  });
});
