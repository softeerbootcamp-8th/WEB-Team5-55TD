import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

const auction = {
  id: "1",
  cardName: "Charizard",
  status: "ENDED" as const,
  won: true,
  currentPrice: 12000,
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
  notFound: () => new Error("not found"),
}));

describe("경매 종료 라우트", () => {
  it("낙찰 결과를 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "낙찰되었습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
  });
});
