# 입찰 동시성 제어 4방식 부하테스트 실행 계획

> 이 문서는 다른 AI 에이전트(또는 사람)에게 실행을 위임하기 위한 자기완결적 실행 계획이다.
> 배경 설명 없이 이 문서만 보고 처음부터 끝까지 실행할 수 있도록 작성했다.

## 0. 배경과 목표

브랜치 `OOTD-292/experiment/bid-concurrency-comparison`에는 같은 "입찰(placeBid)" 기능을
서로 다른 동시성 제어 전략으로 구현한 4개의 REST 엔드포인트가 공존한다. 이 4개를 t3.micro
인스턴스 하나에 배포한 뒤, **경매 하나에 수백 건의 동시 입찰이 몰리는 상황**을 각 방식별로
재현해 Datadog 지표로 트레이드오프를 정량적으로 비교하는 것이 목표다.

비교 결과로 답해야 할 질문:
- 동일한 동시 요청 수에서 어느 방식이 지연시간(p95/p99)이 가장 낮은가?
- 어느 방식이 DB 커넥션 풀/락 대기로 가장 먼저 무너지는가(처리량 한계)?
- "짧은 트랜잭션 + 비동기 Bid 기록" 방식이 응답 지연을 줄이는 대신 치른 대가(최종 정합성 지연,
  유실 위험)는 실측으로 얼마나 되는가?

## 1. 비교 대상 4가지 방식

| 코드명 | 엔드포인트 | 이슈 | 핵심 메커니즘 |
| --- | --- | --- | --- |
| A. 분산 락 | `POST /auctions/{id}/bids/distributed-lock` | OOTD-278 | Redisson 분산 락으로 같은 경매에 대한 요청을 완전히 직렬화(`waitTime=3s`, `leaseTime=5s`). 락 획득 실패 시 409(`BID_LOCK_ACQUISITION_FAILED`). |
| B. 조건부 UPDATE | `POST /auctions/{id}/bids/conditional-update` | OOTD-279 | 락 없이 `WHERE current_price < :price` 조건부 UPDATE의 영향 row 수로 승패 판정. InnoDB row lock에 의존. 캐시 없음. |
| C. 조건부 UPDATE + Redis 캐시 | `POST /auctions/{id}/bids` (기본) | OOTD-292 초안 | B에 Redis 현재가 캐시 사전검사를 추가했지만, **Redis 호출이 `@Transactional` 메서드 안(=DB 커넥션을 쥔 채)에서 일어난다.** |
| D. 짧은 트랜잭션 | `POST /auctions/{id}/bids/short-transaction` | OOTD-292 개선 | Redis 사전검사·경매 검증을 트랜잭션 밖(`Propagation.NOT_SUPPORTED`)에서 수행하고, DB 트랜잭션은 `updateCurrentPriceIfHigher` 한 줄로 최소화. Bid row 기록(추월 처리+INSERT)은 응답 이후 완전히 비동기(`AsyncBidRecorder`, 단일 스레드, 큐 500)로 수행. 응답은 202 + `PlaceBidAcceptedResponse`(bidId 없음). |

가설(검증 대상, 사실로 단정하지 말 것):
- A는 정합성이 가장 확실하지만 완전 직렬화라 처리량이 가장 낮고, 지연시간이 동시성 수준에
  선형으로 비례할 것이다. 락 대기 3초를 넘기면 409가 급증할 것이다.
- B는 A보다 처리량이 높을 것이나, 트랜잭션 전체(조회+검증+UPDATE+INSERT)가 auction row
  락과 DB 커넥션을 계속 쥐고 있어 커넥션 풀 대기시간이 C/D보다 길 것이다.
- C는 Redis 사전검사로 "확실히 지는 입찰"을 걸러내려는 의도지만, 그 Redis 호출 자체가
  DB 커넥션을 쥔 채 일어나 Redis 지연/장애 시 오히려 커넥션 풀을 더 오래 붙잡을 수 있다.
- D는 락 보유 시간이 가장 짧아 처리량/지연시간이 가장 좋을 것으로 기대되지만, Bid 기록이
  비동기이므로 응답 성공(202) 시점과 실제 Bid row 생성 시점 사이에 지연(eventual consistency
  lag)이 있고, 비동기 큐가 밀리면 최악의 경우 기록이 유실될 수 있다(§7에서 반드시 검증).

