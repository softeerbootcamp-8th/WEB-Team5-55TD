import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("./heart-button", () => ({
  HeartButton: () => (
    <button data-testid="heart" aria-label="관심 등록" type="button" />
  ),
  WatchButton: ({ watched }: { watched: boolean }) => (
    <button
      data-testid="watch"
      aria-label={watched ? "관심 해제" : "관심 등록"}
      type="button"
    />
  ),
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

  it("판매자 닉네임을 표시한다", async () => {
    const { AuctionCard } = await import("./auction-card");
    render(
      <AuctionCard
        auction={{ ...base, status: "LIVE", sellerNickname: "카드왕" }}
      />,
    );
    expect(screen.getByText("판매자 · 카드왕")).toBeInTheDocument();
  });

  it("판매자 닉네임이 없으면 판매자 줄을 그리지 않는다", async () => {
    const { AuctionCard } = await import("./auction-card");
    render(<AuctionCard auction={{ ...base, status: "LIVE" }} />);
    expect(screen.queryByText(/판매자 ·/)).not.toBeInTheDocument();
  });

  it("입찰자가 없던 종료 경매는 유찰로 표시한다", async () => {
    const { AuctionCard } = await import("./auction-card");
    render(<AuctionCard auction={{ ...base, status: "ENDED" }} />);
    expect(screen.getByText("유찰")).toBeInTheDocument();
  });

  it("종료 경매에는 관심 수와 하트 버튼을 표시하지 않는다", async () => {
    const { AuctionCard } = await import("./auction-card");
    render(
      <AuctionCard
        auction={{ ...base, status: "ENDED", currentPrice: 20000 }}
      />,
    );

    expect(screen.queryByTestId("watch")).not.toBeInTheDocument();
    expect(screen.queryByText("10")).not.toBeInTheDocument();
  });

  it.each([
    ["watched 값이 없어도", undefined, "관심 등록"],
    ["관심 등록된 경매는", true, "관심 해제"],
  ])("%s 서버 연동 하트를 렌더한다", async (_case, watched, label) => {
    const { AuctionCard } = await import("./auction-card");
    render(
      <AuctionCard
        auction={{ ...base, status: "LIVE", watchCount: 328, watched }}
      />,
    );
    expect(screen.getByTestId("watch")).toBeInTheDocument();
    expect(screen.queryByTestId("heart")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
  });
});
