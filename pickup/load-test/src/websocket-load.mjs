import { monitorEventLoopDelay, performance } from 'node:perf_hooks';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import { createClient } from 'redis';
import { SequenceBitSet } from './lib/bitset.mjs';
import {
  hasFlag,
  parseConfigPath,
  readJsonConfig,
  requireLoadTestConfirmation,
} from './lib/config.mjs';
import { MillisecondHistogram } from './lib/histogram.mjs';
import { sleep, writeJson } from './lib/io.mjs';
import { createBidUpdatedEnvelope, parsePublishedAt } from './lib/notification.mjs';
import { StompAuctionClient } from './lib/stomp-client.mjs';
import { localConnectionIndexes, validateWebSocketConfig } from './lib/websocket-config.mjs';

const argv = process.argv.slice(2);
const mode = argv[0];

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}

async function main() {
  const config = validateWebSocketConfig(
    await readJsonConfig(parseConfigPath(argv)),
    mode,
  );
  if (hasFlag(argv, '--dry-run')) {
    console.log(JSON.stringify(publicConfig(config), null, 2));
    return;
  }
  requireLoadTestConfirmation(argv);

  const result = await runWebSocketLoad(config);
  await writeJson(config.resultPath, result);
  console.log(JSON.stringify(result.summary, null, 2));
}

export async function runWebSocketLoad(config) {
  const indexes = localConnectionIndexes(config);
  const clients = [];
  const connectedClients = [];
  const eventCount =
    config.mode === 'fanout' ? config.eventsPerSecond * config.durationSeconds : 0;
  const latency = new MillisecondHistogram();
  const sequenceEventIds = new Map();
  const sessionSequences = new Map();
  const counters = {
    attemptedConnections: indexes.length,
    stompConnected: 0,
    connectFailures: 0,
    expectedClose: 0,
    unexpectedClose: 0,
    messages: 0,
    uniqueMessages: 0,
    duplicateMessages: 0,
    outOfRangeMessages: 0,
    invalidMessages: 0,
    mismatchedEventIds: 0,
    publishedEvents: 0,
    publishFailures: 0,
    publishNoSubscribers: 0,
  };
  const eventLoop = monitorEventLoopDelay({ resolution: 20 });
  eventLoop.enable();
  const cpuStart = process.cpuUsage();
  const wallStart = performance.now();
  let isStopping = false;

  const stop = () => {
    if (isStopping) return;
    isStopping = true;
    for (const client of clients) client.close(true);
  };
  process.once('SIGINT', stop);
  process.once('SIGTERM', stop);

  const rampInterval = indexes.length === 0 ? 0 : (config.rampSeconds * 1000) / indexes.length;
  const connectionTasks = indexes.map(async (sessionIndex, localIndex) => {
    if (rampInterval > 0) await sleep(localIndex * rampInterval);
    if (isStopping) return;
    const auctionId = config.auctionIds[sessionIndex % config.auctionIds.length];
    if (config.mode === 'fanout') {
      sessionSequences.set(sessionIndex, new SequenceBitSet(config.bidIdBase, eventCount));
    }
    const client = new StompAuctionClient({
      config,
      sessionIndex,
      auctionId,
      onMessage: (source, frame) =>
        handleMessage({
          source,
          frame,
          config,
          counters,
          latency,
          sequenceEventIds,
          sessionSequences,
        }),
    });
    clients.push(client);
    try {
      await client.connect();
      counters.stompConnected += 1;
      connectedClients.push(client);
      client.onClose((expected) => {
        if (expected) counters.expectedClose += 1;
        else counters.unexpectedClose += 1;
      });
    } catch {
      counters.connectFailures += 1;
    }
  });
  await Promise.all(connectionTasks);

  const connectedRatio =
    counters.attemptedConnections === 0
      ? 0
      : counters.stompConnected / counters.attemptedConnections;
  if (connectedRatio < config.minimumConnectedRatio) {
    stop();
    throw new Error(
      `연결 성공률 ${connectedRatio.toFixed(4)}가 기준 ${config.minimumConnectedRatio}보다 낮아 시험을 중단합니다.`,
    );
  }

  await sleep(config.stabilizationSeconds * 1000);
  let publishStartedAt = null;
  if (config.mode === 'fanout') {
    const requestedStart = config.startAtEpochMillis ?? Date.now();
    if (requestedStart < Date.now() - 1000) {
      stop();
      throw new Error('startAtEpochMillis가 이미 지난 시각입니다.');
    }
    await sleep(Math.max(0, requestedStart - Date.now()));
    publishStartedAt = Date.now();
    if (config.publishEvents) {
      await publishEvents(config, counters, () => isStopping);
    } else {
      await sleep(config.durationSeconds * 1000);
    }
    await sleep(config.deliveryGraceMillis);
  } else {
    await sleep(config.durationSeconds * 1000);
  }

  clients.forEach((client, index) => {
    const abrupt = index / Math.max(clients.length, 1) < config.abruptCloseRatio;
    client.close(abrupt);
  });
  await sleep(1000);
  eventLoop.disable();
  process.removeListener('SIGINT', stop);
  process.removeListener('SIGTERM', stop);

  const expectedDeliveries =
    config.mode === 'fanout'
      ? calculateExpectedDeliveries(config, connectedClients, eventCount)
      : 0;
  const cpu = process.cpuUsage(cpuStart);
  const wallMillis = performance.now() - wallStart;
  const summary = {
    connectedRatio,
    deliveryRatio:
      expectedDeliveries === 0 ? null : counters.uniqueMessages / expectedDeliveries,
    expectedDeliveries,
    ...counters,
    missingMessages: Math.max(0, expectedDeliveries - counters.uniqueMessages),
    latency: latency.summary(),
    loadGenerator: {
      wallMillis,
      cpuUserMillis: cpu.user / 1000,
      cpuSystemMillis: cpu.system / 1000,
      eventLoopDelayP99Millis: eventLoop.percentile(99) / 1_000_000,
      rssBytes: process.memoryUsage().rss,
      heapUsedBytes: process.memoryUsage().heapUsed,
    },
  };

  return {
    schemaVersion: 1,
    runId: config.runId,
    mode: config.mode,
    shardIndex: config.shardIndex,
    shardCount: config.shardCount,
    startedAt: new Date(Date.now() - wallMillis).toISOString(),
    publishStartedAt: publishStartedAt && new Date(publishStartedAt).toISOString(),
    completedAt: new Date().toISOString(),
    config: publicConfig(config),
    summary,
    latencyHistogram: latency.summary({ includeBuckets: true }),
  };
}

