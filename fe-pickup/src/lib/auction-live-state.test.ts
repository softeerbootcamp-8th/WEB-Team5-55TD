import { describe, expect, it } from "vitest";
import {
  mergeLatestBid,
  type AuctionBidsSnapshot,
} from "@/lib/auction-live-state";

const snapshot: AuctionBidsSnapshot = {
  items: [
    {
      id: "5",
      nickname: "기존",
      amount: 10_000,
      createdAt: "2026-08-11T00:00:00",
    },
  ],
  hasNext: true,
  cursor: "cursor-1",
};

const latestBid = {
  bidId: 6,
  nickname: "새 입찰자",
  bidPrice: 10_500,
  createdAt: "2026-08-11T00:00:01",
};

describe("mergeLatestBid", () => {
  it("최신 입찰을 앞에 추가하고 기존 페이지 정보를 보존한다", () => {
    expect(mergeLatestBid(snapshot, latestBid, false, 6)).toEqual({
      items: [
        {
          id: "6",
          nickname: "새 입찰자",
          profileImageUrl: undefined,
          amount: 10_500,
          createdAt: "2026-08-11T00:00:01",
          isMine: false,
        },
        snapshot.items[0],
      ],
      hasNext: true,
      cursor: "cursor-1",
    });
  });

  it("같은 bidId를 교체하고 목록 크기를 제한한다", () => {
    const result = mergeLatestBid(
      {
        ...snapshot,
        items: Array.from({ length: 7 }, (_, index) => ({
          id: String(index + 1),
          nickname: "입찰자",
          amount: index,
          createdAt: "2026-08-11T00:00:00",
        })),
      },
      { ...latestBid, bidId: 3 },
      true,
      6,
    );

    expect(result?.items).toHaveLength(6);
    expect(result?.items[0]).toMatchObject({ id: "3", isMine: true });
    expect(result?.items.filter((item) => item.id === "3")).toHaveLength(1);
  });

  it("같은 입찰자의 웹소켓 갱신에는 기존 프로필 이미지를 유지한다", () => {
    const result = mergeLatestBid(
      {
        ...snapshot,
        items: [
          {
            ...snapshot.items[0],
            nickname: "피카츄마스터",
            profileImageUrl: "/profile.webp",
          },
        ],
      },
      { ...latestBid, nickname: "피카츄마스터" },
      false,
      6,
    );

    expect(result?.items[0].profileImageUrl).toBe("/profile.webp");
  });

  it("이전 6개 입찰에 없는 사용자도 웹소켓 프로필 이미지를 표시한다", () => {
    const result = mergeLatestBid(
      snapshot,
      { ...latestBid, profileImageUrl: "/new-bidder.webp" },
      false,
      6,
    );

    expect(result?.items[0].profileImageUrl).toBe("/new-bidder.webp");
  });

  it("본인 여부가 누락된 웹소켓 갱신에도 기존 본인 표시를 유지한다", () => {
    const result = mergeLatestBid(
      {
        ...snapshot,
        items: [
          {
            ...snapshot.items[0],
            nickname: "피카츄마스터",
            isMine: true,
          },
        ],
      },
      { ...latestBid, nickname: "피카츄마스터" },
      false,
      6,
    );

    expect(result?.items[0].isMine).toBe(true);
  });

  it("cache가 없으면 새 query를 만들지 않는다", () => {
    expect(mergeLatestBid(undefined, latestBid, false, 6)).toBeUndefined();
  });
});
