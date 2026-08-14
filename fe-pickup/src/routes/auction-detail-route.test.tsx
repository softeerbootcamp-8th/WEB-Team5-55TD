import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ComponentType, ReactNode } from "react";
import { pokemonAvatarForKey } from "@/lib/pokemon-avatars";

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
vi.mock("@/components/domain/related-auctions-banner", () => ({
  RelatedAuctionsBanner: () => null,
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
  sellerProfileImageUrl: "seller-profile.jpg",
  minBidUnit: 500,
  startsAt: "2099-01-01T10:00:00",
  endsAt: "2099-01-01T11:00:00",
  card: { setName: "Base", cardNumber: "1", language: "EN", rarity: "Rare" },
  inspectedAt: "2026-01-01",
  cardState: "HIGH",
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
    expect(
      screen.getByRole("img", { name: "seller 프로필 이미지" }),
    ).toHaveAttribute("src", "seller-profile.jpg");
    expect(screen.getByText("현재가")).toBeInTheDocument();
    expect(screen.getByText("상")).toBeInTheDocument();
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
    auction = { ...base, status: "ENDED", currentPrice: 20000, won: true };
    render(<Component />);
    expect(screen.getByText("낙찰가")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "관심 등록" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: "경매 참여" }),
    ).not.toBeInTheDocument();
  });

  it("유찰된 경매는 낙찰가 대신 유찰로 표시한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    auction = { ...base, status: "ENDED", currentPrice: 20000, won: false };
    render(<Component />);
    expect(screen.getByText("결과")).toBeInTheDocument();
    expect(screen.getByText("유찰")).toBeInTheDocument();
    expect(screen.queryByText("낙찰가")).not.toBeInTheDocument();
    expect(screen.queryByText("20,000원")).not.toBeInTheDocument();
  });

  it("판매자 프로필 이미지가 없으면 포켓몬 아바타로 대체한다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    auction = {
      ...base,
      sellerId: "seller-42",
      sellerProfileImageUrl: undefined,
    };
    render(<Component />);
    expect(
      screen.getByRole("img", { name: "seller 프로필 이미지" }),
    ).toHaveAttribute("src", pokemonAvatarForKey("seller-42"));
  });

  // OOTD-457 은 "썸네일을 누르면 라이트박스가 열린다"는 전제에서 첫 이미지를 줄에서 뺐다.
  // 이제 썸네일 줄은 대표를 고르는 선택기이므로 현재 대표도 줄에 있어야 하고,
  // 대신 같은 사진이 줄 안에서 두 번 나오지 않아야 한다.
  it("썸네일_줄은_모든_이미지를_한_번씩만_보여준다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    const thumbnails = screen.getAllByRole("button", {
      name: /Mewtwo 이미지 \d+/,
    });
    expect(thumbnails).toHaveLength(2);

    const sources = screen
      .getAllByAltText("Mewtwo")
      .map((image) => image.getAttribute("src"));
    // 대표 1장 + 썸네일 2장, 썸네일 안에서는 중복 없음
    expect(sources).toEqual(["front.jpg", "front.jpg", "back.jpg"]);
  });

  it("썸네일을_누르면_대표_사진이_바뀐다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "Mewtwo 이미지 2" }));

    const [main] = screen.getAllByAltText("Mewtwo");
    expect(main).toHaveAttribute("src", "back.jpg");
    expect(
      screen.getByRole("button", { name: "Mewtwo 이미지 2" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("대표_사진을_누르면_확대된다", async () => {
    const { Route } = await import("@/routes/_buyer/auctions/$auctionId/index");
    const Component = Route.options.component as ComponentType;
    render(<Component />);

    fireEvent.click(screen.getByRole("button", { name: "Mewtwo 이미지 확대" }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
