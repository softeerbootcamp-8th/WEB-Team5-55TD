import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useLoaderData: () => ({
      auction: {
        id: "1",
        cardName: "Mewtwo",
        status: "LIVE" as const,
        currentPrice: 10000,
        minBidUnit: 500,
        endsAt: new Date(Date.now() + 3600000).toISOString(),
      },
    }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(),
  notFound: () => new Error("not found"),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: undefined,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useMutation: () => ({ mutate: vi.fn(), isPending: false }),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));
vi.mock("@/api/bids", () => ({
  getAuctionBids: vi.fn(),
  placeBid: vi.fn(),
  getBidErrorMessage: vi.fn(),
}));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => true }));

describe("실시간 경매 라우트", () => {
  it("현재가, 최소 입찰가, 입찰 입력을 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "Mewtwo" })).toBeInTheDocument();
    expect(screen.getByText("10,000원")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("10,500 이상")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "입찰하기" }),
    ).toBeInTheDocument();
  });

  it("최소 입찰가보다 낮은 금액은 입찰 버튼을 비활성화한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/live");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    const input = screen.getByPlaceholderText("10,500 이상");
    fireEvent.change(input, { target: { value: "10000" } });
    expect(screen.getByRole("button", { name: "입찰하기" })).toBeDisabled();
  });
});
