import type { Bid } from "@/lib/types";

export interface AuctionBidsSnapshot {
  items: Bid[];
  hasNext: boolean;
  cursor?: string;
}

export interface IncomingLatestBid {
  bidId: number;
  nickname: string;
  profileImageUrl?: string | null;
  bidPrice: number;
  createdAt: string;
}

export function mergeLatestBid(
  snapshot: AuctionBidsSnapshot | undefined,
  latestBid: IncomingLatestBid,
  isMine: boolean,
  limit: number,
): AuctionBidsSnapshot | undefined {
  if (!snapshot) return undefined;

  const previousBid = snapshot.items.find(
    (item) =>
      item.nickname === latestBid.nickname ||
      (isMine && item.isMine),
  );

  const bid: Bid = {
    id: String(latestBid.bidId),
    nickname: latestBid.nickname,
    profileImageUrl: latestBid.profileImageUrl ?? previousBid?.profileImageUrl,
    amount: latestBid.bidPrice,
    createdAt: latestBid.createdAt,
    isMine: Boolean(isMine || previousBid?.isMine),
  };

  return {
    ...snapshot,
    items: [bid, ...snapshot.items.filter((item) => item.id !== bid.id)].slice(
      0,
      limit,
    ),
  };
}
