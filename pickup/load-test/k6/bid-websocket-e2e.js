import http from 'k6/http';
import ws from 'k6/ws';
import { Counter, Trend } from 'k6/metrics';
import { check, sleep } from 'k6';
import { connect, parseFrame, subscribe } from './stomp.js';

const baseUrl = __ENV.TEST_BASE_URL;
const wsUrl = __ENV.TEST_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const tokens = (__ENV.TEST_ACCESS_TOKENS || '').split(',').filter(Boolean);
const initialPrices = Number(__ENV.TEST_INITIAL_BID_PRICE || 100000);
const bidIncrement = Number(__ENV.TEST_BID_INCREMENT || 1000);

export const bidSuccess = new Counter('bid_success');
export const bidFailures = new Counter('bid_failures');
export const wsEvents = new Counter('ws_events_received');
export const wsOrderErrors = new Counter('ws_order_errors');
export const wsDeliveryLatency = new Trend('ws_delivery_latency');

export const options = {
  scenarios: {
    observers: {
      exec: 'observer',
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '120s', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '600s', target: 100 },
        { duration: '120s', target: 250 },
        { duration: '30s', target: 250 },
        { duration: '600s', target: 250 },
        { duration: '120s', target: 500 },
        { duration: '30s', target: 500 },
        { duration: '600s', target: 500 },
        { duration: '120s', target: 750 },
        { duration: '30s', target: 750 },
        { duration: '600s', target: 750 },
        { duration: '120s', target: 1000 },
        { duration: '30s', target: 1000 },
        { duration: '600s', target: 1000 },
      ],
      gracefulRampDown: '0s',
    },
    bidders: {
      exec: 'bidder',
      executor: 'constant-vus',
      vus: 2,
      duration: '3750s',
      startTime: '0s',
    },
  },
  thresholds: {
    bid_failures: ['count==0'],
    ws_order_errors: ['count==0'],
    checks: ['rate>0.999'],
  },
};

export function observer() {
  const auctionId = auctionIds[(__VU - 1) % auctionIds.length];
  let previousBidId = 0;
  ws.connect(wsUrl, { headers: { Origin: origin } }, (socket) => {
    socket.on('open', () => {
      connect(socket);
      subscribe(socket, auctionId);
    });
    socket.on('message', (raw) => {
      const message = parseFrame(raw);
      if (!message || message.command !== 'MESSAGE') return;
      const receivedAt = Date.now();
      const payload = JSON.parse(message.body);
      const bidId = Number(payload.latestBid?.bidId);
      wsEvents.add(1);
      if (bidId <= previousBidId) wsOrderErrors.add(1);
      previousBidId = Math.max(previousBidId, bidId);
      if (payload.occurredAt) wsDeliveryLatency.add(receivedAt - Date.parse(payload.occurredAt));
    });
    socket.setInterval(() => socket.send('\n'), 10000);
  });
}

export function bidder() {
  const index = (__VU - 1) % auctionIds.length;
  const auctionId = auctionIds[index];
  const token = tokens[index % tokens.length];
  const price = initialPrices + (__ITER + 1) * bidIncrement;
  const response = http.post(`${baseUrl}/auctions/${auctionId}/bids`, JSON.stringify({ bidPrice: price }), {
    headers: { 'Content-Type': 'application/json', Origin: origin, Cookie: `access-token=${token}` },
  });
  const ok = check(response, { 'bid accepted': (r) => r.status === 201 });
  if (ok) bidSuccess.add(1);
  else bidFailures.add(1);
  sleep(1);
}
