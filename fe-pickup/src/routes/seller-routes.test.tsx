import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

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
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: undefined,
    isPending: true,
    isError: false,
    refetch: vi.fn(),
  }),
}));
vi.mock("@/api/consignments", () => ({ getMyConsignments: vi.fn() }));
vi.mock("@/api/sales", () => ({ getMySalesHistory: vi.fn() }));
vi.mock("@/api/seller-stats", () => ({ getMySellerStats: vi.fn() }));

describe("셀러 라우트 초기 상태", () => {
  it("셀러 홈은 통계와 목록 영역을 표시한다", async () => {
    const { Route } = await import("@/routes/seller/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "셀러 홈" }),
    ).toBeInTheDocument();
    expect(screen.getByText("등록 상품")).toBeInTheDocument();
    expect(screen.getByText("경매 예정")).toBeInTheDocument();
  });

  it("상품·판매 내역 페이지는 로딩 상태를 표시한다", async () => {
    const products = await import("@/routes/seller/products/index");
    const sales = await import("@/routes/seller/sales");
    const Products = products.Route.options.component as React.ComponentType;
    const Sales = sales.Route.options.component as React.ComponentType;
    const productView = render(<Products />);
    expect(
      screen.getByRole("heading", { name: "상품 목록" }),
    ).toBeInTheDocument();
    productView.unmount();
    render(<Sales />);
    expect(
      screen.getByRole("heading", { name: "판매 내역" }),
    ).toBeInTheDocument();
  });
});
