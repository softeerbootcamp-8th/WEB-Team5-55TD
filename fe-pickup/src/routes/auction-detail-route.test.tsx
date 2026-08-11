import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";

let auction: Record<string, unknown>;
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
vi.mock("@/components/domain/heart-button", () => ({
  WatchButton: () => <button type="button">관심 등록</button>,
}));
vi.mock("@/components/domain/image-lightbox", () => ({
  ImageLightbox: ({ alt }: { alt: string }) => (
    <div role="dialog">{alt} 확대</div>
  ),
}));
vi.mock("@/components/domain/market-price-chart", () => ({
  MarketPriceChart: () => null,
}));

const base = {
  id: "1",
  cardName: "Mewtwo",
  grade: { agency: "PSA", score: "10", serial: "A" },
  watchCount: 2,
  watched: false,
  thumbnailUrl: "thumb.jpg",
  images: ["front.jpg", "back.jpg"],
  sellerNickname: "seller",
  minBidUnit: 500,
  startsAt: "2099-01-01T10:00:00",
  endsAt: "2099-01-01T11:00:00",
  card: { setName: "Base", cardNumber: "1", language: "EN", rarity: "Rare" },
  inspectedAt: "2026-01-01",
  cardState: "NM",
  majorDefect: "없음",
};

describe("구매자 경매 상세", () => {
  beforeEach(() => {
    auction = { ...base, status: "LIVE", currentPrice: 10000 };
  });
  it("진행 중·예정·종료 가격 상태와 상세 정보를 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    render(<Component />);
    expect(screen.getByRole("heading", { name: "Mewtwo" })).toBeInTheDocument();
    expect(screen.getByText("현재가")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button")[0]);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    cleanup();
    auction = {
      ...base,
      status: "UPCOMING",
      startPrice: 5000,
      currentPrice: undefined,
    };
    render(<Component />);
    expect(screen.getByText("시작가")).toBeInTheDocument();
    cleanup();
    auction = { ...base, status: "ENDED", currentPrice: 20000 };
    render(<Component />);
    expect(screen.getByText("낙찰가")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "경매 참여" }),
    ).not.toBeInTheDocument();
  });
});
