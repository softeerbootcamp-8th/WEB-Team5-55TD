import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

function auctionOf(
  won: boolean,
  myBidWon: boolean,
  winnerNicknameMasked?: string,
) {
  return {
    id: "1",
    cardName: "Charizard",
    status: "ENDED" as const,
    won,
    myBidWon,
    currentPrice: 12000,
    thumbnailUrl: undefined,
    winnerNicknameMasked,
  };
}

let auction = auctionOf(true, true);

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
  it("조회자 본인이 낙찰자면 낙찰되었습니다를 표시한다", async () => {
    auction = auctionOf(true, true);
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "낙찰되었습니다" }),
    ).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
  });

  it("낙찰됐지만_조회자_본인이_낙찰자가_아니면_낙찰되었습니다를_표시하지_않는다", async () => {
    auction = auctionOf(true, false);
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "낙찰자가 결정되었습니다" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "낙찰되었습니다" }),
    ).not.toBeInTheDocument();
  });

  it("유찰된_경매는_유찰되었습니다를_표시한다", async () => {
    auction = auctionOf(false, false);
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "유찰되었습니다" }),
    ).toBeInTheDocument();
  });

  it("본인이_낙찰자면_낙찰자_행에_나를_표시한다", async () => {
    auction = auctionOf(true, true, "닉***임");
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByText("낙찰자")).toBeInTheDocument();
    expect(screen.getByText("나")).toBeInTheDocument();
  });

  it("다른_회원이_낙찰자면_마스킹된_닉네임을_표시한다", async () => {
    auction = auctionOf(true, false, "닉***임");
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.getByText("낙찰자")).toBeInTheDocument();
    expect(screen.getByText("닉***임")).toBeInTheDocument();
  });

  it("유찰된_경매는_낙찰자_행을_표시하지_않는다", async () => {
    auction = auctionOf(false, false);
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(screen.queryByText("낙찰자")).not.toBeInTheDocument();
  });
});
