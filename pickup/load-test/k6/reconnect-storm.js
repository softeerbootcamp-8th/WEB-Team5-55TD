import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { check, sleep } from 'k6';
import { connect, subscribe } from './stomp.js';

const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const attempts = Number(__ENV.RECONNECT_ATTEMPTS || 3);
const holdSeconds = Number(__ENV.RECONNECT_HOLD_SECONDS || 30);

export const reconnectAttempts = new Counter('reconnect_attempts');
export const reconnectSuccess = new Counter('reconnect_success');
export const reconnectLatency = new Trend('reconnect_latency');

export const options = {
  vus: Number(__ENV.RECONNECT_VUS || 1000),
  duration: `${Number(__ENV.RECONNECT_DURATION_SECONDS || 300)}s`,
  thresholds: {
    reconnect_success: ['count>0'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const startedAt = Date.now();
    const response = ws.connect(__ENV.TEST_WS_URL, { headers: { Origin: __ENV.TEST_ORIGIN } }, (socket) => {
      socket.on('open', () => {
        connect(socket);
        subscribe(socket, auctionId);
        socket.setInterval(() => socket.send('\n'), 10000);
        socket.setTimeout(() => socket.close(), holdSeconds * 1000);
      });
    });
    reconnectAttempts.add(1);
    const ok = check(response, { 'reconnect websocket status is 101': (r) => r && r.status === 101 });
    if (ok) reconnectSuccess.add(1);
    reconnectLatency.add(Date.now() - startedAt);
    sleep(Math.min(30, 2 ** (attempt - 1)) + Math.random());
  }
}
