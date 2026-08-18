import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const initialAuction = {
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
let auction = initialAuction;
let nickname = "seller";
const connectionStatus = "connected";
const navigate = vi.fn();
const invalidateQueries = vi.fn();
const setQueryData = vi.fn();
let bidUpdateOptions: {
  onBidUpdated: (message: Record<string, unknown>) => void;
};
let auctionQueryOptions: Record<string, unknown>;
let bidsQueryResult: Record<string, unknown> = {
  isPending: false,
  isError: false,
  data: { items: [], hasNext: false },
};

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useLoaderData: () => ({ auction: initialAuction }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  notFound: () => new Error("not found"),
  useNavigate: () => navigate,
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: (options: { queryKey: string[] }) => {
    if (options.queryKey[0] !== "auction-detail") return bidsQueryResult;

    auctionQueryOptions = options;
    return { data: auction };
  },
  useQueryClient: () => ({ invalidateQueries, setQueryData }),
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: (options: typeof bidUpdateOptions) => {
    bidUpdateOptions = options;
    return connectionStatus;
  },
}));
vi.mock("@/lib/auth", () => ({ useNickname: () => nickname }));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  BID_PREVIEW_SIZE: 6,
  BID_MODAL_SIZE: 100,
}));

function makeBids(
  count: number,
  nicknameAt: (index: number) => string = (index) => `collector${index}`,
) {
  return Array.from({ length: count }, (_, i) => ({
    id: String(i),
    nickname: nicknameAt(i),
    amount: 11000 - i,
    createdAt: new Date().toISOString(),
  }));
}

describe("셀러 경매 상세", () => {
  afterEach(() => {
    auction = initialAuction;
    nickname = "seller";
    navigate.mockReset();
    invalidateQueries.mockReset();
    setQueryData.mockReset();
  });

  it("소유자의 경매 모니터링 정보와 최근 입찰 미리보기를 표시한다", () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(1, () => "collector88"), hasNext: false },
    };
    return import("@/routes/seller/auctions.$auctionId").then(({ Route }) => {
      const Component = Route.options.component as ComponentType;
      const { unmount } = render(<Component />);
      expect(
        screen.getByRole("heading", { name: "Mewtwo" }),
      ).toBeInTheDocument();
      expect(screen.getByText("12,000원")).toBeInTheDocument();
      expect(screen.getByText("실시간 순위")).toBeInTheDocument();
      expect(screen.getByText("collector88")).toBeInTheDocument();
      expect(screen.getByText("1")).toBeInTheDocument();
      unmount();

      nickname = "other";
      const guarded = render(<Component />);
      expect(
        guarded.getByText("본인 소유의 경매만 모니터링할 수 있습니다."),
      ).toBeInTheDocument();
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
      data: { items: makeBids(100), hasNext: false },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.queryByText("100+")).not.toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(6);
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
    expect(screen.getAllByRole("listitem")).toHaveLength(100);
  });

  it("실시간 미리보기에서는 같은 입찰자의 최신 입찰만 보여준다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: {
        items: [
          ...makeBids(2, () => "collector88"),
          ...makeBids(1, () => "another99").map((bid) => ({
            ...bid,
            id: "3",
          })),
        ],
        hasNext: false,
      },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    expect(screen.getAllByText("collector88")).toHaveLength(1);
    expect(screen.getAllByRole("listitem")).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "전체" }));
    expect(screen.getAllByText("collector88")).toHaveLength(3);
  });

  it("웹소켓 입찰을 현재가와 입찰 목록에 즉시 반영한다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: makeBids(1), hasNext: false },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    const latestBid = {
      bidId: 2,
      nickname: "newBidder",
      bidPrice: 15000,
      createdAt: new Date().toISOString(),
    };
    act(() => {
      bidUpdateOptions.onBidUpdated({ currentPrice: 15000, latestBid });
    });

    expect(screen.getByText("15,000원")).toBeInTheDocument();
    expect(setQueryData).toHaveBeenCalledWith(
      ["auction-bids", "4"],
      expect.any(Function),
    );
    const updateCache = setQueryData.mock.calls[0][1] as (
      snapshot: Record<string, unknown>,
    ) => { items: Array<{ id: string }> };
    const updated = updateCache(
      bidsQueryResult.data as Record<string, unknown>,
    );
    expect(updated.items[0].id).toBe("2");
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ["auction-bids", "4"],
    });
  });

  it("경매 종료 상태를 확인하면 결과 화면으로 이동한다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: [], hasNext: false },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    const { rerender } = render(<Component />);

    auction = { ...initialAuction, status: "ENDED" };
    rerender(<Component />);

    expect(navigate).toHaveBeenCalledWith({
      to: "/auctions/$auctionId/end",
      params: { auctionId: "4" },
    });
  });

  it("예정 경매는 남은 시간 대신 시작 시각을 보여준다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: [], hasNext: false },
    };
    auction = {
      ...initialAuction,
      status: "UPCOMING",
      startsAt: "2026-09-01T05:30:00Z",
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    expect(screen.getByText("시작 시각")).toBeInTheDocument();
    expect(screen.getByText("2026.09.01 14:30")).toBeInTheDocument();
    expect(screen.queryByText("남은 시간")).not.toBeInTheDocument();
  });

  it("진행 중 경매는 남은 시간을 보여준다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: [], hasNext: false },
    };
    auction = initialAuction;
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    expect(screen.getByText("남은 시간")).toBeInTheDocument();
    expect(screen.queryByText("시작 시각")).not.toBeInTheDocument();
  });

  it("진행 중에는 상세 상태를 주기적으로 확인하고 종료 후에는 멈춘다", async () => {
    bidsQueryResult = {
      isPending: false,
      isError: false,
      data: { items: [], hasNext: false },
    };
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    const refetchInterval = auctionQueryOptions.refetchInterval as (query: {
      state: { data: typeof initialAuction | { status: string } };
    }) => number | false;
    expect(refetchInterval({ state: { data: initialAuction } })).toBe(15_000);
    expect(refetchInterval({ state: { data: { status: "ENDED" } } })).toBe(
      false,
    );
  });
});
