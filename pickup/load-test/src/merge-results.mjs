import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { readJsonConfig, requireString } from './lib/config.mjs';
import { MillisecondHistogram } from './lib/histogram.mjs';
import { writeJson } from './lib/io.mjs';

const argv = process.argv.slice(2);

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

async function main() {
  const outputIndex = argv.indexOf('--output');
  if (outputIndex === -1 || !argv[outputIndex + 1]) {
    throw new Error('--output <path>가 필요합니다.');
  }
  const outputPath = argv[outputIndex + 1];
  const inputPaths = argv.filter((value, index) => {
    if (value === '--output') return false;
    if (index === outputIndex + 1) return false;
    return !value.startsWith('--');
  });
  if (inputPaths.length < 2) {
    throw new Error('병합할 shard 결과 파일을 두 개 이상 지정하세요.');
  }
  const results = await Promise.all(inputPaths.map(readJsonConfig));
  const merged = mergeWebSocketResults(results);
  await writeJson(outputPath, merged);
  console.log(JSON.stringify(merged.summary, null, 2));
}

export function mergeWebSocketResults(results) {
  const first = results[0];
  requireString(first.runId, 'runId');
  for (const result of results) {
    if (result.runId !== first.runId || result.mode !== first.mode) {
      throw new Error('runId와 mode가 같은 결과만 병합할 수 있습니다.');
    }
  }

  const latency = new MillisecondHistogram();
  const numericCounters = [
    'attemptedConnections',
    'stompConnected',
    'connectFailures',
    'expectedClose',
    'unexpectedClose',
    'messages',
    'uniqueMessages',
    'duplicateMessages',
    'outOfRangeMessages',
    'invalidMessages',
    'mismatchedEventIds',
    'publishedEvents',
    'publishFailures',
    'publishNoSubscribers',
    'expectedDeliveries',
    'missingMessages',
  ];
  const summary = Object.fromEntries(numericCounters.map((name) => [name, 0]));
  for (const result of results) {
    latency.merge(result.latencyHistogram);
    for (const name of numericCounters) {
      summary[name] += result.summary[name] ?? 0;
    }
  }
  summary.connectedRatio =
    summary.attemptedConnections === 0
      ? 0
      : summary.stompConnected / summary.attemptedConnections;
  summary.deliveryRatio =
    summary.expectedDeliveries === 0
      ? null
      : summary.uniqueMessages / summary.expectedDeliveries;
  summary.latency = latency.summary();

  return {
    schemaVersion: 1,
    runId: first.runId,
    mode: first.mode,
    mergedAt: new Date().toISOString(),
    shards: results.map((result) => result.shardIndex).sort((a, b) => a - b),
    summary,
    latencyHistogram: latency.summary({ includeBuckets: true }),
  };
}
