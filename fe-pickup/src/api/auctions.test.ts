import { describe, expect, it, vi } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({
  axiosInstance: { get, post },
}));

const card = {
  cardId: 1,
  cardName: "Mewtwo",
  setName: "Base",
  cardNumber: "1",
  language: "JP",
  rarity: "Rare",
  imageUrl: "card.jpg",
};

describe("auctions api", () => {
  it("경매 목록과 등록 응답을 UI 모델로 변환한다", async () => {
    get.mockResolvedValueOnce({
      data: {
        hasNext: false,
        items: [
          {
            auctionId: 4,
            card,
            grade: "PSA 10",
            auctionStatus: "ONGOING",
            startingPrice: 1000,
            currentPrice: 2000,
            watchCount: 2,
            watched: true,
          },
        ],
      },
    });
    post.mockResolvedValue({ data: { auctionId: 9, bidIncrement: 500 } });
    const api = await import("@/api/auctions");
    await expect(
      api.searchAuctions({ status: ["ONGOING"], sort: "POPULAR" }),
    ).resolves.toMatchObject({
      hasNext: false,
      items: [
        {
          id: "4",
          cardName: "Mewtwo",
          status: "LIVE",
          currentPrice: 2000,
          grade: { agency: "PSA", score: "10" },
        },
      ],
    });
    await expect(
      api.registerAuction({
        consignmentId: "3",
        startingPrice: 1000,
        reserve: 1500,
        scheduledStartAt: "2026-08-01T01:00:00Z",
      }),
    ).resolves.toEqual({ auctionId: "9", bidIncrement: 500 });
    expect(post).toHaveBeenCalledWith(
      "/auctions",
      expect.objectContaining({ consignmentId: 3 }),
    );
  });

  it("featured 404는 null로 처리하고 상세 응답을 매핑한다", async () => {
    get.mockRejectedValueOnce({ response: { status: 404 } });
    const api = await import("@/api/auctions");
    await expect(api.getFeaturedAuction()).resolves.toBeNull();
    get.mockResolvedValueOnce({
      data: {
        auctionId: 7,
        card,
        auctionStatus: "WON",
        startingPrice: 10000,
        watchCount: 0,
        watched: false,
        images: [{ imageUrl: "front.jpg" }],
        sellerProfileImageUrl: "seller-profile.jpg",
        certificate: {
          serialNumber: "S1",
          certificationBody: "BGS",
          grade: "9",
          inspectedAt: "2026-01-01",
        },
        bidIncrement: 1000,
        myBidWon: true,
      },
    });
    await expect(api.getAuctionDetail("7")).resolves.toMatchObject({
      id: "7",
      won: true,
      myBidWon: true,
      minBidUnit: 1000,
      images: ["front.jpg"],
      sellerProfileImageUrl: "seller-profile.jpg",
      grade: { agency: "BGS", score: "9", serial: "S1" },
    });
  });

  it("조회자가 낙찰자가 아니면 myBidWon이 false로 매핑된다", async () => {
    const api = await import("@/api/auctions");
    get.mockResolvedValueOnce({
      data: {
        auctionId: 8,
        card,
        auctionStatus: "WON",
        startingPrice: 10000,
        watchCount: 0,
        watched: false,
        images: [],
        certificate: {
          serialNumber: "S2",
          certificationBody: "BGS",
          grade: "9",
          inspectedAt: "2026-01-01",
        },
        bidIncrement: 1000,
        myBidWon: false,
      },
    });
    await expect(api.getAuctionDetail("8")).resolves.toMatchObject({
      id: "8",
      won: true,
      myBidWon: false,
    });
  });

  it("상세 API가 목록 형식이면 기본 상세 모델로 변환한다", async () => {
    const api = await import("@/api/auctions");
    get.mockResolvedValueOnce({
      data: {
        auctionId: 11,
        card,
        auctionStatus: "SCHEDULED",
        startingPrice: 20000,
        thumbnailUrl: "thumb.jpg",
        watchCount: 1,
        watched: false,
      },
    });
    await expect(api.getAuctionDetail("11")).resolves.toMatchObject({
      id: "11",
      status: "UPCOMING",
      minBidUnit: 1000,
      images: ["thumb.jpg", "card.jpg"],
      won: false,
      myBidWon: false,
    });
  });

  it("상세 API가 404면 목록에서 경매를 찾아 보완한다", async () => {
    const api = await import("@/api/auctions");
    get
      .mockRejectedValueOnce({ response: { status: 404 } })
      .mockResolvedValueOnce({
        data: {
          hasNext: false,
          items: [
            {
              auctionId: 12,
              card,
              auctionStatus: "PASSED",
              startingPrice: 30000,
              thumbnailUrl: "thumb.jpg",
              watchCount: 0,
              watched: false,
            },
          ],
        },
      });
    await expect(api.getAuctionDetail("12")).resolves.toMatchObject({
      id: "12",
      status: "ENDED",
      minBidUnit: 1500,
      images: ["thumb.jpg", "card.jpg"],
      won: false,
      myBidWon: false,
    });
  });

  it("상세 API가 404여도 목록의 낙찰 여부를 그대로 반영한다", async () => {
    const api = await import("@/api/auctions");
    get
      .mockRejectedValueOnce({ response: { status: 404 } })
      .mockResolvedValueOnce({
        data: {
          hasNext: false,
          items: [
            {
              auctionId: 13,
              card,
              auctionStatus: "WON",
              startingPrice: 30000,
              currentPrice: 50000,
              thumbnailUrl: "thumb.jpg",
              watchCount: 0,
              watched: false,
            },
          ],
        },
      });
    await expect(api.getAuctionDetail("13")).resolves.toMatchObject({
      id: "13",
      status: "ENDED",
      currentPrice: 50000,
      won: true,
    });
  });
});
