import assert from 'node:assert/strict';
import test from 'node:test';
import { mergeWebSocketResults } from '../src/merge-results.mjs';

test('shard 결과의 전달 수와 latency histogram을 합친다', () => {
  const result = mergeWebSocketResults([
    shardResult(0, 100, 99, [[10, 99]]),
    shardResult(1, 100, 100, [[20, 100]]),
  ]);

  assert.equal(result.summary.attemptedConnections, 200);
  assert.equal(result.summary.uniqueMessages, 199);
  assert.equal(result.summary.expectedDeliveries, 200);
  assert.equal(result.summary.deliveryRatio, 199 / 200);
  assert.equal(result.summary.latency.p50Millis, 20);
  assert.deepEqual(result.shards, [0, 1]);
});

function shardResult(shardIndex, expected, unique, buckets) {
  return {
    runId: 'run-1',
    mode: 'fanout',
    shardIndex,
    summary: {
      attemptedConnections: 100,
      stompConnected: 100,
      expectedDeliveries: expected,
      uniqueMessages: unique,
      missingMessages: expected - unique,
    },
    latencyHistogram: { buckets },
  };
}
