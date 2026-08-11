import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { connect, parseFrame, subscribe } from './stomp.js';

const baseUrl = __ENV.TEST_BASE_URL;
const wsUrl = __ENV.TEST_WS_URL;
const origin = __ENV.TEST_ORIGIN;
const auctionIds = (__ENV.TEST_AUCTION_IDS || '').split(',').filter(Boolean);
const auctionId = auctionIds[0];
const loginId = __ENV.TEST_LOGIN_ID;
const loginPassword = __ENV.TEST_LOGIN_PASSWORD;
const initialBidPrice = Number(__ENV.TEST_INITIAL_BID_PRICE || 1500000);
const bidIncrement = Number(__ENV.TEST_BID_INCREMENT || 43000);
const bidIntervalSeconds = Number(__ENV.BID_INTERVAL_SECONDS || 2);
const targetVus = Number(__ENV.TARGET_VUS || 300);
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 120);
const scenarioSeconds = rampSeconds + holdSeconds;
const requiredConnections = Math.ceil(targetVus * 0.999);

export const bidSuccess = new Counter('bid_success');
export const bidFailures = new Counter('bid_failures');
export const wsOpenSuccess = new Counter('ws_open_success');
export const stompConnected = new Counter('stomp_connected');
export const wsConnectFailures = new Counter('ws_connect_failures');
export const wsErrors = new Counter('ws_errors');
export const wsEventsReceived = new Counter('ws_events_received');
export const wsDuplicateEvents = new Counter('ws_duplicate_events');
export const wsOrderErrors = new Counter('ws_order_errors');
export const wsHandshakeLatency = new Trend('ws_handshake_latency');
export const wsDeliveryLatency = new Trend('ws_delivery_latency');

export const options = {
  scenarios: {
    observers: {
      exec: 'observer',
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${rampSeconds}s`, target: targetVus },
        { duration: `${holdSeconds}s`, target: targetVus },
      ],
      gracefulRampDown: '0s',
    },
    bidder: {
      exec: 'bidder',
      executor: 'constant-vus',
      vus: 1,
      duration: `${scenarioSeconds}s`,
    },
  },
  thresholds: {
    bid_failures: ['count==0'],
    ws_open_success: [`count>=${requiredConnections}`],
    stomp_connected: [`count>=${requiredConnections}`],
    ws_connect_failures: ['count==0'],
    ws_errors: ['count==0'],
    ws_duplicate_events: ['count==0'],
    ws_order_errors: ['count==0'],
    ws_handshake_latency: ['p(95)<5000'],
    ws_delivery_latency: ['p(95)<500', 'p(99)<1000'],
    checks: ['rate>0.999'],
  },
};

export function setup() {
  const detailResponse = http.get(`${baseUrl}/auctions/${auctionId}`, {
    headers: { Origin: origin },
  });
  if (detailResponse.status !== 200) {
    fail(`auction detail request failed with status ${detailResponse.status}`);
  }

  const auction = detailResponse.json();
  if (auction.auctionStatus !== 'ONGOING') {
    fail(`auction ${auctionId} is not ongoing`);
  }
  if (Number(auction.bidIncrement) > bidIncrement) {
    fail(`auction bid increment ${auction.bidIncrement} exceeds configured increment ${bidIncrement}`);
  }

  const loginResponse = http.post(
    `${baseUrl}/auth`,
    JSON.stringify({ loginId, password: loginPassword }),
    { headers: { 'Content-Type': 'application/json', Origin: origin } },
  );
  const cookieHeader = loginResponse.headers['Set-Cookie'] || loginResponse.headers['set-cookie'] || '';
  const accessToken = cookieHeader.match(/(?:^|[,; ])access-token=([^;]+)/)?.[1];
  const isLoggedIn = check(loginResponse, {
    'test account login succeeded': (response) => response.status === 200 && Boolean(accessToken),
  });
  if (!isLoggedIn) fail(`test account login failed with status ${loginResponse.status}`);

  return {
    accessToken,
    firstBidPrice: Math.max(initialBidPrice, Number(auction.nextMinBid)),
  };
}

export function observer() {
  const startedAt = Date.now();
  const seenEventIds = new Set();
  let isStompConnected = false;
  let previousBidId = 0;

  wsConnectFailures.add(0);
  wsErrors.add(0);
  wsDuplicateEvents.add(0);
  wsOrderErrors.add(0);

  const response = ws.connect(wsUrl, { headers: { Origin: origin } }, (socket) => {
    socket.on('open', () => {
      wsOpenSuccess.add(1);
      wsHandshakeLatency.add(Date.now() - startedAt);
      connect(socket);
      socket.setInterval(() => socket.send('\n'), 10000);
    });
    socket.on('message', (raw) => {
      const message = parseFrame(raw);
      if (!message) return;
      if (message.command === 'CONNECTED' && !isStompConnected) {
        isStompConnected = true;
        stompConnected.add(1);
        subscribe(socket, auctionId);
        return;
      }
      if (message.command !== 'MESSAGE') return;

      const payload = JSON.parse(message.body);
      const eventId = payload.eventId;
      const bidId = Number(payload.latestBid?.bidId);
      wsEventsReceived.add(1);

      if (eventId && seenEventIds.has(eventId)) wsDuplicateEvents.add(1);
      if (eventId) seenEventIds.add(eventId);
      if (bidId < previousBidId) wsOrderErrors.add(1);
      previousBidId = Math.max(previousBidId, bidId);
      if (payload.occurredAt) wsDeliveryLatency.add(Date.now() - Date.parse(payload.occurredAt));
    });
    socket.on('error', () => wsErrors.add(1));
  });

  if (!response || response.status !== 101) wsConnectFailures.add(1);
}

export function bidder({ accessToken, firstBidPrice }) {
  bidFailures.add(0);
  const bidPrice = firstBidPrice + __ITER * bidIncrement;
  const response = http.post(
    `${baseUrl}/auctions/${auctionId}/bids`,
    JSON.stringify({ bidPrice }),
    {
      headers: {
        'Content-Type': 'application/json',
        Origin: origin,
        Cookie: `access-token=${accessToken}`,
      },
    },
  );
  const isAccepted = check(response, { 'bid accepted': (result) => result.status === 201 });
  if (isAccepted) bidSuccess.add(1);
  else bidFailures.add(1, { status: String(response.status) });
  sleep(bidIntervalSeconds);
}
