import {
  requireNonNegativeInteger,
  requireOrigin,
  requirePositiveInteger,
  requirePositiveIntegerArray,
  requireRatio,
  requireString,
  requireUrl,
} from './config.mjs';

export function validateWebSocketConfig(raw, mode) {
  if (!['idle', 'fanout'].includes(mode)) {
    throw new Error('WebSocket 부하 모드는 idle 또는 fanout이어야 합니다.');
  }

  const connections = requirePositiveInteger(raw.connections, 'connections');
  const shardCount = requirePositiveInteger(raw.shardCount ?? 1, 'shardCount');
  const shardIndex = requireNonNegativeInteger(raw.shardIndex ?? 0, 'shardIndex');
  if (shardIndex >= shardCount) {
    throw new Error('shardIndex는 shardCount보다 작아야 합니다.');
  }

  const config = {
    mode,
    runId: requireString(raw.runId, 'runId'),
    wsUrl: requireUrl(raw.wsUrl, 'wsUrl', ['ws:', 'wss:']),
    origin: requireOrigin(raw.origin, 'origin'),
    auctionIds: requirePositiveIntegerArray(raw.auctionIds, 'auctionIds'),
    connections,
    shardCount,
    shardIndex,
    rampSeconds: requireNonNegativeInteger(raw.rampSeconds ?? 60, 'rampSeconds'),
    stabilizationSeconds: requireNonNegativeInteger(
      raw.stabilizationSeconds ?? 30,
      'stabilizationSeconds',
    ),
    durationSeconds: requirePositiveInteger(raw.durationSeconds, 'durationSeconds'),
    heartbeatMillis: requirePositiveInteger(raw.heartbeatMillis ?? 10_000, 'heartbeatMillis'),
    connectTimeoutMillis: requirePositiveInteger(
      raw.connectTimeoutMillis ?? 15_000,
      'connectTimeoutMillis',
    ),
    deliveryGraceMillis: requireNonNegativeInteger(
      raw.deliveryGraceMillis ?? 2000,
      'deliveryGraceMillis',
    ),
    minimumConnectedRatio: requireRatio(
      raw.minimumConnectedRatio ?? 0.999,
      'minimumConnectedRatio',
    ),
    abruptCloseRatio: requireRatio(raw.abruptCloseRatio ?? 0, 'abruptCloseRatio'),
    resultPath: requireString(raw.resultPath, 'resultPath'),
  };

  if (mode === 'fanout') {
    config.redisUrl = requireUrl(raw.redisUrl, 'redisUrl', ['redis:', 'rediss:']);
    config.eventsPerSecond = requirePositiveInteger(raw.eventsPerSecond, 'eventsPerSecond');
    config.bidIdBase = requirePositiveInteger(raw.bidIdBase, 'bidIdBase');
    config.publishEvents = raw.publishEvents ?? shardIndex === 0;
    if (typeof config.publishEvents !== 'boolean') {
      throw new Error('publishEvents는 boolean이어야 합니다.');
    }
    if (raw.startAtEpochMillis !== undefined) {
      config.startAtEpochMillis = requirePositiveInteger(
        raw.startAtEpochMillis,
        'startAtEpochMillis',
      );
    } else if (shardCount > 1) {
      throw new Error('여러 shard로 fanout 시험을 실행할 때 startAtEpochMillis가 필요합니다.');
    }
  }

  return config;
}

export function localConnectionIndexes(config) {
  const indexes = [];
  for (let index = config.shardIndex; index < config.connections; index += config.shardCount) {
    indexes.push(index);
  }
  return indexes;
}