async function publishEvents(config, counters, shouldStop) {
  const redis = createClient({ url: config.redisUrl });
  redis.on('error', (error) => {
    console.error(`Redis client error: ${error.message}`);
  });
  await redis.connect();
  const totalEvents = config.eventsPerSecond * config.durationSeconds;
  const intervalMillis = 1000 / config.eventsPerSecond;
  const startedAt = performance.now();
  try {
    for (let offset = 0; offset < totalEvents; offset += 1) {
      if (shouldStop()) break;
      const plannedAt = startedAt + offset * intervalMillis;
      await sleep(Math.max(0, plannedAt - performance.now()));
      const auctionId = config.auctionIds[offset % config.auctionIds.length];
      const envelope = createBidUpdatedEnvelope({
        auctionId,
        bidId: config.bidIdBase + offset,
      });
      try {
        const subscriberCount = await redis.publish(envelope.channel, envelope.message);
        counters.publishedEvents += 1;
        if (subscriberCount === 0) counters.publishNoSubscribers += 1;
      } catch {
        counters.publishFailures += 1;
      }
    }
  } finally {
    await redis.close();
  }
}

function handleMessage({
  source,
  frame,
  config,
  counters,
  latency,
  sequenceEventIds,
  sessionSequences,
}) {
  counters.messages += 1;
  let message;
  try {
    message = JSON.parse(frame.body);
  } catch {
    counters.invalidMessages += 1;
    return;
  }
  const bidId = message?.latestBid?.bidId;
  if (
    message.type !== 'AUCTION_BID_UPDATED' ||
    message.auctionId !== source.auctionId ||
    typeof message.eventId !== 'string' ||
    !Number.isSafeInteger(bidId)
  ) {
    counters.invalidMessages += 1;
    return;
  }

  const firstEventId = sequenceEventIds.get(bidId);
  if (firstEventId === undefined) sequenceEventIds.set(bidId, message.eventId);
  else if (firstEventId !== message.eventId) counters.mismatchedEventIds += 1;

  const status = sessionSequences.get(source.sessionIndex)?.add(bidId);
  if (status === 'added') {
    counters.uniqueMessages += 1;
    latency.record(Date.now() - parsePublishedAt(message.occurredAt));
  } else if (status === 'duplicate') {
    counters.duplicateMessages += 1;
  } else {
    counters.outOfRangeMessages += 1;
  }
}

function calculateExpectedDeliveries(config, clients, eventCount) {
  let expected = 0;
  for (const client of clients) {
    const auctionIndex = config.auctionIds.indexOf(client.auctionId);
    if (auctionIndex === -1 || auctionIndex >= eventCount) continue;
    expected += Math.floor((eventCount - 1 - auctionIndex) / config.auctionIds.length) + 1;
  }
  return expected;
}

function publicConfig(config) {
  const { redisUrl, ...safe } = config;
  return {
    ...safe,
    redisTarget: redisUrl ? new URL(redisUrl).host : undefined,
    localConnections: localConnectionIndexes(config).length,
  };
}
