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
            status: "IN_AUCTION",
            auctionStatus: "ONGOING",
            certificate: cert,
            thumbnailUrl: null,
          },
          {
            consignmentId: 5,
            card: { ...card, cardName: "Eevee", imageUrl: null },
            sellerMemberId: 3,
            status: "REGISTERABLE",
            auctionStatus: "PASSED",
            certificate: cert,
          },
          {
            consignmentId: 6,
            auctionId: 7,
            card: { ...card, cardName: "Pikachu", imageUrl: null },
            sellerMemberId: 3,
            status: "IN_AUCTION",
            auctionStatus: "SCHEDULED",
            certificate: cert,
          },
          {
            consignmentId: 8,
            auctionId: 9,
            card: { ...card, cardName: "Charizard", imageUrl: null },
            sellerMemberId: 3,
            status: "SOLD",
            auctionStatus: "WON",
            certificate: cert,
          },
          {
            consignmentId: 10,
            card: { ...card, cardName: "Squirtle", imageUrl: null },
            sellerMemberId: 3,
            status: "REGISTERABLE",
            certificate: cert,
          },
        ],
      },
    });
    const { getMyConsignments } = await import("@/api/consignments");
    await expect(
      getMyConsignments({
        status: "IN_AUCTION",
        auctionStatus: "SCHEDULED",
      }),
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
        {
          id: "6",
          auctionId: "7",
          cardName: "Pikachu",
          grade: { agency: "PSA", score: "10", serial: "ABC" },
          status: "AUCTION_UPCOMING",
        },
        {
          id: "8",
          auctionId: "9",
          cardName: "Charizard",
          grade: { agency: "PSA", score: "10", serial: "ABC" },
          status: "SOLD",
        },
        {
          id: "10",
          cardName: "Squirtle",
          grade: { agency: "PSA", score: "10", serial: "ABC" },
          status: "REGISTERABLE",
        },
      ],
    });
    expect(get).toHaveBeenLastCalledWith("/consignments", {
      params: {
        status: "IN_AUCTION",
        auctionStatus: "SCHEDULED",
        cursor: undefined,
        size: 50,
      },
    });
  });

  it("경매중_상품의_경매상태가_없으면_예정으로_분류하지_않는다", async () => {
    get.mockResolvedValueOnce({
      data: {
        hasNext: false,
        cursor: null,
        size: 50,
        items: [
          {
            consignmentId: 1,
            auctionId: 2,
            card,
            sellerMemberId: 3,
            status: "IN_AUCTION",
            auctionStatus: null,
            certificate: cert,
          },
        ],
      },
    });
    const { getMyConsignments } = await import("@/api/consignments");

    await expect(
      getMyConsignments({
        status: "IN_AUCTION",
        auctionStatus: "SCHEDULED",
      }),
    ).rejects.toThrow("IN_AUCTION 상품의 경매 상태가 올바르지 않습니다: null");
  });

  it("상세 이미지를 정렬하고 404·403은 null로 반환한다", async () => {
    get.mockResolvedValueOnce({
      data: {
        consignmentId: 8,
        card,
        sellerMemberNickname: "seller",
        status: "REGISTERABLE",
        certificate: cert,
        auctionRegistered: false,
        cardState: "MEDIUM",
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
      cardState: "MEDIUM",
      images: [{ consignmentImageId: 1 }, { consignmentImageId: 2 }],
      auctionRegistered: false,
    });
    const notFound = new axios.AxiosError("missing");
    notFound.response = { status: 404 } as never;
    get.mockRejectedValueOnce(notFound);
    await expect(getMyConsignmentDetail("404")).resolves.toBeNull();
    const forbidden = new axios.AxiosError("forbidden");
    forbidden.response = { status: 403 } as never;
    get.mockRejectedValueOnce(forbidden);
    await expect(getMyConsignmentDetail("403")).resolves.toBeNull();
    await deleteMyConsignment("8");
    expect(del).toHaveBeenCalledWith("/consignments/8");
  });
});
