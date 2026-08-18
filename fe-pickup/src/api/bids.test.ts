import { AxiosError } from "axios";
import { describe, expect, it, vi } from "vitest";

const { post, get } = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({
  axiosInstance: { post, get },
}));

describe("bids api", () => {
  it("입찰자의 프로필 이미지 URL을 화면 모델로 변환한다", async () => {
    const { getAuctionBids } = await import("@/api/bids");
    get.mockResolvedValue({
      data: {
        items: [
          {
            bidId: 1,
            nickname: "피카츄마스터",
            profileImageUrl: "https://images.test/profile.webp",
            bidPrice: 1000,
            createdAt: "2026-08-13T00:00:00",
            isMine: false,
          },
        ],
        hasNext: false,
      },
    });

    await expect(getAuctionBids("1")).resolves.toMatchObject({
      items: [
        {
          profileImageUrl: "https://images.test/profile.webp",
          nickname: "피카츄마스터",
        },
      ],
    });
  });

  it("입찰 내역의 경매 제목을 화면 모델로 변환한다", async () => {
    const { getMyBids } = await import("@/api/bids");
    get.mockResolvedValue({
      data: {
        items: [
          {
            auctionId: 1,
            title: "피카츄 특별 경매",
            card: { cardName: "Pikachu" },
            myBidPrice: 1000,
            currentPrice: 1200,
            status: "HIGHEST",
            auctionStatus: "ONGOING",
          },
        ],
        hasNext: false,
      },
    });

    await expect(getMyBids()).resolves.toMatchObject({
      items: [{ title: "피카츄 특별 경매", cardName: "Pikachu" }],
    });
  });

  it("returns server bid error and fallback messages", async () => {
    const { getBidErrorMessage, placeBid } = await import("@/api/bids");
    const error = new AxiosError("bad");
    error.response = { data: { message: "잔액 부족" } } as never;
    expect(getBidErrorMessage(error)).toBe("잔액 부족");
    expect(getBidErrorMessage(new Error("bad"))).toContain("입찰에 실패");
    post.mockResolvedValue({ data: { bidId: 1, bidPrice: 1000 } });
    await expect(placeBid("1", 1000)).resolves.toEqual({
      bidId: 1,
      bidPrice: 1000,
    });
    expect(post).toHaveBeenCalledWith("/auctions/1/bids", { bidPrice: 1000 });
  });

  it("입찰 요청을 접수하고 접수 결과를 그대로 반환한다", async () => {
    const { createBidRequest } = await import("@/api/bids");
    post.mockResolvedValue({
      data: { bidRequestId: 1, bidPrice: 1000, status: "PENDING" },
    });

    await expect(createBidRequest("1", 1000)).resolves.toEqual({
      bidRequestId: 1,
      bidPrice: 1000,
      status: "PENDING",
    });
    expect(post).toHaveBeenCalledWith("/auctions/1/bid-requests", {
      bidPrice: 1000,
    });
  });

  it("입찰 요청 처리 결과를 조회한다", async () => {
    const { getBidRequestResult } = await import("@/api/bids");
    get.mockResolvedValue({
      data: { bidRequestId: 7, bidPrice: 10500, status: "SUCCEEDED" },
    });

    await expect(getBidRequestResult("1", 7)).resolves.toEqual({
      bidRequestId: 7,
      bidPrice: 10500,
      status: "SUCCEEDED",
    });
    expect(get).toHaveBeenCalledWith("/auctions/1/bid-requests/7");
  });
});
