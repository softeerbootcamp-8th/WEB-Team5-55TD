import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const navigate = vi.fn();
const auction = {
  id: "1",
  cardName: "Pikachu",
  status: "UPCOMING" as const,
  startsAt: "2026-08-08T15:00:00Z",
  startPrice: 10000,
  thumbnailUrl: undefined,
};

vi.mock("@tanstack/react-router", () => ({
  createFileRoute: () => (options: Record<string, unknown>) => ({
    options,
    useLoaderData: () => ({ auction }),
  }),
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
  useNavigate: () => navigate,
  notFound: () => new Error("not found"),
}));
vi.mock("@/api/generated/member/member", () => ({
  useGetMyPointBalance: () => ({
    isLoading: false,
    data: {
      pointBalance: 50000,
      reservedPointBalance: 0,
      availablePointBalance: 50000,
    },
  }),
}));
vi.mock("@/lib/auth", () => ({ useIsAuthenticated: () => true }));

describe("경매 대기 라우트", () => {
  it("시작 전 경매 정보와 포인트를 표시한다", async () => {
    const { Route } =
      await import("@/routes/_buyer/auctions/$auctionId/waiting");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByText("경매 대기")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Pikachu" }),
    ).toBeInTheDocument();
    expect(screen.getByText("50,000P")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "시작 전 · 입찰 대기 중" }),
    ).toBeInTheDocument();
  });
});
