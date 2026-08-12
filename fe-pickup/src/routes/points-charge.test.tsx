import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const balance = {
  pointBalance: 500_000,
  reservedPointBalance: 0,
  availablePointBalance: 500_000,
};

const transactionsPage = {
  hasNext: false,
  cursor: null,
  size: 20,
  items: [
    {
      pointTransactionId: 1,
      transactionType: "CHARGE",
      amount: 300_000,
      balanceAfter: 500_000,
      auctionId: null,
      createdAt: "2026-01-01T00:00:00",
    },
  ],
};

const invalidateQueries = vi.fn();
const chargeMyPoint = vi.fn();
let lastMutate:
  | {
      variables: unknown;
      callbacks?: {
        onSuccess?: (data: unknown) => void;
        onError?: (error: unknown) => void;
      };
    }
  | undefined;

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useInfiniteQuery: () => ({
    data: { pages: [transactionsPage] },
    isPending: false,
    isError: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
    refetch: vi.fn(),
  }),
  useMutation: () => ({
    mutate: (
      variables: unknown,
      callbacks?: {
        onSuccess?: (data: unknown) => void;
        onError?: (error: unknown) => void;
      },
    ) => {
      lastMutate = { variables, callbacks };
    },
    isPending: false,
  }),
  useQueryClient: () => ({ invalidateQueries }),
}));
vi.mock("@/api/generated/member/member", () => ({
  useGetMyPointBalance: () => ({
    data: balance,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  getMyPointTransactions: vi.fn(),
  chargeMyPoint: (...args: unknown[]) => chargeMyPoint(...args),
  getGetMyPointBalanceQueryKey: () => ["/members/me/points"],
  getGetMyPointTransactionsQueryKey: () => ["/members/me/point-transactions"],
}));

describe("포인트 충전", () => {
  beforeEach(() => {
    invalidateQueries.mockClear();
    chargeMyPoint.mockClear();
    lastMutate = undefined;
  });

  it("프리셋을_선택하고_충전하기를_누르면_확인_모달에_금액이_표시된다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "300,000P" }));
    fireEvent.click(screen.getByRole("button", { name: "충전하기" }));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("충전 확인")).toBeInTheDocument();
    expect(within(dialog).getByText("300,000P")).toBeInTheDocument();
  });

  it("확인을_누르면_idempotencyKey와_함께_뮤테이션을_호출한다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "300,000P" }));
    fireEvent.click(screen.getByRole("button", { name: "충전하기" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "충전하기" }));

    expect(lastMutate?.variables).toMatchObject({ amount: 300_000 });
    expect(
      (lastMutate?.variables as { idempotencyKey: string }).idempotencyKey,
    ).toBeTruthy();
  });

  it("충전이_성공하면_잔액과_거래내역_쿼리를_무효화한다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "300,000P" }));
    fireEvent.click(screen.getByRole("button", { name: "충전하기" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "충전하기" }));

    act(() => {
      lastMutate?.callbacks?.onSuccess?.({});
    });

    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ["/members/me/points"],
    });
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ["/members/me/point-transactions"],
    });
  });

  it("충전이_실패하면_실패_모달에_서버_메시지를_보여준다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "300,000P" }));
    fireEvent.click(screen.getByRole("button", { name: "충전하기" }));
    const dialog = screen.getByRole("dialog");
    fireEvent.click(within(dialog).getByRole("button", { name: "충전하기" }));

    act(() => {
      lastMutate?.callbacks?.onError?.(new Error("boom"));
    });

    expect(screen.getByText("충전 실패")).toBeInTheDocument();
  });

  it("최소금액보다_적은_금액을_입력하면_확인_모달이_열리지_않는다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.change(screen.getByPlaceholderText(/이상/), {
      target: { value: "1000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전하기" }));

    expect(screen.queryByText("충전 확인")).not.toBeInTheDocument();
    expect(screen.getByText("충전 실패")).toBeInTheDocument();
  });

  it("CHARGE_거래는_거래내역에_포인트_충전으로_표시된다", async () => {
    const { Route } = await import("@/routes/_buyer/points");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    const transactionList = screen.getByRole("list");
    expect(
      within(transactionList).getByText("포인트 충전"),
    ).toBeInTheDocument();
  });
});
