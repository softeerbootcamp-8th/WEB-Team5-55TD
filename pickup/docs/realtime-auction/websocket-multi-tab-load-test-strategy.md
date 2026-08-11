# 멀티탭 WebSocket 용량 테스트 전략

## 1. 해결하려는 문제

현재 브라우저는 탭마다 WebSocket session과 STOMP subscription을 만든다. 따라서 사용자가 탭을
여러 개 열면 서버가 관리하는 연결과 subscription도 함께 증가한다.

```text
사용자 1명 · 탭 1개 = session 1개
사용자 1명 · 탭 3개 = session 3개
```

하지만 연결 수가 늘어난다는 사실만으로 WebSocket이 병목이라고 결론 내릴 수는 없다. 실제 장애가
발생하는 지점을 구분하기 위해 다음 세 경계를 독립적으로 측정한다.

1. 이벤트가 없어도 많은 session을 유지할 수 있는가?
2. 실제 입찰이 DB·Redis·Simple Broker를 거쳐 여러 session에 전달되는가?
3. 많은 연결이 동시에 재접속할 때 handshake와 STOMP 재구독이 처리되는가?

결과는 사용자 수가 아니라 WebSocket session 수로 먼저 기록한다. 다중탭 사용자 수는 실제 탭
분포를 알기 전까지 참고값으로만 환산한다.

## 2. 공통 시험 조건

- 단일 t3.micro 애플리케이션 인스턴스
- 실제 CloudFront → WebSocket endpoint 경로
- k6 부하 생성기에서 실행
- 목표 단계: `100 → 250 → 500 → 750 → 1,000`
- 한 단계라도 threshold를 위반하면 해당 시나리오의 상위 단계 중단
- 각 단계는 60초 ramp-up과 120초 유지
- 단계 종료 후 workflow가 60초 대기해 연결 정리를 유도

서버 자원은 k6 결과와 별도로 같은 시간대의 Datadog·CloudWatch에서 수집한다. CPU·CPU credit,
heap·GC·Tomcat connection은 Datadog과 CloudWatch로 확인하고, file descriptor와 실제 socket 수는
Datadog process 지표가 제공되는지 확인한 뒤 없으면 SSM 또는 `/proc` snapshot으로 보완한다.

## 3. 시나리오 1: 유휴 WebSocket 연결

### 목적과 가설

입찰과 REST 요청 없이 WebSocket, STOMP subscription, heartbeat만 유지한다. 연결 수가 증가하면
handshake tail latency와 서버 자원 점유가 늘어날 것이라고 가정한다. 공유 WebSocket을 직접
구현하지 않으므로, 공유 구조의 절감량이 아니라 현재 탭별 구조의 비용과 사용자 영향까지 측정한다.

### 실행 방법

```text
60초 동안 0 → 목표 VU
→ 120초 유지
→ 각 연결에서 STOMP CONNECTED 확인
→ 10초 heartbeat 전송
```

측정값은 `ws_open_success`, `stomp_connected`, `ws_connect_failures`, `ws_errors`,
`ws_handshake_latency`다. `ws.connect()`가 오래 유지되므로 실행 종료 시 iteration이 정상 완료되지
않을 수 있다. 따라서 open·CONNECTED counter를 연결 성공의 기준으로 사용한다.

### 합격 기준

- open과 STOMP CONNECTED 각각 99.9% 이상
- connection error 0건
- handshake p95 5초 미만

## 4. 시나리오 2: 실제 입찰부터 WebSocket 수신까지

### 목적과 가설

DB 동시성 문제와 WebSocket 전달 병목을 분리하기 위해 bidder는 1명만 사용한다. 입찰 자체가
성공한다는 전제하에 관찰자 session 수를 늘려 DB → AFTER_COMMIT → Redis → Simple Broker →
WebSocket 경로의 지연과 순서를 측정한다.

### 실행 방법

```text
GET /api/auctions/312
→ 테스트 계정 로그인
→ observer 60초 ramp-up
→ bidder 1명, 1초마다 입찰
→ 첫 가격 2,800,000원
→ 매 입찰 5,000원 증가
```

입찰자 수를 1명으로 제한한 이유는 여러 bidder가 같은 가격을 보내 발생하는 OUTBID_EXISTS를
WebSocket 장애로 오해하지 않기 위해서다.

