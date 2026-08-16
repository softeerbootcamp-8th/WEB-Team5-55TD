import http from 'k6/http';
import ws from 'k6/ws';
import { check, fail } from 'k6';
import exec from 'k6/execution';
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
const rampSeconds = Number(__ENV.RAMP_SECONDS || 60);
const holdSeconds = Number(__ENV.HOLD_SECONDS || 60);
const capacityTargets = [300, 700, 1000];
const scenarioSeconds = capacityTargets.length * (rampSeconds + holdSeconds);
const requiredConnections = Math.ceil(
  capacityTargets[capacityTargets.length - 1] * 0.999,
);

export const bidSuccess = new Counter('bid_success');
export const bidFailures = new Counter('bid_failures');
export const wsOpenSuccess = new Counter('ws_open_success');
export const stompConnected = new Counter('stomp_connected');
export const wsConnectFailures = new Counter('ws_connect_failures');
export const wsErrors = new Counter('ws_errors');
export const wsUnexpectedCloses = new Counter('ws_unexpected_closes');
export const wsEventsReceived = new Counter('ws_events_received');
export const wsDuplicateEvents = new Counter('ws_duplicate_events');
export const wsOrderErrors = new Counter('ws_order_errors');
export const wsHandshakeLatency = new Trend('ws_handshake_latency');
export const wsDeliveryLatency = new Trend('ws_delivery_latency');
export const bidHttpDuration = new Trend('bid_http_duration');

export const options = {
  scenarios: {
    observers: {
      exec: 'observer',
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
    bidder: {
      exec: 'bidder',
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '1s',
      duration: `${scenarioSeconds}s`,
      preAllocatedVUs: 1,
      maxVUs: 10,
    },
  },
  thresholds: {
    bid_failures: ['count==0'],
    ws_open_success: [`count>=${requiredConnections}`],
    stomp_connected: [`count>=${requiredConnections}`],
    ws_connect_failures: ['count==0'],
    ws_errors: ['count==0'],
    ws_unexpected_closes: ['count==0'],
    ws_duplicate_events: ['count==0'],
    ws_order_errors: ['count==0'],
    ws_handshake_latency: ['p(95)<5000'],
    ws_delivery_latency: ['p(95)<500', 'p(99)<1000'],
    'ws_delivery_latency{stage:hold-300}': ['p(95)<500', 'p(99)<1000'],
    'ws_delivery_latency{stage:hold-700}': ['p(95)<500', 'p(99)<1000'],
    'ws_delivery_latency{stage:hold-1000}': ['p(95)<500', 'p(99)<1000'],
    dropped_iterations: ['count==0'],
    checks: ['rate>0.999'],
  },
};

function getCapacityStage(testStartedAt) {
  const elapsedSeconds = Math.max(0, (Date.now() - testStartedAt) / 1000);
  const stageSeconds = rampSeconds + holdSeconds;
  const targetIndex = Math.min(
    Math.floor(elapsedSeconds / stageSeconds),
    capacityTargets.length - 1,
  );
  const targetElapsedSeconds = elapsedSeconds - targetIndex * stageSeconds;
  const phase = targetElapsedSeconds < rampSeconds ? 'ramp' : 'hold';
  return `${phase}-${capacityTargets[targetIndex]}`;
}

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
    fail(
      `auction bid increment ${auction.bidIncrement} exceeds configured increment ${bidIncrement}`,
    );
  }

  const loginResponse = http.post(
    `${baseUrl}/auth`,
    JSON.stringify({ loginId, password: loginPassword }),
    { headers: { 'Content-Type': 'application/json', Origin: origin } },
  );
  const cookieHeader =
    loginResponse.headers['Set-Cookie'] ||
    loginResponse.headers['set-cookie'] ||
    '';
  const accessToken = cookieHeader.match(
    /(?:^|[,; ])access-token=([^;]+)/,
  )?.[1];
  const isLoggedIn = check(loginResponse, {
    'test account login succeeded': (response) =>
      response.status === 200 && Boolean(accessToken),
  });
  if (!isLoggedIn)
    fail(`test account login failed with status ${loginResponse.status}`);

  const testStartedAt = Date.now();
  return {
    accessToken,
    firstBidPrice: Math.max(initialBidPrice, Number(auction.nextMinBid)),
    testStartedAt,
    testEndsAt: testStartedAt + scenarioSeconds * 1000,
  };
}

export function observer({ testStartedAt, testEndsAt }) {
  const startedAt = Date.now();
  const seenEventIds = new Set();
  let isStompConnected = false;
  let previousBidId = 0;

  wsConnectFailures.add(0);
  wsErrors.add(0);
  wsUnexpectedCloses.add(0);
  wsDuplicateEvents.add(0);
  wsOrderErrors.add(0);

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
        if (payload.occurredAt) {
          wsDeliveryLatency.add(Date.now() - Date.parse(payload.occurredAt), {
            stage: getCapacityStage(testStartedAt),
          });
        }
      });
      socket.on('error', () => wsErrors.add(1));
      socket.on('close', () => {
        if (Date.now() < testEndsAt - 1000) wsUnexpectedCloses.add(1);
      });
    },
  );

  if (!response || response.status !== 101) wsConnectFailures.add(1);
}

export function bidder({ accessToken, firstBidPrice, testStartedAt }) {
  bidFailures.add(0);
  const bidPrice = firstBidPrice + exec.scenario.iterationInTest * bidIncrement;
  const stage = getCapacityStage(testStartedAt);
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
  bidHttpDuration.add(response.timings.duration, { stage });
  const isAccepted = check(response, {
    'bid accepted': (result) => result.status === 201,
  });
  if (isAccepted) bidSuccess.add(1);
  else bidFailures.add(1, { status: String(response.status) });
}
