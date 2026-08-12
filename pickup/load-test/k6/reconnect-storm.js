import ws from 'k6/ws';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { connect, parseFrame, subscribe } from './stomp.js';

const wsUrl = __ENV.TEST_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const targetVus = Number(__ENV.TARGET_VUS || 300);
const reconnectCycles = Number(__ENV.RECONNECT_CYCLES || 3);
const firstConnectionSeconds = Number(__ENV.FIRST_CONNECTION_SECONDS || 60);
const reconnectHoldSeconds = Number(__ENV.RECONNECT_HOLD_SECONDS || 20);
const reconnectBackoffEnabled = __ENV.RECONNECT_BACKOFF_ENABLED === 'true';
const requiredReconnects = Math.ceil(targetVus * reconnectCycles * 0.999);
const requiredStompConnections = Math.ceil(targetVus * (reconnectCycles + 1) * 0.999);

export const initialOpenSuccess = new Counter('initial_open_success');
export const reconnectAttempts = new Counter('reconnect_attempts');
export const reconnectSuccess = new Counter('reconnect_success');
export const reconnectFailures = new Counter('reconnect_failures');
export const stompConnected = new Counter('stomp_connected');
export const wsErrors = new Counter('ws_errors');
export const initialHandshakeLatency = new Trend('initial_handshake_latency');
export const reconnectHandshakeLatency = new Trend('reconnect_handshake_latency');
export const reconnectOpenRecoveryLatency = new Trend('reconnect_open_recovery_latency');
export const reconnectStompRecoveryLatency = new Trend('reconnect_stomp_recovery_latency');

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
    reconnect_success: [`count>=${requiredReconnects}`],
    reconnect_failures: ['count==0'],
    stomp_connected: [`count>=${requiredStompConnections}`],
    ws_errors: ['count==0'],
    initial_handshake_latency: ['p(95)<5000'],
    reconnect_handshake_latency: ['p(95)<5000'],
  },
};

export function setup() {
  return { firstCloseAt: Date.now() + firstConnectionSeconds * 1000 };
}

function connectSession(auctionId, closeAt, isReconnect, recoveryStartedAt) {
  const startedAt = Date.now();
  let isStompConnected = false;

  const response = ws.connect(wsUrl, { headers: { Origin: origin } }, (socket) => {
    socket.on('open', () => {
      if (isReconnect) {
        reconnectSuccess.add(1);
        reconnectHandshakeLatency.add(Date.now() - startedAt);
        reconnectOpenRecoveryLatency.add(Date.now() - recoveryStartedAt);
      } else {
        initialOpenSuccess.add(1);
        initialHandshakeLatency.add(Date.now() - startedAt);
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
      if (!message || message.command !== 'CONNECTED' || isStompConnected) return;
      isStompConnected = true;
      stompConnected.add(1);
      if (isReconnect) {
        reconnectStompRecoveryLatency.add(Date.now() - recoveryStartedAt);
      }
      subscribe(socket, auctionId);
    });
    socket.on('error', () => wsErrors.add(1));
  });

  if (isReconnect && (!response || response.status !== 101)) reconnectFailures.add(1);
}

export default function ({ firstCloseAt }) {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  reconnectFailures.add(0);
  wsErrors.add(0);

  connectSession(auctionId, firstCloseAt, false, null);

  for (let attempt = 1; attempt <= reconnectCycles; attempt += 1) {
    const recoveryStartedAt = Date.now();
    if (reconnectBackoffEnabled) {
      sleep(1 + Math.random());
    }
    reconnectAttempts.add(1, { attempt: String(attempt) });
    connectSession(auctionId, null, true, recoveryStartedAt);
  }
}
