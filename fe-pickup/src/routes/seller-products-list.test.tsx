import { cleanup, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let queries: Array<Record<string, unknown>> = [];
vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useSearch: () => ({}),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => vi.fn(),
}));
vi.mock("@tanstack/react-query", () => ({ useQuery: () => queries.shift() }));
vi.mock("@/api/consignments", () => ({ getMyConsignments: vi.fn() }));

const product = {
  id: "1",
  cardName: "Charizard",
  status: "REGISTERABLE",
  grade: { agency: "PSA", score: "10", serial: "A" },
};
describe("셀러 상품 목록", () => {
  it("상품 상태별 목록·오류·빈 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/seller/products/index");
    const Component = Route.options.component as ComponentType;
    queries = [
      {
        isPending: false,
        isError: false,
        data: [
          product,
          {
            ...product,
            id: "2",
            cardName: "Pikachu",
            auctionTitle: "피카츄 특별 경매",
            status: "SOLD",
            auctionId: "9",
          },
        ],
      },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: true },
      { isPending: false, isError: false, data: [] },
    ];
    render(<Component />);
    expect(screen.getAllByText("Charizard").length).toBeGreaterThan(0);
    expect(screen.getByText("피카츄 특별 경매")).toBeInTheDocument();
    expect(screen.getAllByText("Pikachu").length).toBeGreaterThan(0);
    expect(screen.getByText("상세 보기")).toBeInTheDocument();
    expect(screen.getByText("낙찰 상세")).toBeInTheDocument();
    cleanup();
    queries = [
      { isPending: false, isError: true },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
    ];
    render(<Component />);
    expect(screen.getByText("상품을 불러오지 못했습니다.")).toBeInTheDocument();
    cleanup();
    queries = [
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
      { isPending: false, isError: false, data: [] },
    ];
    render(<Component />);
    expect(
      screen.getByText("해당 상태의 상품이 없습니다."),
    ).toBeInTheDocument();
  });
});
