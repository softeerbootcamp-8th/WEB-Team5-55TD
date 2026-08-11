import assert from 'node:assert/strict';
import test from 'node:test';
import { SequenceBitSet } from '../src/lib/bitset.mjs';
import { MillisecondHistogram } from '../src/lib/histogram.mjs';
import {
  createBidUpdatedEnvelope,
  notificationChannel,
  parsePublishedAt,
} from '../src/lib/notification.mjs';

test('sequence bitset은 고유 수신, 중복과 범위 밖 값을 구분한다', () => {
  const bitset = new SequenceBitSet(100, 3);
  assert.equal(bitset.add(100), 'added');
  assert.equal(bitset.add(100), 'duplicate');
  assert.equal(bitset.add(102), 'added');
  assert.equal(bitset.add(103), 'out_of_range');
  assert.equal(bitset.received, 2);
});

test('histogram은 percentile을 계산하고 bucket으로 병합한다', () => {
  const first = new MillisecondHistogram();
  [10, 20, 30, 40, 50].forEach((value) => first.record(value));
  const second = new MillisecondHistogram();
  second.merge(first.summary({ includeBuckets: true }));
  assert.deepEqual(second.summary(), {
    count: 5,
    averageMillis: 30,
    maxMillis: 50,
    p50Millis: 30,
    p95Millis: 50,
    p99Millis: 50,
  });
});

test('Redis notification envelope는 애플리케이션 계약과 같은 구조를 사용한다', () => {
  const publishedAt = new Date('2026-08-11T01:02:03.456Z');
  const envelope = createBidUpdatedEnvelope({ auctionId: 42, bidId: 100, publishedAt });
  const parsed = JSON.parse(envelope.message);

  assert.equal(envelope.channel, notificationChannel(42));
  assert.equal(parsed.eventType, 'AUCTION_BID_UPDATED');
  assert.equal(parsed.payload.eventId, envelope.eventId);
  assert.equal(parsed.payload.winningBid.bidId, 100);
  assert.equal(parsed.payload.winningBid.bidStatus, 'HIGHEST');
  assert.equal(parsePublishedAt(parsed.payload.occurredAt), publishedAt.getTime());
  assert.match(envelope.eventId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
