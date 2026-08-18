import { describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({ axiosInstance: { get } }));

describe("sales api", () => {
  it("판매 내역 응답을 화면 모델로 변환한다", async () => {
    get.mockResolvedValue({
      data: {
        hasNext: true,
        cursor: "next",
        size: 20,
        items: [
          {
            auctionId: 7,
            card: { cardName: "Pikachu", imageUrl: null },
            grade: "PSA 10",
            winningPrice: 25000,
            resultType: "WON",
          },
          {
            auctionId: 8,
            card: { cardName: "Eevee", imageUrl: "img" },
            grade: null,
            winningPrice: null,
            resultType: "PASSED",
          },
        ],
      },
    });
    const { getMySalesHistory } = await import("@/api/sales");
    await expect(
      getMySalesHistory({ status: "WON", size: 10 }),
    ).resolves.toEqual({
      hasNext: true,
      cursor: "next",
      items: [
        {
          auctionId: "7",
          cardName: "Pikachu",
          grade: { agency: "PSA", score: "10" },
          finalPrice: 25000,
          resultType: "WON",
        },
        {
          auctionId: "8",
          cardName: "Eevee",
          thumbnailUrl: "img",
          resultType: "PASSED",
        },
      ],
    });
    expect(get).toHaveBeenCalledWith("/sellers/me/sales", {
      params: { status: "WON", cursor: undefined, size: 10 },
    });
  });
});
