import type { Bid } from "@/lib/types";

/**
 * 입찰자를 구분하는 키. 실명 대신 마스킹된 닉네임을 쓴다 — 익명화 정책(DESIGN.md §6)을
 * 유지하면서도, 같은 회원의 반복 입찰을 실시간 목록에서 하나로 묶어 보여주는 데 필요한
 * 최소한의 식별자다. 본인은 항상 같은 키("나")로 묶인다.
 */
export function bidderKey(bid: Bid): string {
  return bid.isMine ? "__me__" : bid.maskedNickname;
}

/**
 * 입찰 내역을 입찰자별로 중복 제거해, 각 입찰자의 가장 최근 입찰만 남긴다.
 *
 * `bids`가 최신순(내림차순)으로 정렬돼 있다고 가정한다 — 그래야 각 입찰자의 "첫 등장"이
 * 곧 "가장 최근 입찰"이 된다. 결과도 최신순을 유지하므로, 중간에 있던 입찰자가 다시
 * 입찰하면 그 입찰자의 자리가 자연히 맨 위로 올라온다.
 */
export function dedupeBidsByBidder(bids: Bid[]): Bid[] {
  const seen = new Set<string>();
  const result: Bid[] = [];
  for (const bid of bids) {
    const key = bidderKey(bid);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(bid);
  }
  return result;
}
