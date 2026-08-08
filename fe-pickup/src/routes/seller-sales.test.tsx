import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType } from "react";

let search = { tab: undefined as string | undefined };
let queries: Array<Record<string, unknown>> = [];
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useSearch: () => search,
  }),
  useNavigate: () => vi.fn(),
}));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => queries.shift() }));
vi.mock("@/api/sales", () => ({ getMySalesHistory: vi.fn() }));

describe("셀러 판매 내역", () => {
  it("판매 내역 로딩·오류·낙찰 목록을 표시한다", async () => {
    const { Route } = await import("@/routes/seller/sales");
    const Component = Route.options.component as ComponentType;
    queries = [{ isPending: true }, { isPending: true }, { isPending: true }];
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "판매 내역" }),
    ).toBeInTheDocument();
    cleanup();
    queries = [
      { isPending: false, isError: true },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
    ];
    render(<Component />);
    expect(
      screen.getByText("판매 내역을 불러오지 못했습니다."),
    ).toBeInTheDocument();
    cleanup();
    queries = [
      {
        isPending: false,
        isError: false,
        data: [
          {
            auctionId: "1",
            cardName: "Pikachu",
            resultType: "WON",
            finalPrice: 30000,
            grade: { agency: "PSA", score: "10" },
          },
        ],
      },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
    ];
    render(<Component />);
    expect(screen.getAllByText("Pikachu").length).toBeGreaterThan(0);
    expect(screen.getByText("30,000원")).toBeInTheDocument();
  });
});
