import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queries: Array<Record<string, unknown>> = [];
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => queries.shift() }));
vi.mock("@/api/consignments", () => ({ getMyConsignments: vi.fn() }));
vi.mock("@/api/sales", () => ({ getMySalesHistory: vi.fn() }));
vi.mock("@/api/seller-stats", () => ({ getMySellerStats: vi.fn() }));

describe("셀러 홈", () => {
  it("통계와 진행 중·최근 낙찰 상품을 표시한다", async () => {
    queries = [
      { data: { registered: 1, scheduled: 2, ongoing: 3, sold: 4 } },
      {
        isPending: false,
        isError: false,
        data: [{ id: "1", cardName: "Mewtwo", auctionId: "9" }],
      },
      {
        isPending: false,
        isError: false,
        data: [
          {
            auctionId: "2",
            cardName: "Pikachu",
            resultType: "WON",
            finalPrice: 20000,
          },
        ],
      },
    ];
    const { Route } = await import("@/routes/seller/index");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByText("셀러 홈")).toBeInTheDocument();
    expect(screen.getAllByText("Mewtwo").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Pikachu").length).toBeGreaterThan(0);
    expect(screen.getByText("모니터링")).toBeInTheDocument();
    cleanup();
    queries = [
      { data: undefined },
      { isPending: false, isError: true },
      { isPending: false, isError: true },
    ];
    render(<Component />);
    expect(
      screen.getByText("진행 중인 경매를 불러오지 못했습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("최근 낙찰 상품을 불러오지 못했습니다."),
    ).toBeInTheDocument();
  });
});
