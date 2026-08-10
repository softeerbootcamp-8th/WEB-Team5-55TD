import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("./heart-button", () => ({
  HeartButton: () => <button aria-label="관심 등록" type="button" />,
  WatchButton: () => <button aria-label="관심 등록" type="button" />,
}));

const base = { id: "a1", cardName: "Charizard", watchCount: 10 };

describe("AuctionCard", () => {
  it.each([
    ["LIVE", "현재가", "12,000원"],
    ["UPCOMING", "시작가", "5,000원"],
    ["ENDED", "낙찰가", "20,000원"],
  ])("%s 상태의 가격 정보를 표시한다", async (status, label, price) => {
    const { AuctionCard } = await import("./auction-card");
    render(
      <AuctionCard
        auction={{
          ...base,
          status: status as "LIVE" | "UPCOMING" | "ENDED",
          currentPrice: status === "ENDED" ? 20000 : 12000,
          startPrice: 5000,
          endsAt: new Date(Date.now() + 3600000).toISOString(),
          startsAt: new Date(Date.now() + 3600000).toISOString(),
        }}
      />,
    );
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.getByText(price)).toBeInTheDocument();
  });

  it("입찰자가 없던 종료 경매는 유찰로 표시한다", async () => {
    const { AuctionCard } = await import("./auction-card");
    render(<AuctionCard auction={{ ...base, status: "ENDED" }} />);
    expect(screen.getByText("유찰")).toBeInTheDocument();
  });
});
