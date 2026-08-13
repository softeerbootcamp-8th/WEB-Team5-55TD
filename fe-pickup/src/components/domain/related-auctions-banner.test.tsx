import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import type { AuctionDetailView } from "@/api/auctions";

vi.mock("@tanstack/react-router", () => ({
  Link: ({ children, ...props }: { children: ReactNode }) => (
    <a {...props}>{children}</a>
  ),
}));
vi.mock("./heart-button", () => ({
  HeartButton: () => <button aria-label="관심 등록" type="button" />,
  WatchButton: () => <button aria-label="관심 등록" type="button" />,
}));
vi.mock("@/api/auctions", () => ({
  searchAuctions: vi.fn(),
}));

let sellerItems: Array<{
  id: string;
  cardName: string;
  status: string;
  watchCount: number;
}> = [];
let similarItems: Array<{
  id: string;
  cardName: string;
  status: string;
  watchCount: number;
}> = [];

let isPending = false;

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => ({
    isPending,
    data: isPending
      ? undefined
      : {
          items: queryKey[1] === "seller-other" ? sellerItems : similarItems,
          hasNext: false,
        },
  }),
}));

const baseAuction = {
  id: "1",
  cardName: "리자몽",
  status: "ONGOING",
  sellerId: "42",
  sellerNickname: "카드마스터샵",
  card: {
    cardId: 7,
    cardName: "리자몽",
    setName: "Base Set",
    cardNumber: "4/102",
    language: "일본어",
    rarity: "MINT",
  },
} as unknown as AuctionDetailView;

describe("RelatedAuctionsBanner", () => {
  beforeEach(() => {
    sellerItems = [];
    similarItems = [];
    isPending = false;
  });

  it("같은 카드의 다른 경매가 없으면 빈 상태를 안내한다", async () => {
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 카드의 다른 경매")).toBeInTheDocument();
    expect(
      screen.getByText("같은 카드의 다른 경매가 없습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("같은 판매자의 다른 경매"),
    ).not.toBeInTheDocument();
  });

  it("조회 중에는 빈 상태를 먼저 보여주지 않는다", async () => {
    isPending = true;
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 카드의 다른 경매")).toBeInTheDocument();
    expect(
      screen.queryByText("같은 카드의 다른 경매가 없습니다."),
    ).not.toBeInTheDocument();
  });

  it("같은 판매자의 다른 경매만 있으면 판매자 섹션과 빈 상태를 함께 보여준다", async () => {
    sellerItems = [
      { id: "10", cardName: "피카츄", status: "ONGOING", watchCount: 0 },
    ];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 판매자의 다른 경매")).toBeInTheDocument();
    expect(
      screen.getByText("같은 카드의 다른 경매가 없습니다."),
    ).toBeInTheDocument();
  });

  it("같은 카드의 다른 경매가 있으면 목록을 보여준다", async () => {
    similarItems = [
      { id: "20", cardName: "리자몽 2판", status: "SCHEDULED", watchCount: 0 },
    ];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(
      screen.getByRole("heading", { name: "리자몽 2판" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("같은 카드의 다른 경매가 없습니다."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("같은 판매자의 다른 경매"),
    ).not.toBeInTheDocument();
  });

  it("두 목록이 모두 있으면 두 섹션을 모두 보여준다", async () => {
    sellerItems = [
      { id: "10", cardName: "피카츄", status: "ONGOING", watchCount: 0 },
    ];
    similarItems = [
      { id: "20", cardName: "리자몽 2판", status: "SCHEDULED", watchCount: 0 },
    ];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 판매자의 다른 경매")).toBeInTheDocument();
    expect(screen.getByText("같은 카드의 다른 경매")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "피카츄" })).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "리자몽 2판" }),
    ).toBeInTheDocument();
  });
});