## 2. 통제 변수 (4방식 모두 동일하게 고정)

아래 값이 방식마다 다르면 "동시성 제어 전략의 차이"가 아니라 "환경 차이"를 측정하게 된다.
테스트 시작 전 반드시 확인하고, 최종 보고서에도 기록할 것.

- HikariCP `maximum-pool-size` (기본 `application-prod.yml` 기준 10), `connection-timeout`(5000ms)
- JVM 힙 크기 (`-Xmx`) — t3.micro는 RAM 1GB뿐이므로 명시적으로 설정하고 고정할 것
- `bidRecordingExecutor`(D 방식 전용) 스레드 수(1)·큐 용량(500) — 코드 수정 없이 그대로 사용
- Redis(캐시), MySQL 배치 위치와 스펙 — 부하 생성기(k6)는 **반드시 t3.micro 외부의 별도 머신**에서
  실행한다. 같은 인스턴스에서 부하 생성기를 돌리면 CPU를 나눠 써서 측정치가 왜곡된다.
- 시드 데이터(경매 4개)의 `starting_price`/`bid_increment`/`reserve_price`는 §3.2에서 동일하게 세팅

## 3. 사전 준비

### 3.1 배포

1. `OOTD-292/experiment/bid-concurrency-comparison` 브랜치를 t3.micro에 배포한다(빌드/배포
   방식은 기존 CI/CD 파이프라인을 그대로 사용).
2. Datadog Java 에이전트(`dd-java-agent.jar`)가 이미 붙어 있는지 확인한다
   (`application.yml`의 HikariCP `register-mbeans: true` 주석이 JMXFetch 전제를 명시하고 있다).
   `DD_SERVICE`, `DD_ENV`, `DD_AGENT_HOST`, `DD_VERSION` 등 필수 env가 설정돼 있는지 확인.
3. **JWT 액세스 토큰 TTL을 늘린다.** 기본값(`JWT_ACCESS_TOKEN_TTL=15m`)으로 두면 15분 넘게
   걸리는 테스트 라운드 중간에 로그인 토큰이 만료돼 401이 섞여 들어와 결과가 오염된다.
   테스트 인스턴스에서만 `JWT_ACCESS_TOKEN_TTL=2h` 정도로 늘려서 배포할 것.
4. 배포 후 헬스체크로 4개 엔드포인트가 모두 응답하는지 최소 1건씩 수동 확인한다.

### 3.2 시드 데이터

이 저장소의 `load-test/seed.sql`, `load-test/reset.sql`을 사용한다.

1. 셀러 계정을 API로 만든다(입찰자는 k6 스크립트가 스스로 만들므로 셀러만 미리 필요):
   ```bash
   curl -X POST $BASE_URL/members -H 'Content-Type: application/json' \
     -d '{"loginId":"loadtest_seller","nickname":"부하테스트셀러","password":"password123"}'
   ```
   응답의 `memberId`를 기록해 둔다.
2. `seed.sql`의 `SET @seller_member_id = 0;`을 위 memberId로 바꾸고, 배포된 MySQL에 실행한다.
   방식(A/B/C/D)별로 독립된 카드/상품/경매를 하나씩 만든다 — 한 경매를 공유하면 한 방식의
   테스트가 남긴 `currentPrice`가 다음 방식의 시작 조건을 오염시키기 때문이다.
3. 실행 결과로 나오는 4개의 `auction_id`(A/B/C/D)를 기록해 둔다. 이후 모든 라운드에서
   재사용하며, 라운드 사이에는 새로 만들지 않고 `reset.sql`로 초기화한다.
4. 각 라운드(같은 방식을 여러 번 반복 실행할 때) 시작 전 반드시:
   - `reset.sql`로 해당 `auction_id`의 `current_price`를 `starting_price`로, `bid` 테이블을 비운다.
   - C·D 방식은 Redis 캐시도 지운다: `redis-cli DEL auction:current-price:<C의 auction_id> auction:current-price:<D의 auction_id>`
     (캐시가 남아 있으면 리셋된 DB 값보다 오래된 가격을 기준으로 사전검사가 이루어져 결과가 왜곡된다.)

### 3.3 Datadog 확인/보강 사항

