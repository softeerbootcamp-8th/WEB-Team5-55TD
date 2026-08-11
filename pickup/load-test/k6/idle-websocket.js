import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { check, sleep } from 'k6';
import { connect, subscribe } from './stomp.js';

const url = __ENV.TEST_WS_URL;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 600);

export const wsConnectFailures = new Counter('ws_connect_failures');
export const wsConnectLatency = new Trend('ws_connect_latency');

export const options = {
  stages: [
    { duration: '60s', target: 100 },
    { duration: '60s', target: 100 },
    { duration: '60s', target: 250 },
    { duration: '60s', target: 250 },
    { duration: '60s', target: 500 },
    { duration: '60s', target: 500 },
    { duration: '60s', target: 750 },
    { duration: '60s', target: 750 },
    { duration: '60s', target: 1000 },
    { duration: '60s', target: 1000 },
  ],
  gracefulRampDown: '0s',
  thresholds: {
    ws_connect_failures: ['count==0'],
    checks: ['rate>0.999'],
  },
};

export default function () {
  const startedAt = Date.now();
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  const response = ws.connect(url, { headers: { Origin: __ENV.TEST_ORIGIN } }, (socket) => {
    socket.on('open', () => {
      connect(socket);
      subscribe(socket, auctionId);
      socket.setInterval(() => socket.send('\n'), 10000);
      socket.setTimeout(() => socket.close(), holdSeconds * 1000);
    });
  });
  wsConnectLatency.add(Date.now() - startedAt);
  const connected = check(response, { 'websocket status is 101': (r) => r && r.status === 101 });
  if (!connected) wsConnectFailures.add(1);
  sleep(1);
}
