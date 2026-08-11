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

let onBidUpdated: ((message: Record<string, unknown>) => void) | undefined;
let onBidRequestFailed: ((message: Record<string, unknown>) => void) | undefined;
const toastWarning = vi.fn();
const toastSuccess = vi.fn();
const toastError = vi.fn();

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
  useQuery: (options: { queryKey: unknown[]; initialData?: unknown }) => {
    // "preview" 입찰 목록 조회에서, 조회자 본인이 현재 최고 입찰자인 상태를 시뮬레이션한다.
    if (options.queryKey.includes("preview")) {
      return {
        data: {
          items: [
            { id: "5", maskedNickname: "me", amount: 10000, isMine: true },
          ],
        },
        isPending: false,
      };
    }
    return { data: options.initialData, isPending: false };
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
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  getBidErrorMessage: vi.fn(),
  createBidRequest: vi.fn(),
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: (options: {
    onBidUpdated: (message: Record<string, unknown>) => void;
    onBidRequestFailed: (message: Record<string, unknown>) => void;
  }) => {
    onBidUpdated = options.onBidUpdated;
    onBidRequestFailed = options.onBidRequestFailed;
  },
}));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => true }));
vi.mock("sonner", () => ({
  toast: { warning: toastWarning, success: toastSuccess, error: toastError },
}));

describe("실시간 경매 추월 알림", () => {
  beforeEach(() => {
    toastWarning.mockClear();
    toastSuccess.mockClear();
    toastError.mockClear();
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

  it("내_입찰_요청이_성공하면_성공_토스트를_보여주고_추월_토스트는_뜨지_않는다", async () => {
    const { Route } = await import(
      "@/routes/_buyer/auctions/$auctionId/live"
    );
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    act(() => {
      onBidUpdated?.({
        bidRequestId: 42,
        latestBid: { bidId: 7, nicknameMasked: "me", bidPrice: 10500 },
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

  it("내_입찰_요청이_실패하면_실패_토스트를_실패_사유와_함께_보여준다", async () => {
    const { Route } = await import(
      "@/routes/_buyer/auctions/$auctionId/live"
    );
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    submitBidRequest("10500");

    act(() => {
      onBidRequestFailed?.({
        bidRequestId: 42,
        auctionId: 1,
        bidPrice: 10500,
        failureCode: "OUTBID_EXISTS",
        failureMessage: "이미 더 높은 입찰이 존재합니다.",
      });
    });

    expect(toastError).toHaveBeenCalledWith(
      "입찰 실패",
      expect.objectContaining({
        description: "이미 더 높은 입찰이 존재합니다.",
      }),
    );
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
