import { performance } from 'node:perf_hooks';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { publicBidConfig, validateBidConfig } from './lib/bid-config.mjs';
import {
  hasFlag,
  parseConfigPath,
  readJsonConfig,
  requireLoadTestConfirmation,
} from './lib/config.mjs';
import { MillisecondHistogram } from './lib/histogram.mjs';
import { sleep, writeJson } from './lib/io.mjs';

const argv = process.argv.slice(2);

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

async function main() {
  const config = validateBidConfig(await readJsonConfig(parseConfigPath(argv)));
  if (hasFlag(argv, '--dry-run')) {
    console.log(JSON.stringify(publicBidConfig(config), null, 2));
    return;
  }
  requireLoadTestConfirmation(argv);

  const result = await runBidLoad(config);
  await writeJson(config.resultPath, result);
  console.log(JSON.stringify(result.summary, null, 2));
}

export async function runBidLoad(config) {
  const latency = new MillisecondHistogram();
  const counters = {
    scheduled: config.requestsPerSecond * config.durationSeconds,
    sent: 0,
    skippedByClientLimit: 0,
    succeeded: 0,
    timedOut: 0,
    networkFailures: 0,
    statusCounts: {},
    errorCodeCounts: {},
    auctionSuccessCounts: Object.fromEntries(
      config.auctions.map((auction) => [auction.auctionId, 0]),
    ),
  };
  const nextPrices = new Map(
    config.auctions.map((auction) => [auction.auctionId, auction.initialBidPrice]),
  );
  const requests = new Set();
  const intervalMillis = 1000 / config.requestsPerSecond;
  const startedAt = performance.now();

  for (let index = 0; index < counters.scheduled; index += 1) {
    const plannedAt = startedAt + index * intervalMillis;
    await sleep(Math.max(0, plannedAt - performance.now()));
    if (requests.size >= config.maxInFlight) {
      counters.skippedByClientLimit += 1;
      continue;
    }
    const auction = config.auctions[index % config.auctions.length];
    const token = config.accessTokens[index % config.accessTokens.length];
    const bidPrice = nextPrices.get(auction.auctionId);
    nextPrices.set(auction.auctionId, bidPrice + auction.bidIncrement);

    const request = placeBid({ config, auction, token, bidPrice, latency, counters }).finally(() =>
      requests.delete(request),
    );
    requests.add(request);
    counters.sent += 1;
  }
  await Promise.allSettled(requests);

  return {
    schemaVersion: 1,
    runId: config.runId,
    mode: 'bid-baseline',
    startedAt: new Date(Date.now() - (performance.now() - startedAt)).toISOString(),
    completedAt: new Date().toISOString(),
    config: publicBidConfig(config),
    summary: {
      ...counters,
      successRate: counters.sent === 0 ? 0 : counters.succeeded / counters.sent,
      latency: latency.summary(),
    },
    latencyHistogram: latency.summary({ includeBuckets: true }),
  };
}

async function placeBid({ config, auction, token, bidPrice, latency, counters }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMillis);
  const startedAt = performance.now();
  try {
    const response = await fetch(
      new URL(`/auctions/${auction.auctionId}/bids`, config.baseUrl),
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Cookie: `access-token=${token}`,
          Origin: config.origin,
        },
        body: JSON.stringify({ bidPrice }),
        signal: controller.signal,
      },
    );
    increment(counters.statusCounts, String(response.status));
    if (response.status === 201) {
      await response.arrayBuffer();
      counters.succeeded += 1;
      counters.auctionSuccessCounts[auction.auctionId] += 1;
      return;
    }
    const errorCode = await readErrorCode(response);
    increment(counters.errorCodeCounts, errorCode);
  } catch (error) {
    if (error.name === 'AbortError') counters.timedOut += 1;
    else counters.networkFailures += 1;
  } finally {
    latency.record(performance.now() - startedAt);
    clearTimeout(timeout);
  }
}

async function readErrorCode(response) {
  try {
    const body = await response.json();
    return String(body.code ?? body.errorCode ?? `HTTP_${response.status}`);
  } catch {
    return `HTTP_${response.status}`;
  }
}

function increment(counts, key) {
  counts[key] = (counts[key] ?? 0) + 1;
}