측정값은 다음처럼 분리한다.

- 입찰: `bid_success`, `bid_failures`, HTTP status
- 연결: `ws_open_success`, `stomp_connected`, `ws_connect_failures`
- 전달: `ws_events_received`, `ws_delivery_latency`
- 정합성: `ws_duplicate_events`, `ws_order_errors`

### 순서와 유실 해석

`eventId`는 같은 사건의 중복 여부를 확인하는 값이다. UUID의 크기로 순서를 비교하지 않는다.
`bidId`는 같은 경매에서 더 최신인 입찰을 판단하는 값이다. 따라서 낮은 `bidId`가 늦게 도착해도
화면을 과거 상태로 되돌리지 않는 데 사용할 수 있지만, 번호의 빈칸만으로 유실을 판정할 수는
없다.

이번 측정에서 누락률을 계산하려면 publisher가 기대 이벤트 목록을 함께 남겨야 한다. 수신한
이벤트 수만으로는 마지막 이벤트 유실을 발견할 수 없다. 이 복구는 polling이 담당하고,
stateVersion을 도입할 경우 중간 gap 감지 신호를 추가할 수 있다.

### 합격 기준

- 입찰 실패 0건
- open·STOMP 연결 99.9% 이상
- 전달 p95 500ms, p99 1초 이하
- 중복·역순 0건

## 5. 시나리오 3: 동시 재연결

### 목적과 가설

많은 탭이 동시에 끊기면 재연결 handshake가 한 시점에 몰린다. 기존에는 지수 backoff와 요청별
jitter를 적용한 결과를 보존했고, 이번 기준선은 해당 지연 없이 즉시 재연결해 두 방식을 비교한다.

### 실행 방법

```text
1,000 VU 최초 연결
→ 60초 후 공통 시각에 socket 종료
→ 대기 없이 즉시 재연결
→ 20초 유지
→ 대기 없이 즉시 재연결
→ 20초 유지
→ 대기 없이 즉시 재연결
→ 20초 유지
```

현재 k6 시험은 healthy endpoint에서 client socket을 닫는 reconnect storm이다. 기존 backoff+jitter
결과와 이번 즉시 재연결 baseline을 비교하며, 서버 장애·인스턴스 failover·재연결 뒤 REST snapshot
복구까지는 별도 장애 복구 시험으로 분리한다.

측정값은 `initial_open_success`, `reconnect_attempts`, `reconnect_success`, `reconnect_failures`,
`stomp_connected`, `reconnect_handshake_latency`, `ws_errors`다.

### 합격 기준

- 최초 연결 99.9% 이상
- 재연결·STOMP 연결 99.9% 이상
- 재연결 실패와 socket error 0건
- handshake p95 5초 미만

## 6. 결과를 읽는 규칙

```text
목표 VU ≠ 동시 session 수 ≠ 누적 session 생성 시도 수
```

특히 `ramping-vus` 함수가 socket 종료 후 다시 실행되면 `ws_open_success`와 `ws_sessions`는
누적 시도 수가 된다. 이 값을 곧바로 “동시에 유지된 session 수”로 해석하지 않는다.

또한 k6 threshold 실패는 서버의 단일 원인을 뜻하지 않는다. 다음 원인을 분리해 확인해야 한다.

- CloudFront·proxy handshake 처리량
- t3.micro CPU credit과 애플리케이션 CPU
- Tomcat WebSocket session·subscription 메모리
- Simple Broker fan-out과 outbound executor
- 느린 socket의 send buffer
- GitHub Actions 부하 생성기 자체의 CPU·event loop

## 7. 다음 단계

1. idle·bid 시나리오를 VU당 고정 session 하나로 유지해 동시 session과 누적 시도를 분리한다.
2. open, CONNECTED, SUBSCRIBE, close를 각각 기록한다.
3. 기대 이벤트 집합을 만들어 누락·중복·역순을 독립적으로 계산한다.
4. Redis receive, broker publish, client 수신을 실행 ID와 시간축으로 연결한다.
5. Datadog·CloudWatch CPU, heap, GC, FD, Tomcat connection, executor queue를 함께 수집한다.
6. 보완된 1,000 단계가 통과할 때만 2,000 이상으로 확대한다.
7. 서버 장애를 실제로 발생시키는 failover와 재연결 후 snapshot 복구 시험을 별도로 수행한다.
