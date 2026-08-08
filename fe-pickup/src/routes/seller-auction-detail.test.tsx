import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

const auction = {
  id: "4",
  cardName: "Mewtwo",
  status: "LIVE",
  currentPrice: 12000,
  startPrice: 10000,
  sellerNickname: "seller",
  images: ["front.jpg"],
  grade: { agency: "PSA", score: "10" },
  endsAt: new Date(Date.now() + 3600000).toISOString(),
};
let nickname = "seller";
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
vi.mock("@/lib/auth", () => ({ useNickname: () => nickname }));
vi.mock("@/api/auctions", () => ({ getAuctionDetail: vi.fn() }));

describe("셀러 경매 상세", () => {
  it("소유자의 경매 모니터링 정보를 표시한다", async () => {
    const { Route } = await import("@/routes/seller/auctions.$auctionId");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "Mewtwo" })).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
    expect(screen.getByText("입찰 내역")).toBeInTheDocument();
    nickname = "other";
    const guarded = render(<Component />);
    expect(
      guarded.getByText("본인 소유의 경매만 모니터링할 수 있습니다."),
    ).toBeInTheDocument();
  });
});
