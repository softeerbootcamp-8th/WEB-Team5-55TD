import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const auction = {
  id: "1",
  cardName: "Mewtwo",
  status: "LIVE" as const,
  currentPrice: 10000,
  startPrice: 10000,
  minBidUnit: 500,
  endsAt: "2099-01-01T11:00:00",
  sellerNickname: "seller",
  sellerProfileImageUrl: "seller-profile.jpg",
};

let myNickname: string | null = "collector88";

const mutate = vi.fn(
  (
    price: number,
    options?: { onSuccess?: (placed: Record<string, unknown>) => void },
  ) => {
    options?.onSuccess?.({
      bidRequestId: 42,
      auctionId: 1,
      memberId: 2,
      bidPrice: price,
      status: "PENDING",
      createdAt: "2026-01-01T00:00:00",
    });
  },
);

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
  useInfiniteQuery: () => ({
    data: { pages: [{ items: [], hasNext: false }] },
    isPending: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  }),
  useMutation: () => ({ mutate, isPending: false }),
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
  getBidRequestResult: vi.fn().mockResolvedValue({ status: "PENDING" }),
}));
vi.mock("@/hooks/use-auction-bid-updates", () => ({
  useAuctionBidUpdates: () => "connected",
}));
vi.mock("@/lib/auth", () => ({
  useIsAuthenticated: () => true,
  useNickname: () => myNickname,
}));
vi.mock("sonner", () => ({
  toast: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
}));

async function renderLivePage() {
  const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
  const Component = Route.options.component as ComponentType;
  render(<Component />);
}

describe("입찰 확인 팝업 다시 보지 않기", () => {
  beforeEach(() => {
    mutate.mockClear();
    localStorage.clear();
  });
  afterEach(() => {
    localStorage.clear();
  });

  it("판매자_프로필_이미지를_보여준다", async () => {
    await renderLivePage();

    expect(
      screen.getByRole("img", { name: "seller 프로필 이미지" }),
    ).toHaveAttribute("src", "seller-profile.jpg");
  });

  it("다시_보지_않기를_체크하고_확인하면_저장되고_입찰이_접수된다", async () => {
    const { shouldSkipBidConfirm } =
      await import("@/lib/bid-confirm-preference");
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "10500" },
    });
    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));

    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByLabelText("다시 보지 않기"));
    fireEvent.click(within(dialog).getByRole("button", { name: "입찰하기" }));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith(10500, expect.anything());
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(shouldSkipBidConfirm()).toBe(true);
  });

  it("체크하지_않고_확인하면_다시_보지_않기_설정이_저장되지_않는다", async () => {
    const { shouldSkipBidConfirm } =
      await import("@/lib/bid-confirm-preference");
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "10500" },
    });
    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "입찰하기" }));

    expect(mutate).toHaveBeenCalledTimes(1);
    expect(shouldSkipBidConfirm()).toBe(false);
  });

  it("이미_다시_보지_않기가_저장되어_있으면_확인_팝업을_띄우지_않고_바로_입찰한다", async () => {
    const { setSkipBidConfirm } = await import("@/lib/bid-confirm-preference");
    setSkipBidConfirm(true);
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "10500" },
    });
    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(mutate).toHaveBeenCalledTimes(1);
    expect(mutate).toHaveBeenCalledWith(10500, expect.anything());
  });

  it("취소를_누르면_체크박스_상태가_초기화된다", async () => {
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "10500" },
    });
    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));
    let dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByLabelText("다시 보지 않기"));
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));
    dialog = screen.getByRole("dialog");
    expect(within(dialog).getByLabelText("다시 보지 않기")).not.toBeChecked();
    expect(mutate).not.toHaveBeenCalled();
  });
});

describe("입찰 금액 + 버튼", () => {
  beforeEach(() => {
    mutate.mockClear();
    localStorage.clear();
  });

  it("입력된_금액에_최소_입찰_단위만큼_더한다", async () => {
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "10500" },
    });
    fireEvent.click(screen.getByRole("button", { name: /최소 입찰 단위/ }));

    expect(screen.getByPlaceholderText(/이상/)).toHaveValue("11,000");
  });

  it("입력한_금액에_3자리_콤마를_다시_매긴다", async () => {
    await renderLivePage();
    const input = screen.getByPlaceholderText(/이상/);

    fireEvent.change(input, { target: { value: "1234567" } });
    expect(input).toHaveValue("1,234,567");

    // 아무 자리에나 찍은 콤마도 제자리로 돌아온다.
    fireEvent.change(input, { target: { value: "1,2,3,4,5,6,7" } });
    expect(input).toHaveValue("1,234,567");

    // 숫자가 아닌 문자는 들어가지 않는다.
    fireEvent.change(input, { target: { value: "12a34" } });
    expect(input).toHaveValue("1,234");
  });

  it("콤마가_붙은_금액도_그대로_입찰에_쓴다", async () => {
    await renderLivePage();

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "1,2,3,4,5,6" },
    });
    expect(screen.getByPlaceholderText(/이상/)).toHaveValue("123,456");

    fireEvent.click(screen.getByRole("button", { name: "입찰하기" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "입찰하기" }));

    expect(mutate).toHaveBeenCalledWith(123456, expect.anything());
  });

  it("입력값이_없으면_현재가에_최소_입찰_단위를_더한_값으로_채운다", async () => {
    await renderLivePage();

    fireEvent.click(screen.getByRole("button", { name: /최소 입찰 단위/ }));

    expect(screen.getByPlaceholderText(/이상/)).toHaveValue("10,500");
  });
});

describe("자신이 올린 경매", () => {
  beforeEach(() => {
    mutate.mockClear();
    localStorage.clear();
  });
  afterEach(() => {
    myNickname = "collector88";
  });

  it("셀러_본인이면_입찰_관련_조작을_모두_막는다", async () => {
    myNickname = "seller";
    await renderLivePage();

    expect(screen.getByRole("button", { name: "입찰하기" })).toBeDisabled();
    expect(screen.getByPlaceholderText(/이상/)).toBeDisabled();
    expect(
      screen.getByRole("button", { name: /최소 입찰 단위/ }),
    ).toBeDisabled();
    expect(
      screen.getByText("자신이 등록한 경매에는 입찰할 수 없습니다."),
    ).toBeInTheDocument();
  });

  it("셀러_본인이면_입찰가_옆에_자신의_상품임을_표시한다", async () => {
    myNickname = "seller";
    await renderLivePage();

    expect(screen.getByText("자신의 상품")).toBeInTheDocument();
  });

  it("다른_회원이면_입찰가_옆에_문구를_보여주지_않는다", async () => {
    await renderLivePage();

    expect(screen.queryByText("자신의 상품")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/이상/)).not.toBeDisabled();
  });

  it("닉네임을_모르면_막지_않는다", async () => {
    myNickname = null;
    await renderLivePage();

    expect(screen.queryByText("자신의 상품")).not.toBeInTheDocument();
    expect(screen.getByPlaceholderText(/이상/)).not.toBeDisabled();
  });
});
