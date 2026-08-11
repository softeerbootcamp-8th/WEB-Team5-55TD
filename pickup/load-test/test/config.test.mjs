import assert from 'node:assert/strict';
import test from 'node:test';
import { publicBidConfig, validateBidConfig } from '../src/lib/bid-config.mjs';
import { validateWebSocketConfig } from '../src/lib/websocket-config.mjs';

test('여러 fanout shard에는 공통 시작 시각이 필요하다', () => {
  assert.throws(
    () =>
      validateWebSocketConfig(
        {
          runId: 'run-1',
          wsUrl: 'wss://test.example.com/ws',
          origin: 'https://test.example.com',
          redisUrl: 'redis://localhost:6379',
          auctionIds: [42],
          connections: 100,
          shardCount: 2,
          shardIndex: 0,
          durationSeconds: 10,
          eventsPerSecond: 1,
          bidIdBase: 1000,
          resultPath: './result.json',
        },
        'fanout',
      ),
    /startAtEpochMillis/,
  );
});

test('입찰 공개 설정과 JSON 결과에는 access token이 남지 않는다', () => {
  const config = validateBidConfig({
    mode: 'single-auction',
    runId: 'bid-1',
    baseUrl: 'https://api.example.com',
    origin: 'https://example.com',
    auctions: [{ auctionId: 42, initialBidPrice: 10_000, bidIncrement: 1000 }],
    accessTokens: ['secret-token'],
    requestsPerSecond: 1,
    durationSeconds: 10,
    resultPath: './result.json',
  });
  const serialized = JSON.stringify(publicBidConfig(config));
  assert.equal(serialized.includes('secret-token'), false);
  assert.equal(JSON.parse(serialized).accountCount, 1);
});
