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

let sellerItems: Array<{ id: string; cardName: string; status: string; watchCount: number }> =
  [];
let similarItems: Array<{ id: string; cardName: string; status: string; watchCount: number }> =
  [];

vi.mock("@tanstack/react-query", () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => ({
    data: {
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
  });

  it("두 목록이 모두 비어있으면 아무것도 렌더링하지 않는다", async () => {
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    const { container } = render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("같은 판매자의 다른 경매만 있으면 해당 섹션만 보여준다", async () => {
    sellerItems = [{ id: "10", cardName: "피카츄", status: "ONGOING", watchCount: 0 }];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 판매자의 다른 경매")).toBeInTheDocument();
    expect(screen.queryByText("비슷한 카드 경매")).not.toBeInTheDocument();
  });

  it("비슷한 카드 경매만 있으면 해당 섹션만 보여준다", async () => {
    similarItems = [{ id: "20", cardName: "리자몽 2판", status: "SCHEDULED", watchCount: 0 }];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("비슷한 카드 경매")).toBeInTheDocument();
    expect(screen.queryByText("같은 판매자의 다른 경매")).not.toBeInTheDocument();
  });

  it("두 목록이 모두 있으면 두 섹션을 모두 보여준다", async () => {
    sellerItems = [{ id: "10", cardName: "피카츄", status: "ONGOING", watchCount: 0 }];
    similarItems = [{ id: "20", cardName: "리자몽 2판", status: "SCHEDULED", watchCount: 0 }];
    const { RelatedAuctionsBanner } = await import("./related-auctions-banner");
    render(<RelatedAuctionsBanner auction={baseAuction} />);
    expect(screen.getByText("같은 판매자의 다른 경매")).toBeInTheDocument();
    expect(screen.getByText("비슷한 카드 경매")).toBeInTheDocument();
  });
});
