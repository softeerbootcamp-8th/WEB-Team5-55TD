import { act, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const auction = {
  id: "1",
  cardName: "Mewtwo",
  status: "LIVE" as const,
  currentPrice: 10000,
  startPrice: 10000,
  minBidUnit: 500,
  endsAt: "2099-01-01T11:00:00",
};

let onBidUpdated: ((message: Record<string, unknown>) => void) | undefined;
const toastWarning = vi.fn();

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useLoaderData: () => ({ auction }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  notFound: () => new Error("not found"),
  useNavigate: () => vi.fn(),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: (options: { initialData?: unknown }) => ({
    data: options.initialData,
    isPending: false,
  }),
  useInfiniteQuery: (options: { queryKey: unknown[] }) => {
    // "preview" 입찰 목록 조회에서, 조회자 본인이 현재 최고 입찰자인 상태를 시뮬레이션한다.
    if (options.queryKey.includes("preview")) {
      return {
        data: {
          pages: [
            {
              items: [
                { id: "5", maskedNickname: "me", amount: 10000, isMine: true },
              ],
              hasNext: false,
            },
          ],
        },
        isPending: false,
        hasNextPage: false,
        isFetchingNextPage: false,
        fetchNextPage: vi.fn(),
      };
    }
    return {
      data: undefined,
      isPending: false,
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage: vi.fn(),
    };
  },
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  getBidErrorMessage: vi.fn(),
  placeBid: vi.fn(),
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: (options: {
    onBidUpdated: (message: Record<string, unknown>) => void;
  }) => {
    onBidUpdated = options.onBidUpdated;
  },
}));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => true }));
vi.mock("sonner", () => ({
  toast: { warning: toastWarning, success: vi.fn(), error: vi.fn() },
}));

describe("실시간 경매 추월 알림", () => {
  beforeEach(() => {
    toastWarning.mockClear();
  });

  it("내가_최고_입찰자였다가_다른_회원에게_추월당하면_알림을_보여준다", async () => {
    const { Route } = await import(
      "@/routes/_buyer/auctions/$auctionId/live"
    );
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    act(() => {
      onBidUpdated?.({
        latestBid: { bidId: 6, nicknameMasked: "다른회원", bidPrice: 10500 },
        currentPrice: 10500,
        endedAt: null,
      });
    });

    expect(toastWarning).toHaveBeenCalledWith(
      "추월당했습니다",
      expect.objectContaining({
        description: expect.stringContaining("10,500"),
      }),
    );
  });

  it("자신의_입찰이_그대로_최고가면_알림을_보여주지_않는다", async () => {
    const { Route } = await import(
      "@/routes/_buyer/auctions/$auctionId/live"
    );
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    act(() => {
      onBidUpdated?.({
        latestBid: { bidId: 5, nicknameMasked: "me", bidPrice: 10000 },
        currentPrice: 10000,
        endedAt: null,
      });
    });

    expect(toastWarning).not.toHaveBeenCalled();
  });
});
