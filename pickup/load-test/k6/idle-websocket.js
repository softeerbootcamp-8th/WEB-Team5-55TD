import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { connect, parseFrame, subscribe } from './stomp.js';

const wsUrl = __ENV.TEST_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const targetVus = Number(__ENV.TARGET_VUS || 1000);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 120);
const requiredConnections = Math.ceil(targetVus * 0.999);

export const wsOpenSuccess = new Counter('ws_open_success');
export const stompConnected = new Counter('stomp_connected');
export const wsConnectFailures = new Counter('ws_connect_failures');
export const wsErrors = new Counter('ws_errors');
export const wsHandshakeLatency = new Trend('ws_handshake_latency');

export const options = {
  scenarios: {
    idleConnections: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${rampSeconds}s`, target: targetVus },
        { duration: `${holdSeconds}s`, target: targetVus },
      ],
      gracefulRampDown: '0s',
    },
  },
  thresholds: {
    ws_open_success: [`count>=${requiredConnections}`],
    stomp_connected: [`count>=${requiredConnections}`],
    ws_connect_failures: ['count==0'],
    ws_errors: ['count==0'],
    ws_handshake_latency: ['p(95)<5000'],
  },
};

export default function () {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  const startedAt = Date.now();
  let isStompConnected = false;

  wsConnectFailures.add(0);
  wsErrors.add(0);

  const response = ws.connect(wsUrl, { headers: { Origin: origin } }, (socket) => {
    socket.on('open', () => {
      wsOpenSuccess.add(1);
      wsHandshakeLatency.add(Date.now() - startedAt);
      connect(socket);
      socket.setInterval(() => socket.send('\n'), 10000);
    });
    socket.on('message', (raw) => {
      const message = parseFrame(raw);
      if (!message || message.command !== 'CONNECTED' || isStompConnected) return;
      isStompConnected = true;
      stompConnected.add(1);
      subscribe(socket, auctionId);
    });
    socket.on('error', () => wsErrors.add(1));
  });

  if (!response || response.status !== 101) wsConnectFailures.add(1);
}
