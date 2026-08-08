import axios from "axios";
import { describe, expect, it, vi } from "vitest";

const { get, del } = vi.hoisted(() => ({ get: vi.fn(), del: vi.fn() }));
vi.mock("@/api/mutator/custom-instance", () => ({
  axiosInstance: { get, delete: del },
}));

const card = {
  cardId: 1,
  cardName: "Blastoise",
  setName: "Base",
  cardNumber: "2",
  language: "EN",
  rarity: "Rare",
  imageUrl: "card.jpg",
};
const cert = {
  certificateId: 1,
  serialNumber: "ABC",
  certificationBody: "PSA",
  grade: "10",
  gradeCode: "G10",
  inspectedAt: "2026-01-01",
};

describe("consignments api", () => {
  it("위탁 목록을 화면 모델로 변환한다", async () => {
    get.mockResolvedValue({
      data: {
        hasNext: true,
        cursor: 4,
        size: 50,
        items: [
          {
            consignmentId: 1,
            auctionId: 2,
            card,
            sellerMemberId: 3,
            status: "AUCTION_ONGOING",
            certificate: cert,
            thumbnailUrl: null,
          },
          {
            consignmentId: 5,
            card: { ...card, cardName: "Eevee", imageUrl: null },
            sellerMemberId: 3,
            status: "PASSED",
            certificate: cert,
          },
        ],
      },
    });
    const { getMyConsignments } = await import("@/api/consignments");
    await expect(
      getMyConsignments({ status: "AUCTION_ONGOING" }),
    ).resolves.toEqual({
      hasNext: true,
      cursor: 4,
      items: [
        {
          id: "1",
          auctionId: "2",
          cardName: "Blastoise",
          thumbnailUrl: "card.jpg",
          grade: { agency: "PSA", score: "10", serial: "ABC" },
          status: "AUCTION_LIVE",
        },
        {
          id: "5",
          cardName: "Eevee",
          grade: { agency: "PSA", score: "10", serial: "ABC" },
          status: "REAPPLICABLE",
        },
      ],
    });
  });

  it("상세 이미지를 정렬하고 404는 undefined로 반환한다", async () => {
    get.mockResolvedValueOnce({
      data: {
        consignmentId: 8,
        card,
        sellerMemberNickname: "seller",
        status: "REGISTERABLE",
        certificate: cert,
        auctionRegistered: false,
        majorDefect: null,
        images: [
          { consignmentImageId: 2, imageOrder: 2, imageUrl: "back" },
          { consignmentImageId: 1, imageOrder: 1, imageUrl: "front" },
        ],
      },
    });
    const { getMyConsignmentDetail, deleteMyConsignment } =
      await import("@/api/consignments");
    await expect(getMyConsignmentDetail("8")).resolves.toMatchObject({
      id: "8",
      thumbnailUrl: "front",
      images: [{ consignmentImageId: 1 }, { consignmentImageId: 2 }],
      auctionRegistered: false,
    });
    const notFound = new axios.AxiosError("missing");
    notFound.response = { status: 404 } as never;
    get.mockRejectedValueOnce(notFound);
    await expect(getMyConsignmentDetail("404")).resolves.toBeUndefined();
    await deleteMyConsignment("8");
    expect(del).toHaveBeenCalledWith("/consignments/8");
  });
});
