import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { connect, parseFrame, subscribe } from './stomp.js';

const wsUrl = __ENV.TEST_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 60);
const capacityTargets = [300, 700, 1000];
const scenarioSeconds = capacityTargets.length * (rampSeconds + holdSeconds);
const requiredConnections = Math.ceil(
  capacityTargets[capacityTargets.length - 1] * 0.999,
);

export const wsOpenSuccess = new Counter('ws_open_success');
export const stompConnected = new Counter('stomp_connected');
export const wsConnectFailures = new Counter('ws_connect_failures');
export const wsErrors = new Counter('ws_errors');
export const wsUnexpectedCloses = new Counter('ws_unexpected_closes');
export const wsHandshakeLatency = new Trend('ws_handshake_latency');

export const options = {
  scenarios: {
    idleConnections: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${rampSeconds}s`, target: 300 },
        { duration: `${holdSeconds}s`, target: 300 },
        { duration: `${rampSeconds}s`, target: 700 },
        { duration: `${holdSeconds}s`, target: 700 },
        { duration: `${rampSeconds}s`, target: 1000 },
        { duration: `${holdSeconds}s`, target: 1000 },
      ],
      gracefulRampDown: '0s',
    },
  },
  thresholds: {
    ws_open_success: [`count>=${requiredConnections}`],
    stomp_connected: [`count>=${requiredConnections}`],
    ws_connect_failures: ['count==0'],
    ws_errors: ['count==0'],
    ws_unexpected_closes: ['count==0'],
    ws_handshake_latency: ['p(95)<5000'],
  },
};

export function setup() {
  return { testEndsAt: Date.now() + scenarioSeconds * 1000 };
}

export default function ({ testEndsAt }) {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  const startedAt = Date.now();
  let isStompConnected = false;

  wsConnectFailures.add(0);
  wsErrors.add(0);
  wsUnexpectedCloses.add(0);

  const response = ws.connect(
    wsUrl,
    { headers: { Origin: origin } },
    (socket) => {
      socket.on('open', () => {
        wsOpenSuccess.add(1);
        wsHandshakeLatency.add(Date.now() - startedAt);
        connect(socket);
        socket.setInterval(() => socket.send('\n'), 10000);
      });
      socket.on('message', (raw) => {
        const message = parseFrame(raw);
        if (!message || message.command !== 'CONNECTED' || isStompConnected)
          return;
        isStompConnected = true;
        stompConnected.add(1);
        subscribe(socket, auctionId);
      });
      socket.on('error', () => wsErrors.add(1));
      socket.on('close', () => {
        if (Date.now() < testEndsAt - 1000) wsUnexpectedCloses.add(1);
      });
    },
  );

  if (!response || response.status !== 101) wsConnectFailures.add(1);
}