부하테스트 시작 전에 아래 지표가 Datadog에서 실제로 보이는지 먼저 확인한다. 안 보이면
테스트를 진행해도 나중에 비교할 데이터가 없다.

- **APM 트레이스**: `service:pickup` 리소스별(엔드포인트별) 요청 수·지연시간·에러율.
  4개 엔드포인트가 서로 다른 리소스명으로 태깅되는지 확인(안 되어 있으면 커스텀 스팬 태그로
  구분 — 예: `resource_name` 태그를 컨트롤러 메서드명 기준으로).
- **HikariCP 풀 지표**(JMXFetch): `active connections`, `pending threads`(대기 스레드 수),
  `connection wait time` — pool 고갈 여부를 보는 핵심 지표. `pickup-pool`이라는 이름으로
  JMX에 등록되어 있음(`application.yml` 참고).
- **JVM 지표**: heap 사용률, GC pause time — t3.micro 1GB RAM에서 GC 압박이 결과를
  왜곡할 수 있으므로 반드시 같이 본다.
- **호스트 인프라 지표**: CPU 사용률(t3.micro는 버스트 인스턴스라 CPU 크레딧 고갈 시 성능이
  급락한다 — CloudWatch `CPUCreditBalance`도 같이 확인할 것. Datadog에 AWS 연동이 안 돼
  있으면 CloudWatch 콘솔을 별도로 열어둔다).
- **Redis 지표**(C, D 방식): 커맨드 지연시간, 커넥션 수.
- **비동기 큐 지표(D 방식 전용, 가장 중요하고 가장 빠지기 쉬움)**: `bidRecordingExecutor`의
  active/queue size/completed task count. Spring Boot Actuator + Micrometer가 켜져 있으면
  `ThreadPoolTaskExecutor` 빈은 보통 자동으로 계측된다(`executor.queue.remaining.capacity`,
  `executor.active` 게이지 등, 태그는 빈 이름 `bidRecordingExecutor`). Datadog에서 이 메트릭이
  안 보이면, 코드를 건드리지 말고 **먼저 이 문서 작성자에게 보고**한다 — 이 지표 없이는 D 방식의
  "비동기 큐가 밀리는 순간"을 잡아낼 수 없어 테스트의 핵심 목적을 달성할 수 없다.

## 4. 테스트 설계

### 4.1 요청 시나리오

`load-test/k6-bid-concurrency.js`를 사용한다. VU(가상 사용자)별 동작:

1. (setup, 1회) 입찰자 계정 N명(`BIDDER_COUNT`, 기본 50)을 자체 생성하고 로그인해 토큰 확보.
2. (매 반복) `GET /auctions/{auctionId}`로 `nextMinBid`(현재가+최소입찰단위)를 읽는다.
3. 그 값으로 대상 엔드포인트에 `POST {bidPrice: nextMinBid}`를 보낸다.
4. 응답을 3가지로 분류한다(§6에서 지표로 사용):
   - `bid_accepted` — 201/202 (성공)
   - `bid_outbid_rejected` — 409 `OUTBID_EXISTS`/`BELOW_MIN_INCREMENT` (설계상 정상적인
     낙찰 실패. 여러 VU가 같은 가격대를 동시에 노리면 대부분 이 결과가 나오는 게 **정상**이다.)
   - `bid_lock_acquisition_failed` — 409 `BID_LOCK_ACQUISITION_FAILED` (A 방식 전용)
   - `bid_system_errors` — 그 외 전부(5xx, 타임아웃, 예상 못한 4xx). **이것만 진짜 장애 신호다.**

이렇게 나누는 이유: 동시성 제어가 잘 동작할수록 "낙찰 경쟁에서 진 입찰"의 409 비율은
오히려 높아진다. 이걸 일반적인 에러율에 합쳐서 보면 "제어가 잘 될수록 에러율이 높아 보이는"
착시가 생긴다.

### 4.2 2단계 테스트 매트릭스

**1단계 — 고정 부하 비교 (`SCENARIO=fixed_load`)**: 4방식에 동일하게 VU 200명, 3분 유지.
목적은 "완전히 같은 조건"에서 지연시간 분포와 인프라 지표(§6)를 나란히 비교하는 것.
200이라는 숫자는 t3.micro 스펙(2 vCPU, 1GB RAM) 대비 상당히 높은 동시성이므로, 만약
1단계 도중 특정 방식이 과도한 타임아웃/5xx로 무너진다면(즉 `bid_system_errors`가 급증하면)
VU를 50/100 등으로 낮춰 재시도하고 그 사실 자체를 결과에 기록한다(→ 그 방식의 한계가
200 미만이라는 뜻이므로 그것도 유의미한 결론이다).

