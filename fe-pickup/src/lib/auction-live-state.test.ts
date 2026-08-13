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

  it("cache가 없으면 새 query를 만들지 않는다", () => {
    expect(mergeLatestBid(undefined, latestBid, false, 6)).toBeUndefined();
  });
});
