import type { AuctionSummary } from "@/lib/types";

export interface WatchState {
  watched: boolean;
  watchCount?: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function patchSummary(
  summary: AuctionSummary,
  auctionId: string,
  state: WatchState,
): AuctionSummary {
  if (summary.id !== auctionId) return summary;
  return {
    ...summary,
    watched: state.watched,
    watchCount: state.watchCount ?? summary.watchCount,
  };
}

/** 바뀐 항목이 없으면 원본 배열을 그대로 돌려준다(무관한 목록의 리렌더 방지). */
function patchItems(
  items: AuctionSummary[],
  auctionId: string,
  state: WatchState,
): AuctionSummary[] {
  const patched = items.map((item) => patchSummary(item, auctionId, state));
  return patched.some((item, index) => item !== items[index]) ? patched : items;
}

/**
 * 관심 토글 결과를 `["auctions"]` 캐시에 직접 반영한다.
 *
 * 목록을 다시 불러오면 인기순(관심 수) 정렬이 그 자리에서 바뀌어 방금 누른 카드가
 * 다른 위치로 튄다. 그래서 순서는 건드리지 않고 해당 경매의 관심 상태만 갈아끼운다.
 * 무한 스크롤 목록(`pages`), 단일 페이지(`items`), 대표 경매(단건)를 모두 받는다.
 */
export function applyWatchToAuctionCache(
  data: unknown,
  auctionId: string,
  state: WatchState,
): unknown {
  if (!isRecord(data)) return data;

  if (Array.isArray(data.pages)) {
    const originalPages: unknown[] = data.pages;
    const pages = originalPages.map((page) => {
      if (!isRecord(page) || !Array.isArray(page.items)) return page;
      const items = patchItems(
        page.items as AuctionSummary[],
        auctionId,
        state,
      );
      return items === page.items ? page : { ...page, items };
    });
    return pages.some((page, index) => page !== originalPages[index])
      ? { ...data, pages }
      : data;
  }

  if (Array.isArray(data.items)) {
    const items = patchItems(data.items as AuctionSummary[], auctionId, state);
    return items === data.items ? data : { ...data, items };
  }

  if (typeof data.id === "string") {
    return patchSummary(data as unknown as AuctionSummary, auctionId, state);
  }

  return data;
}
