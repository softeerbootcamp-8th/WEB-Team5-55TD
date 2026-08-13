import { describe, expect, it } from "vitest";
import { applyWatchToAuctionCache } from "@/lib/watch-cache";

const WATCHED = { watched: true, watchCount: 3 };

function summary(id: string, watched: boolean, watchCount: number) {
  return { id, cardName: "Mewtwo", status: "LIVE", watched, watchCount };
}

describe("applyWatchToAuctionCache", () => {
  it("무한 스크롤 목록에서 해당 경매만 갈아끼우고 순서는 그대로 둔다", () => {
    const data = {
      pages: [
        { items: [summary("1", false, 9), summary("7", false, 2)] },
        { items: [summary("9", false, 1)] },
      ],
    };

    const next = applyWatchToAuctionCache(data, "7", WATCHED) as typeof data;

    expect(next.pages[0].items.map((item) => item.id)).toEqual(["1", "7"]);
    expect(next.pages[0].items[1]).toMatchObject({
      watched: true,
      watchCount: 3,
    });
    expect(next.pages[0].items[0]).toBe(data.pages[0].items[0]);
  });

  it("단일 페이지 목록도 갈아끼운다", () => {
    const data = { items: [summary("7", false, 2)], hasNext: false };

    const next = applyWatchToAuctionCache(data, "7", WATCHED) as typeof data;

    expect(next.items[0]).toMatchObject({ watched: true, watchCount: 3 });
    expect(next.hasNext).toBe(false);
  });

  it("대표 경매처럼 단건이어도 갈아끼운다", () => {
    const data = summary("7", false, 2);

    const next = applyWatchToAuctionCache(data, "7", WATCHED);

    expect(next).toMatchObject({ watched: true, watchCount: 3 });
  });

  it("해당 경매가 없으면 원본을 그대로 돌려준다", () => {
    const data = { pages: [{ items: [summary("1", false, 9)] }] };

    expect(applyWatchToAuctionCache(data, "7", WATCHED)).toBe(data);
  });

  it("관심 수를 모르면 기존 값을 유지한다", () => {
    const data = { items: [summary("7", false, 2)] };

    const next = applyWatchToAuctionCache(data, "7", {
      watched: true,
    }) as typeof data;

    expect(next.items[0]).toMatchObject({ watched: true, watchCount: 2 });
  });

  it("캐시가 비어 있으면 건드리지 않는다", () => {
    expect(applyWatchToAuctionCache(undefined, "7", WATCHED)).toBeUndefined();
    expect(applyWatchToAuctionCache(null, "7", WATCHED)).toBeNull();
  });
});