**2단계 — 한계치 탐색 (`SCENARIO=ramp_to_break`)**: 방식별로 VU를 20→50→100→200→400까지
30초 단위로 램프업하며, `bid_system_errors`가 눈에 띄게 증가하기 시작하는 지점(=처리량 한계)을
찾는다. 4방식의 한계치를 나란히 비교하는 것이 이 단계의 목적.

### 4.3 실행 순서·격리 원칙

- 4방식을 **절대 동시에** 테스트하지 않는다. 순서대로 하나씩: 리셋 → 1단계 실행 → 결과 기록 →
  리셋 → 2단계 실행 → 결과 기록 → 다음 방식.
- 각 라운드 사이에는 최소 1분의 쿨다운을 둔다(이전 라운드의 비동기 큐/GC/커넥션 풀이
  완전히 안정화된 상태에서 다음 라운드를 시작해야 한다 — 특히 D 방식의 비동기 큐가
  이전 라운드 요청을 아직 처리 중인 상태로 다음 라운드가 겹치면 안 된다. §7의 "큐 배수 확인"
  절차로 완전히 빈 것을 확인하고 다음 라운드를 시작한다).
- 순서 자체가 결과에 영향(예: 첫 방식이 콜드스타트로 불리)을 줄 수 있으므로, 여유가 있다면
  A→B→C→D와 D→C→B→A 두 번의 전체 사이클을 돌려 순서 효과를 상쇄시키는 것을 권장한다
  (필수는 아니고, 시간이 부족하면 1회만 진행하고 그 사실을 보고서에 명시한다).

실행 명령 예:
```bash
k6 run -e BASE_URL=http://<host>:8080 -e ENDPOINT_PATH=/bids/distributed-lock \
  -e AUCTION_ID=<A의 auction_id> -e SCENARIO=fixed_load \
  load-test/k6-bid-concurrency.js
```

## 5. 측정 지표와 Datadog 조회

라운드마다 아래 지표를 캡처한다(k6 자체 출력 + Datadog 대시보드 스크린샷 또는 쿼리 결과).

| 구분 | 지표 | 어디서 |
| --- | --- | --- |
| 클라이언트 관점 지연시간 | p50/p95/p99 `bid_duration_ms` | k6 요약 출력(`summary`) |
| 처리량 | 초당 성공 요청 수(`bid_accepted`/duration), 초당 낙찰경쟁실패 수 | k6 요약 출력 |
| 서버 관점 지연시간 | 엔드포인트별 APM p95/p99, 에러율 | Datadog APM → `service:pickup`, 엔드포인트별 리소스 필터 |
| DB 커넥션 풀 압박 | HikariCP `pending threads`(대기), `connection wait time` 최대/평균 | Datadog Metrics, `pickup-pool` JMX 지표 |
| 락 대기 | A 방식만: `bid_lock_acquisition_failed` 카운트, 락 대기시간(가능하면 커스텀 스팬으로 확인) | k6 카운터 + APM |
| 인프라 | CPU 사용률, 메모리, GC pause | Datadog Infrastructure / JVM 대시보드 |
| Redis | 커맨드 지연, 에러율 | Datadog Redis 인테그레이션 (C, D 방식) |
| 비동기 큐(D 전용) | queue size, active thread, rejected count | Datadog Metrics, `bidRecordingExecutor` |
| 진짜 장애 | `bid_system_errors` 카운트, 5xx 비율 | k6 카운터 + Datadog APM 에러율 |

## 6. 정합성(correctness) 검증 절차 — 각 라운드 종료 직후 반드시 수행

부하테스트는 "빠른가"만 보는 게 아니라 "그래도 정답이 맞는가"를 같이 봐야 한다. 특히
D 방식은 비동기라 이 검증이 없으면 성능이 가장 좋게 나와도 신뢰할 수 없는 결과가 된다.

