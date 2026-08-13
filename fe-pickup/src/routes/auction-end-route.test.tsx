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
  it("조회자 본인이 낙찰자면 축하합니다와 카드명 문구를 표시한다", async () => {
    auction = auctionOf(true, true, "닉***임");
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "축하합니다!" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Charizard, 넌 내 거야!")).toBeInTheDocument();
    expect(screen.getByText("12,000원")).toBeInTheDocument();
  });

  it("다른 회원이 낙찰자면 닉네임님 낙찰을 헤드라인에 표시한다", async () => {
    auction = auctionOf(true, false, "닉***임");
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "닉***임님 낙찰!" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "축하합니다!" }),
    ).not.toBeInTheDocument();
  });

  it("낙찰자_닉네임_정보가_없으면_낙찰자가_결정되었습니다를_표시한다", async () => {
    auction = auctionOf(true, false);
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/end");
    const Component = Route.options.component as React.ComponentType;
    render(<Component />);
    expect(
      screen.getByRole("heading", { name: "낙찰자가 결정되었습니다" }),
    ).toBeInTheDocument();
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
});
