import { act, fireEvent, render, screen, within } from "@testing-library/react";
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

const myNickname: string | null = "collector88";
let onBidUpdated: ((message: Record<string, unknown>) => void) | undefined;
const toastWarning = vi.fn();
const toastSuccess = vi.fn();
const toastError = vi.fn();
const getBidRequestResult = vi.fn();

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
                { id: "5", nickname: "me", amount: 10000, isMine: true },
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
  useMutation: () => ({
    mutate: (
      price: number,
      options?: { onSuccess?: (placed: Record<string, unknown>) => void },
    ) => {
      // 입찰 요청 접수(202)가 성공한 상황을 시뮬레이션한다 — 실제 처리 결과는 아직 모른다.
      options?.onSuccess?.({
        bidRequestId: 42,
        auctionId: 1,
        memberId: 2,
        bidPrice: price,
        status: "PENDING",
        createdAt: "2026-01-01T00:00:00",
      });
    },
    isPending: false,
  }),
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
    setQueryData: vi.fn(),
  }),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  getBidErrorMessage: vi.fn(),
  createBidRequest: vi.fn(),
  getBidRequestResult,
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: (options: {
    onBidUpdated: (message: Record<string, unknown>) => void;
  }) => {
    onBidUpdated = options.onBidUpdated;
    return "connected";
  },
}));
vi.mock("@/lib/auth", () => ({
  useIsAuthenticated: () => true,
  useNickname: () => myNickname,
}));
vi.mock("sonner", () => ({
  toast: { warning: toastWarning, success: toastSuccess, error: toastError },
}));

describe("실시간 경매 추월 알림", () => {
  beforeEach(() => {
    toastWarning.mockClear();
    toastSuccess.mockClear();
    toastError.mockClear();
    getBidRequestResult.mockReset();
    getBidRequestResult.mockResolvedValue({
      bidRequestId: 42,
      auctionId: 1,
      bidPrice: 10500,
      status: "PENDING",
    });
  });

  it("내가_최고_입찰자였다가_다른_회원에게_추월당하면_알림을_보여준다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    act(() => {
      onBidUpdated?.({
        latestBid: { bidId: 6, nickname: "다른회원", bidPrice: 10500 },
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

  it("다른_탭이나_기기에서_낸_내_입찰은_추월_알림을_띄우지_않는다", async () => {
    // 이 탭의 pendingBidRequestId와 무관하게(= 다른 탭/기기에서 낸 요청) 도착한 브로드캐스트라도,
    // 닉네임이 나와 같으면 실제로는 내가 더 높게 입찰한 것이다. bidId만 보고 판단하던 예전
    // 로직은 이를 "다른 회원에게 추월당함"으로 오인해 잘못된 경고를 띄웠다.
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    act(() => {
      onBidUpdated?.({
        latestBid: { bidId: 8, nickname: myNickname, bidPrice: 11000 },
        currentPrice: 11000,
        endedAt: null,
      });
    });

    expect(toastWarning).not.toHaveBeenCalled();
  });

  it("자신의_입찰이_그대로_최고가면_알림을_보여주지_않는다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    act(() => {
      onBidUpdated?.({
        latestBid: { bidId: 5, nickname: "me", bidPrice: 10000 },
        currentPrice: 10000,
        endedAt: null,
      });
    });

    expect(toastWarning).not.toHaveBeenCalled();
  });

  it("내_입찰_요청이_성공하면_성공_토스트를_보여주고_추월_토스트는_뜨지_않는다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    act(() => {
      onBidUpdated?.({
        bidRequestId: 42,
        latestBid: { bidId: 7, nickname: "me", bidPrice: 10500 },
        currentPrice: 10500,
        endedAt: null,
      });
    });

    expect(toastSuccess).toHaveBeenCalledWith(
      "입찰 성공",
      expect.objectContaining({
        description: expect.stringContaining("10,500"),
      }),
    );
    expect(toastWarning).not.toHaveBeenCalled();
  });

  it("입찰_결과를_받지_못하면_처리중_상태를_해제한다", async () => {
    vi.useFakeTimers();
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    expect(screen.getByRole("button", { name: "처리 중…" })).toBeDisabled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(toastError).toHaveBeenCalledWith(
      "입찰 결과 확인 지연",
      expect.objectContaining({
        description: expect.stringContaining("입찰 내역을 확인"),
      }),
    );
    expect(screen.getByRole("button", { name: "입찰하기" })).toBeEnabled();
    vi.useRealTimers();
  });

  it("웹소켓_알림이_유실돼도_REST_조회로_입찰_성공을_확인한다", async () => {
    vi.useFakeTimers();
    getBidRequestResult.mockResolvedValue({
      bidRequestId: 42,
      auctionId: 1,
      bidPrice: 10500,
      status: "SUCCEEDED",
    });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });

    expect(getBidRequestResult).toHaveBeenCalledWith("1", 42);
    expect(toastSuccess).toHaveBeenCalledWith(
      "입찰 성공",
      expect.objectContaining({
        description: expect.stringContaining("10,500"),
      }),
    );
    expect(
      screen.queryByRole("button", { name: "처리 중…" }),
    ).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it("웹소켓_알림이_유실돼도_REST_조회로_입찰_실패를_확인한다", async () => {
    vi.useFakeTimers();
    getBidRequestResult.mockResolvedValue({
      bidRequestId: 42,
      auctionId: 1,
      bidPrice: 10500,
      status: "FAILED",
      failureMessage: "포인트가 부족합니다.",
    });
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });

    expect(screen.getByText("포인트가 부족합니다.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "확인" }));
    expect(screen.getByRole("button", { name: "입찰하기" })).toBeEnabled();
    vi.useRealTimers();
  });
});

/** 금액을 입력하고 입찰 확인 다이얼로그까지 열어 입찰 요청을 접수(mutate)한다. */
function submitBidRequest(amount: string) {
  fireEvent.change(screen.getByPlaceholderText(/이상/), {
    target: { value: amount },
  });
  fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "입찰하기" }));
}
