import { describe, expect, it } from "vitest";
import { bidderKey, dedupeBidsByBidder } from "@/lib/bids";
import type { Bid } from "@/lib/types";

function bid(overrides: Partial<Bid> & { id: string }): Bid {
  return {
    maskedNickname: "ab***12",
    amount: 1000,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("bidderKey", () => {
  it("본인 여부와 관계없이 같은 마스킹 닉네임을 키로 쓴다", () => {
    expect(
      bidderKey(bid({ id: "1", isMine: true, maskedNickname: "aa***11" })),
    ).toBe(
      bidderKey(bid({ id: "2", isMine: false, maskedNickname: "aa***11" })),
    );
  });

  it("타인 입찰은 마스킹 닉네임을 키로 쓴다", () => {
    expect(bidderKey(bid({ id: "1", maskedNickname: "ab***12" }))).toBe(
      "ab***12",
    );
  });
});

describe("dedupeBidsByBidder", () => {
  it("같은 입찰자의 더 오래된 입찰을 제거하고 최신 입찰만 남긴다", () => {
    const bids: Bid[] = [
      bid({ id: "3", maskedNickname: "bb***22", amount: 3000 }),
      bid({ id: "2", maskedNickname: "aa***11", amount: 2000 }),
      bid({ id: "1", maskedNickname: "bb***22", amount: 1000 }),
    ];

    const result = dedupeBidsByBidder(bids);

    expect(result.map((b) => b.id)).toEqual(["3", "2"]);
  });

  it("중간에 있던 입찰자가 다시 입찰하면 그 입찰이 맨 위에서 남는다", () => {
    // 입력은 항상 최신순이라, bb***22의 재입찰(id=4)이 맨 앞에 온다.
    const bids: Bid[] = [
      bid({ id: "4", maskedNickname: "bb***22", amount: 4000 }),
      bid({ id: "3", maskedNickname: "cc***33", amount: 3000 }),
      bid({ id: "2", maskedNickname: "aa***11", amount: 2000 }),
      bid({ id: "1", maskedNickname: "bb***22", amount: 1000 }),
    ];

    const result = dedupeBidsByBidder(bids);

    expect(result.map((b) => b.id)).toEqual(["4", "3", "2"]);
    expect(result[0].amount).toBe(4000);
  });

  it("본인 입찰이 여러 번이어도 하나만 남는다", () => {
    const bids: Bid[] = [
      bid({ id: "2", isMine: true, amount: 2000 }),
      bid({ id: "1", maskedNickname: "bb***22", amount: 1000 }),
      bid({ id: "0", isMine: true, amount: 500 }),
    ];

    const result = dedupeBidsByBidder(bids);

    expect(result.map((b) => b.id)).toEqual(["2", "1"]);
  });

  it("빈 목록은 빈 목록을 돌려준다", () => {
    expect(dedupeBidsByBidder([])).toEqual([]);
  });
});
