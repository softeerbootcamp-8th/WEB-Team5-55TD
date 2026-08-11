import {
  requireOrigin,
  requirePositiveInteger,
  requireString,
  requireUrl,
} from './config.mjs';

export function validateBidConfig(raw) {
  if (!['single-auction', 'distributed-auctions'].includes(raw.mode)) {
    throw new Error('mode는 single-auction 또는 distributed-auctions여야 합니다.');
  }
  if (!Array.isArray(raw.auctions) || raw.auctions.length === 0) {
    throw new Error('auctions에는 하나 이상의 테스트 경매가 필요합니다.');
  }
  if (raw.mode === 'single-auction' && raw.auctions.length !== 1) {
    throw new Error('single-auction 모드에는 경매 하나만 지정해야 합니다.');
  }
  if (!Array.isArray(raw.accessTokens) || raw.accessTokens.length === 0) {
    throw new Error('accessTokens에는 하나 이상의 테스트 계정 token이 필요합니다.');
  }

  return {
    mode: raw.mode,
    runId: requireString(raw.runId, 'runId'),
    baseUrl: requireUrl(raw.baseUrl, 'baseUrl', ['http:', 'https:']),
    origin: requireOrigin(raw.origin, 'origin'),
    auctions: raw.auctions.map((auction, index) => ({
      auctionId: requirePositiveInteger(auction.auctionId, `auctions[${index}].auctionId`),
      initialBidPrice: requirePositiveInteger(
        auction.initialBidPrice,
        `auctions[${index}].initialBidPrice`,
      ),
      bidIncrement: requirePositiveInteger(
        auction.bidIncrement,
        `auctions[${index}].bidIncrement`,
      ),
    })),
    accessTokens: raw.accessTokens.map((token, index) =>
      requireString(token, `accessTokens[${index}]`),
    ),
    requestsPerSecond: requirePositiveInteger(raw.requestsPerSecond, 'requestsPerSecond'),
    durationSeconds: requirePositiveInteger(raw.durationSeconds, 'durationSeconds'),
    maxInFlight: requirePositiveInteger(raw.maxInFlight ?? 100, 'maxInFlight'),
    requestTimeoutMillis: requirePositiveInteger(
      raw.requestTimeoutMillis ?? 5000,
      'requestTimeoutMillis',
    ),
    resultPath: requireString(raw.resultPath, 'resultPath'),
  };
}

export function publicBidConfig(config) {
  return {
    ...config,
    accessTokens: undefined,
    accountCount: config.accessTokens.length,
  };
}
