import ws from 'k6/ws';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { connect, parseFrame, subscribe } from './stomp.js';

const wsUrl = __ENV.TEST_WS_URL;
const failureWsUrl = __ENV.TEST_FAILURE_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const targetVus = Number(__ENV.TARGET_VUS || 300);
const forcedFailures = Number(__ENV.RECONNECT_FORCED_FAILURES || 5);
const firstConnectionSeconds = Number(__ENV.FIRST_CONNECTION_SECONDS || 60);
const reconnectHoldSeconds = Number(__ENV.RECONNECT_HOLD_SECONDS || 20);
const reconnectPolicy = __ENV.RECONNECT_POLICY || 'backoff-jitter';
if (!['immediate', 'backoff-jitter'].includes(reconnectPolicy)) {
  throw new Error(`unsupported reconnect policy: ${reconnectPolicy}`);
}
const requiredReconnectAttempts = Math.ceil(
  targetVus * (forcedFailures + 1) * 0.999,
);
const requiredForcedFailures = Math.ceil(targetVus * forcedFailures * 0.999);
const requiredReconnects = Math.ceil(targetVus * 0.999);
const requiredStompConnections = Math.ceil(targetVus * 2 * 0.999);
const initialReconnectDelayMillis = 1_000;
const maxReconnectDelayMillis = 30_000;
const reconnectJitterMillis = 1_000;
const reconnectDelayThresholds =
  reconnectPolicy === 'immediate'
    ? {
        'reconnect_backoff_delay{attempt:1}': ['max==0'],
        'reconnect_backoff_delay{attempt:2}': ['max==0'],
        'reconnect_backoff_delay{attempt:3}': ['max==0'],
        'reconnect_backoff_delay{attempt:4}': ['max==0'],
        'reconnect_backoff_delay{attempt:5}': ['max==0'],
        'reconnect_backoff_delay{attempt:6}': ['max==0'],
      }
    : {
        'reconnect_backoff_delay{attempt:1}': ['min>=1000', 'max<2000'],
        'reconnect_backoff_delay{attempt:2}': ['min>=2000', 'max<3000'],
        'reconnect_backoff_delay{attempt:3}': ['min>=4000', 'max<5000'],
        'reconnect_backoff_delay{attempt:4}': ['min>=8000', 'max<9000'],
        'reconnect_backoff_delay{attempt:5}': ['min>=16000', 'max<17000'],
        'reconnect_backoff_delay{attempt:6}': ['min>=30000', 'max<=30000'],
      };

export const initialOpenSuccess = new Counter('initial_open_success');
export const reconnectAttempts = new Counter('reconnect_attempts');
export const forcedReconnectFailures = new Counter('forced_reconnect_failures');
export const unexpectedFailureEndpointSuccess = new Counter(
  'unexpected_failure_endpoint_success',
);
export const reconnectSuccess = new Counter('reconnect_success');
export const reconnectFailures = new Counter('reconnect_failures');
export const stompConnected = new Counter('stomp_connected');
export const wsErrors = new Counter('ws_errors');
export const initialHandshakeLatency = new Trend('initial_handshake_latency');
export const reconnectBackoffDelay = new Trend('reconnect_backoff_delay');
export const reconnectHandshakeLatency = new Trend(
  'reconnect_handshake_latency',
);
export const reconnectOpenRecoveryLatency = new Trend(
  'reconnect_open_recovery_latency',
);
export const reconnectStompRecoveryLatency = new Trend(
  'reconnect_stomp_recovery_latency',
);

export const options = {
  scenarios: {
    reconnectStorm: {
      executor: 'per-vu-iterations',
      vus: targetVus,
      iterations: 1,
      maxDuration: '240s',
    },
  },
  thresholds: {
    initial_open_success: [`count>=${Math.ceil(targetVus * 0.999)}`],
    reconnect_attempts: [`count>=${requiredReconnectAttempts}`],
    forced_reconnect_failures: [`count>=${requiredForcedFailures}`],
    unexpected_failure_endpoint_success: ['count==0'],
    reconnect_success: [`count>=${requiredReconnects}`],
    reconnect_failures: ['count==0'],
    stomp_connected: [`count>=${requiredStompConnections}`],
    ws_errors: ['count==0'],
    initial_handshake_latency: ['p(95)<5000'],
    reconnect_handshake_latency: ['p(95)<5000'],
    ...reconnectDelayThresholds,
  },
};

