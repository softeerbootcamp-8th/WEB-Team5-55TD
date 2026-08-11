import { randomUUID } from 'node:crypto';

export const AUCTION_BID_UPDATED = 'AUCTION_BID_UPDATED';

export function notificationChannel(auctionId) {
  return `pickup:notification:AUCTION:${auctionId}`;
}

export function createBidUpdatedEnvelope({ auctionId, bidId, publishedAt = new Date() }) {
  const occurredAt = toLocalDateTime(publishedAt);
  const eventId = randomUUID();
  return {
    eventId,
    occurredAt,
    channel: notificationChannel(auctionId),
    message: JSON.stringify({
      eventType: AUCTION_BID_UPDATED,
      payload: {
        eventId,
        auctionId,
        consignmentId: auctionId,
        startingPrice: 10_000,
        reservePrice: 10_000,
        winningPrice: bidId,
        auctionStatus: 'ONGOING',
        startedAt: occurredAt,
        endedAt: toLocalDateTime(new Date(publishedAt.getTime() + 3_600_000)),
        createdAt: occurredAt,
        winningBid: {
          bidId,
          memberId: 1,
          memberNickname: 'loadtest',
          bidPrice: bidId,
          bidStatus: 'HIGHEST',
          createdAt: occurredAt,
        },
        occurredAt,
      },
    }),
  };
}

export function parsePublishedAt(occurredAt) {
  return Date.parse(`${occurredAt}Z`);
}

function toLocalDateTime(date) {
  return date.toISOString().slice(0, -1);
}