1. **최종 현재가 검증** (4방식 공통):
   ```sql
   SELECT a.auction_id, a.current_price, MAX(b.bid_price) AS max_bid_price
   FROM auction a LEFT JOIN bid b ON b.auction_id = a.auction_id
   WHERE a.auction_id = <해당 방식의 auction_id>
   GROUP BY a.auction_id;
   ```
   `current_price`와 `max_bid_price`가 일치해야 한다(불일치 = 동시성 제어가 실제로 깨졌다는 뜻).

2. **최고 입찰 단일성 검증** (4방식 공통):
   ```sql
   SELECT COUNT(*) FROM bid WHERE auction_id = <auction_id> AND bid_status = 'HIGHEST';
   ```
   반드시 1이어야 한다. 0이거나 2 이상이면 버그.

3. **응답-기록 수 일치 검증** (D 방식 전용, 가장 중요):
   - k6가 집계한 `bid_accepted` 카운트(=202 응답 수)와,
     `SELECT COUNT(*) FROM bid WHERE auction_id = <D의 auction_id>`의 결과를 비교한다.
   - 테스트 종료 직후 큐가 아직 처리 중일 수 있으므로, **비동기 큐 게이지(active=0,
     queue size=0)가 될 때까지 기다린 뒤** 최종 COUNT를 비교한다.
   - 두 수가 다르면 비동기 Bid 기록이 유실됐다는 뜻이다 — 이는 버그가 아니라 이 방식이
     원래 감수하기로 한 트레이드오프이므로, "몇 건이 유실됐는지"를 정량 결과로 보고서에
     그대로 남긴다(숨기지 말 것).
   - 추가로 "202 응답 시각"과 "해당 Bid row의 `created_at`" 차이(eventual consistency lag)를
     몇 개 샘플로 측정해 D 방식의 "응답은 빠르지만 실제 반영은 얼마나 늦는가"를 수치로 남긴다.

## 7. 결과 정리 템플릿

방식별로 아래 표를 채워 최종 비교표를 만든다.

| 지표 | A. 분산 락 | B. 조건부 UPDATE | C. +Redis 캐시 | D. 짧은 트랜잭션 |
| --- | --- | --- | --- | --- |
| p50/p95/p99 지연(ms, fixed_load) | | | | |
| 처리량(성공/초, fixed_load) | | | | |
| 한계 동시성(ramp_to_break에서 오류 급증 시점) | | | | |
| Hikari pending threads 최대값 | | | | |
| CPU 사용률 최대값 | | | | |
| 낙찰경쟁실패(409) 비율 | | | | |
| 진짜 장애(bid_system_errors) 수 | | | | |
| 정합성: current_price == max(bid_price) | | | | |
| 정합성: HIGHEST 상태 Bid 개수 | | | | |
| (D만) 응답수 vs Bid row 수 불일치 건수 | - | - | - | |
| (D만) eventual consistency lag 평균 | - | - | - | |

마지막에 3~5문장으로 "결론"을 적는다: 어느 방식이 어떤 조건(동시성 수준)에서 우세한지,
D 방식이 얻은 지연시간 개선이 정합성 지연/유실 위험 대비 감수할 만한 트레이드오프인지에
대한 판단을 데이터로 뒷받침해서 서술한다.

## 8. 주의사항

- t3.micro는 버스트 인스턴스다. 테스트를 오래(수십 분) 끌면 CPU 크레딧이 고갈되어 뒤에
  실행한 방식이 부당하게 불리해진다. 4방식 전체를 가능하면 크레딧 고갈 없이 끝낼 수 있는
  시간 내에 마치거나, 크레딧 잔량을 라운드마다 확인해 리포트에 남긴다.
- k6(부하 생성기)는 절대 t3.micro 위에서 실행하지 않는다. 별도 머신(또는 더 큰 EC2)에서 실행.
- MySQL/Redis가 t3.micro와 같은 인스턴스에 있는지(`docker-compose.yml` 참고) 먼저 확인하고,
  같이 있다면 "DB/Redis 자체의 리소스 경쟁"과 "동시성 제어 전략의 차이"가 뒤섞여 보일 수
  있다는 점을 결과 해석 시 감안한다.
- 이 브랜치는 병합 대상이 아니라 실험용이다. 부하테스트로 어떤 방식을 선택할지 결론을 낸
  뒤, 실제 반영은 별도의 정식 브랜치/PR로 진행해야 한다.
