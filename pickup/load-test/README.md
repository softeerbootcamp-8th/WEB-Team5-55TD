# Pickup WebSocket load test

실시간 경매의 유휴 WebSocket session, Redis 이후 fan-out과 실제 입찰 처리량을 서로 분리해
측정하는 도구다. 자세한 시험 순서와 판정 기준은
[`websocket-multi-tab-load-test-strategy.md`](../docs/realtime-auction/websocket-multi-tab-load-test-strategy.md)를
따른다.

## 준비

```bash
pnpm install
pnpm test
```

`config/*.example.json`을 `config/*.local.json`으로 복사하고 시험 환경 값으로 바꾼다. 실제 token과
결과 파일은 Git에 포함되지 않는다.

## 실행

```bash
pnpm idle --config config/idle.local.json --confirm-load-test
pnpm fanout --config config/fanout.local.json --confirm-load-test
pnpm bid --config config/bid.local.json --confirm-load-test
```

설정만 검증할 때는 network 요청을 만들지 않는 `--dry-run`을 사용한다.

```bash
pnpm fanout --config config/fanout.local.json --dry-run
```

## 모드

- `idle`: STOMP 연결과 구독, heartbeat만 유지한다.
- `fanout`: 연결 안정화 후 시험 Redis에 `AUCTION_BID_UPDATED`를 직접 발행한다.
- `bid`: 실제 입찰 API의 성공률과 응답 지연을 기록한다.

`fanout`은 운영 Redis에서 실행하면 안 된다. 직접 발행한 이벤트는 DB에 존재하지 않는 synthetic
snapshot이며 시험 전용 경매 topic과 Redis만 사용해야 한다.

## shard

여러 process나 host가 같은 `runId`, `shardCount`, `connections`, `auctionIds`,
`startAtEpochMillis`를 사용한다. `shardIndex`만 다르게 지정하고 `publishEvents`는 한 shard에서만
true로 둔다.

```bash
pnpm merge results/shard-0.json results/shard-1.json \
  --output results/fanout-merged.json
```

모든 load generator의 시각을 동기화해야 latency와 공통 시작 시각을 신뢰할 수 있다.