export function setup() {
  return { firstCloseAt: Date.now() + firstConnectionSeconds * 1000 };
}

function calculateReconnectDelay(attempt) {
  if (reconnectPolicy === 'immediate') return 0;

  const baseDelay = Math.min(
    initialReconnectDelayMillis * 2 ** Math.max(attempt - 1, 0),
    maxReconnectDelayMillis,
  );
  const jitter = Math.floor(Math.random() * reconnectJitterMillis);
  return Math.min(baseDelay + jitter, maxReconnectDelayMillis);
}

function connectSession(
  auctionId,
  targetUrl,
  closeAt,
  phase,
  recoveryStartedAt,
) {
  const startedAt = Date.now();
  let isStompConnected = false;

  const response = ws.connect(
    targetUrl,
    { headers: { Origin: origin } },
    (socket) => {
      socket.on('open', () => {
        if (phase === 'recovery') {
          reconnectSuccess.add(1);
          reconnectHandshakeLatency.add(Date.now() - startedAt);
          reconnectOpenRecoveryLatency.add(Date.now() - recoveryStartedAt);
        } else if (phase === 'initial') {
          initialOpenSuccess.add(1);
          initialHandshakeLatency.add(Date.now() - startedAt);
        } else {
          unexpectedFailureEndpointSuccess.add(1);
        }
        connect(socket);
        socket.setInterval(() => socket.send('\n'), 10000);
        const closeDelay = closeAt
          ? Math.max(1, closeAt - Date.now())
          : reconnectHoldSeconds * 1000;
        socket.setTimeout(() => socket.close(), closeDelay);
      });
      socket.on('message', (raw) => {
        const message = parseFrame(raw);
        if (!message || message.command !== 'CONNECTED' || isStompConnected)
          return;
        isStompConnected = true;
        stompConnected.add(1);
        if (phase === 'recovery') {
          reconnectStompRecoveryLatency.add(Date.now() - recoveryStartedAt);
        }
        subscribe(socket, auctionId);
      });
      socket.on('error', () => {
        if (phase !== 'forced-failure') wsErrors.add(1);
      });
    },
  );

  const isConnected = response?.status === 101;
  if (phase === 'forced-failure' && !isConnected)
    forcedReconnectFailures.add(1);
  if (phase === 'recovery' && !isConnected) reconnectFailures.add(1);
}

export default function ({ firstCloseAt }) {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  reconnectFailures.add(0);
  forcedReconnectFailures.add(0);
  unexpectedFailureEndpointSuccess.add(0);
  wsErrors.add(0);

  connectSession(auctionId, wsUrl, firstCloseAt, 'initial', null);
  const recoveryStartedAt = Date.now();

  for (let attempt = 1; attempt <= forcedFailures; attempt += 1) {
    const delayMillis = calculateReconnectDelay(attempt);
    reconnectBackoffDelay.add(delayMillis, { attempt: String(attempt) });
    if (delayMillis > 0) sleep(delayMillis / 1000);
    reconnectAttempts.add(1, { attempt: String(attempt) });
    connectSession(
      auctionId,
      failureWsUrl,
      null,
      'forced-failure',
      recoveryStartedAt,
    );
  }

  const recoveryAttempt = forcedFailures + 1;
  const recoveryDelayMillis = calculateReconnectDelay(recoveryAttempt);
  reconnectBackoffDelay.add(recoveryDelayMillis, {
    attempt: String(recoveryAttempt),
  });
  if (recoveryDelayMillis > 0) sleep(recoveryDelayMillis / 1000);
  reconnectAttempts.add(1, { attempt: String(recoveryAttempt) });
  connectSession(auctionId, wsUrl, null, 'recovery', recoveryStartedAt);
}
