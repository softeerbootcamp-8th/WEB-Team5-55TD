// 입찰 동시성 제어 4방식 비교용 k6 스크립트.
// 방식 하나당 이 스크립트를 한 번씩(=총 4회) 별도로 돌린다. 동시에 여러 방식을 같이 돌리면
// 같은 인스턴스의 CPU/커넥션풀/Redis를 나눠 쓰게 되어 방식 간 비교가 무의미해진다.
//
// 실행 예:
//   k6 run \
//     -e BASE_URL=http://<ec2-host>:8080 \
//     -e ENDPOINT_PATH=/bids/distributed-lock \
//     -e AUCTION_ID=101 \
//     -e BIDDER_COUNT=50 \
//     -e SCENARIO=fixed_load \
//     load-test/k6-bid-concurrency.js
//
// ENDPOINT_PATH 후보: /bids | /bids/distributed-lock | /bids/conditional-update | /bids/short-transaction
// SCENARIO 후보: fixed_load(4방식 동일 조건 비교) | ramp_to_break(방식별 한계치 탐색)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ENDPOINT_PATH = __ENV.ENDPOINT_PATH || '/bids';
const AUCTION_ID = __ENV.AUCTION_ID;
const BIDDER_COUNT = parseInt(__ENV.BIDDER_COUNT || '50', 10);
const SCENARIO = __ENV.SCENARIO || 'fixed_load';

if (!AUCTION_ID) {
  throw new Error('AUCTION_ID 환경변수가 필요하다 (seed.sql 실행 결과의 auction_id).');
}

// 성공(2xx)과 "설계상 정상적인 낙찰 실패"(409 OUTBID_EXISTS/BELOW_MIN_INCREMENT)는 분리해서 집계한다.
// 이 둘을 error rate에 합쳐 세면 동시성 제어가 정상 동작할수록(=낙찰 경쟁이 격해질수록) 오히려
// "에러율"이 높아지는 것처럼 보이는 착시가 생긴다. 진짜 장애 신호는 systemErrors뿐이다.
const bidAccepted = new Counter('bid_accepted'); // 201/202
const bidOutbid = new Counter('bid_outbid_rejected'); // 409 OUTBID_EXISTS / BELOW_MIN_INCREMENT
const bidLockFailed = new Counter('bid_lock_acquisition_failed'); // 409 BID_LOCK_ACQUISITION_FAILED (A 방식 전용)
const systemErrors = new Counter('bid_system_errors'); // 5xx, 타임아웃, 그 외 예상 못한 응답
const bidDuration = new Trend('bid_duration_ms', true);

export const options = {
  scenarios: buildScenario(SCENARIO),
  thresholds: {
    // 진짜 장애만 이 threshold에 들어간다. 4방식 모두 이 기준을 넘으면 "무너졌다"로 판단한다.
    bid_system_errors: ['count<50'],
  },
};

function buildScenario(name) {
  if (name === 'ramp_to_break') {
    // 방식별 한계치(더 이상 시스템 오류 없이 버티는 최대 동시성) 탐색용.
    // 4방식 모두 같은 스테이지 정의를 쓰되, 어느 지점에서 bid_system_errors가 튀는지를 비교한다.
    return {
      default: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: '30s', target: 20 },
          { duration: '30s', target: 50 },
          { duration: '30s', target: 100 },
          { duration: '30s', target: 200 },
          { duration: '30s', target: 400 },
          { duration: '30s', target: 0 },
        ],
        gracefulRampDown: '10s',
      },
    };
  }
  // fixed_load: 4방식에 동일하게 고정 동시성을 몇 분간 유지 — apples-to-apples 비교용.
  return {
    default: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 200 },
        { duration: '3m', target: 200 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  };
}

// setup()은 1회만 실행된다. 입찰자 계정을 자체 생성/로그인해 토큰을 미리 확보해 둔다.
// 이미 존재하는 계정(이전 실행에서 만든)은 409를 무시하고 로그인만 진행한다.
export function setup() {
  const tokens = [];
  for (let i = 0; i < BIDDER_COUNT; i++) {
    const loginId = `loadtest_bidder_${i}`;
    const password = 'password123';

    http.post(
      `${BASE_URL}/members`,
      JSON.stringify({ loginId, nickname: `부하테스트입찰자${i}`, password }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    // 계정이 이미 있으면 위 요청은 409를 반환하지만 무시하고 그대로 로그인을 시도한다.

    const loginRes = http.post(
      `${BASE_URL}/auth`,
      JSON.stringify({ loginId, password }),
      { headers: { 'Content-Type': 'application/json' } },
    );

    const cookie = extractAccessTokenCookie(loginRes);
    if (!cookie) {
      throw new Error(`입찰자 ${loginId} 로그인 실패 - status=${loginRes.status}, body=${loginRes.body}`);
    }
    tokens.push(cookie);
  }
  return { tokens };
}

function extractAccessTokenCookie(res) {
  const setCookieHeaders = res.headers['Set-Cookie'];
  if (!setCookieHeaders) return null;
  const headers = Array.isArray(setCookieHeaders) ? setCookieHeaders : [setCookieHeaders];
  for (const header of headers) {
    const match = header.match(/access-token=([^;]+)/);
    if (match) return `access-token=${match[1]}`;
  }
  return null;
}

export default function (data) {
  const cookie = data.tokens[__VU % data.tokens.length];
  const headers = { Cookie: cookie, 'Content-Type': 'application/json' };

  // 현재가를 읽고 그 바로 위 값으로 입찰한다 — 여러 VU가 거의 같은 순간에 같은 가격대를
  // 노리게 되어 동시성 제어 로직이 실제로 경쟁 상황에 놓이게 만든다.
  const auctionRes = http.get(`${BASE_URL}/auctions/${AUCTION_ID}`, { headers });
  if (auctionRes.status !== 200) {
    systemErrors.add(1);
    sleep(0.2);
    return;
  }
  const nextMinBid = auctionRes.json('nextMinBid');

  const start = Date.now();
  const bidRes = http.post(
    `${BASE_URL}${ENDPOINT_PATH}`,
    JSON.stringify({ bidPrice: nextMinBid }),
    { headers },
  );
  bidDuration.add(Date.now() - start);

  classify(bidRes);
  sleep(0.1);
}

function classify(res) {
  if (res.status === 201 || res.status === 202) {
    bidAccepted.add(1);
    check(res, { '입찰 성공 응답 형식이 맞다': (r) => !!r.json('bidPrice') });
    return;
  }
  if (res.status === 409) {
    const errorCode = safeErrorCode(res);
    if (errorCode === 'BID_LOCK_ACQUISITION_FAILED') {
      bidLockFailed.add(1);
    } else if (errorCode === 'OUTBID_EXISTS' || errorCode === 'BELOW_MIN_INCREMENT') {
      bidOutbid.add(1);
    } else {
      systemErrors.add(1);
    }
    return;
  }
  systemErrors.add(1);
}

function safeErrorCode(res) {
  try {
    return res.json('error');
  } catch (e) {
    return null;
  }
}
