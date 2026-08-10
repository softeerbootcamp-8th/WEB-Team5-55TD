import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({ options }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("@tanstack/react-query", () => ({
  useQuery: () => ({
    data: undefined,
    isPending: true,
    isError: false,
    refetch: vi.fn(),
  }),
}));
vi.mock("@/api/auctions", () => ({ searchAuctions: vi.fn() }));

describe("경매 목록 라우트", () => {
  it("검색·정렬·필터 UI와 로딩 상태를 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/index");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "경매" })).toBeInTheDocument();
    expect(screen.getByPlaceholderText("카드명으로 검색")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "진행 중" })).toBeInTheDocument();
    expect(screen.getByText("경매를 불러오는 중입니다.")).toBeInTheDocument();
  });
});
