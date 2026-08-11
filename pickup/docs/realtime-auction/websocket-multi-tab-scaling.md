# 단일 인스턴스 WebSocket 부하 측정

## 1. 측정 목적

현재 경매 화면은 탭마다 WebSocket session을 하나 만든다. 사용자가 여러 탭을 열거나 동시에
많은 사용자가 접속하면 서버가 유지해야 하는 socket, file descriptor, STOMP session,
subscription과 heartbeat 작업이 증가한다.

이번 시험은 이벤트 fan-out만 따로 발생시키는 것이 아니라, 실제 입찰 요청이 WebSocket 이벤트로
전달되는 전체 경로를 포함해 **단일 t3.micro 인스턴스가 현재 WebSocket 구조를 어디까지 감당하는지**
확인한다.

확인할 질문은 다음과 같다.

1. 이벤트가 없어도 WebSocket session을 몇 개까지 안정적으로 유지하는가?
2. 실제 입찰 이벤트가 발생할 때 WebSocket 수신률·지연·순서를 보장하는가?
3. 많은 연결이 한꺼번에 끊겼을 때 재연결 handshake와 재구독을 감당하는가?

Redis synthetic publish, 실제 입찰 동시성 자체를 측정하는 별도 시험, 다중 인스턴스 분산과
Datadog API 자동 조회는 이번 범위에서 제외한다.

## 2. 공통 환경과 단계

- t3.micro 애플리케이션 인스턴스 1대
- 실제 reverse proxy를 거치는 `/ws`와 입찰 API 경로
- 운영과 같은 JVM 옵션, Origin allowlist, STOMP heartbeat 10초
- 애플리케이션 외부에서 실행하는 k6 부하 생성기
- 테스트 경매 346
- 입찰자용 테스트 계정 1개

WebSocket VU는 300으로 고정한다.

```text
300 VU
```

각 시나리오는 다음 순서로 실행한다.

```text
60초 ramp-up
→ 120초 유지
→ 종료 또는 장애 주입
→ 60초 자원 회수 대기
```

각 시나리오가 합격 기준을 충족하는지 독립적으로 기록한다. 300 VU를 통과해도 최대 용량이 아니라
현재 가정한 동시 session 규모에서 안정적인 것으로 해석한다.

## 3. 공통 측정 지표

### 서버 지표

- WebSocket handshake 성공·실패 수
- 현재 WebSocket session 수
- STOMP connect·disconnect 수
- subscription 수
- process CPU와 JVM heap
- GC pause와 old generation 사용량
- Tomcat connection과 file descriptor
- heartbeat 지연·실패
- transport error와 send-limit 종료
- network throughput
- `CPUCreditUsage`, `CPUCreditBalance`
- 입찰 HTTP 요청 수·응답시간·상태 코드
- Redis publish·receive와 broker publish counter

### k6 지표

- VU별 연결 성공률
- 연결 유지 시간
- 입찰 API 성공률과 응답시간
- 실제 입찰 event 수신률
- 입찰 완료부터 WebSocket 수신까지의 지연
- eventId·bidId 누락·중복·역순 수
- 재연결 attempt·성공률·재구독 성공률
- 부하 생성기 CPU·RSS와 event-loop 지연

부하 생성기가 먼저 CPU 포화되면 서버 한계로 기록하지 않는다. k6 VU를 여러 runner로 나눠 같은
단계를 다시 실행한다.

## 4. 시나리오 1: 유휴 WebSocket 연결 수용량

### 왜 측정하는가

입찰 이벤트가 없어도 WebSocket은 TCP socket과 file descriptor를 점유하고, Spring STOMP
session·subscription과 heartbeat 상태를 유지한다. NIO를 사용해도 이 자원 비용은 사라지지 않는다.

이 단계는 이벤트와 DB 요청을 제거하고 “연결을 유지하는 것 자체”의 한계를 측정한다. 이 기준선이
있어야 실제 입찰 단계에서 발생한 CPU·heap 증가가 WebSocket 연결 때문인지 이벤트 처리 때문인지
구분할 수 있다.

### 조건과 실행

- 모든 VU가 같은 경매 topic 하나를 구독
- Redis 이벤트와 입찰 API 요청 없음
- STOMP heartbeat만 유지
- 300 session을 120초 유지
- 종료 후 session과 subscription 회수 확인

### 결과 기록

| VU | 연결 성공률 | heartbeat 오류 | CPU p95 | heap 증가량 | FD 증가량 | 종료 후 회수 | 판정 |
| ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 300 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 미측정 |

### 결과로 얻는 것

- 이벤트가 없어도 안정적으로 유지할 수 있는 최대 WebSocket VU
- CPU·heap·FD·heartbeat 중 가장 먼저 악화되는 자원
- 정상·강제 종료 후 session과 subscription 회수 여부
- 실제 접속자 수를 WebSocket session 수로 환산할 기준

## 5. 시나리오 2: 실제 입찰부터 WebSocket 수신까지

### 왜 측정하는가

Redis에 synthetic 이벤트를 직접 넣으면 실제 사용자 요청이 빠진다. 실제 경매 사용자는 입찰 API를
호출하고, 서버는 transaction commit 이후 이벤트를 Redis와 WebSocket으로 전달한다. 이 시나리오는
그 전체 경로가 현재 WebSocket 연결 수에서 정상적으로 동작하는지 확인한다.

```text
입찰 VU
  → 입찰 API
  → DB transaction·lock
  → AFTER_COMMIT
  → Redis publish
  → Redis subscriber
  → Simple Broker
  → WebSocket 구독 VU
```

### 입찰자를 1명으로 정한 이유

입찰자는 한 명으로 고정하고 경매 346에 순차적으로 입찰한다.

```text
입찰자 1명 → 경매 346
→ 응답 완료 후 1초 대기
→ 첫 입찰가는 1,500,000원과 현재 nextMinBid 중 큰 값
→ 다음 입찰가를 43,000원 증가
```

입찰자를 한 명으로 제한하면 여러 bidder의 DB lock 경합과 동일 가격 충돌이 WebSocket 결과에
섞이는 것을 줄일 수 있다. 현재 방식은 응답 완료 후 1초를 기다리는 closed-loop이므로 고정 1 RPS가
아니다. 결과에는 실제 달성 요청률을 함께 기록한다.

### 구독자 수

입찰자 수와 이벤트율은 고정하고 WebSocket 구독자만 증가시킨다.

| 단계 | 입찰자 | 실제 이벤트율 | WebSocket 구독자 |
| ---: | ---: | ---: | ---: |
| 1 | 1명 | 응답시간에 따라 결정 | 300명 |

이렇게 해야 구독자 수가 증가할 때 WebSocket 처리 비용과 전달 지연이 어떻게 변하는지 비교할 수
있다. 실제 입찰자 수와 이벤트율을 함께 변경하지 않는 이유는 원인 변수를 하나로 제한하기 위해서다.

### 측정 절차

1. 구독 VU 300개를 연결하고 모든 subscription이 완료됐는지 확인한다.
2. 입찰자 한 명이 경매 346에 입찰하고 응답 완료 후 1초를 기다린다.
3. 입찰 API 응답과 서버의 accepted bid를 기록한다.
4. 각 WebSocket VU가 받은 이벤트의 `eventId`, `bidId`, 수신 시각을 기록한다.
5. 120초 동안 전달률·지연·순서 지표를 집계한다.
6. 입찰을 멈추고 WebSocket 연결을 정상 종료한다.

### 순서 보장 측정

각 WebSocket VU별로 수신 순서를 따로 계산한다. 전체 VU의 이벤트를 하나의 전역 순서로 합치지
않는다. TCP는 연결별 frame 순서만 보장하고, 여러 executor·인스턴스·네트워크 경로 사이의 DB
commit 순서까지 보장하지 않기 때문이다.

기록할 값은 다음과 같다.

- accepted bid 수 대비 WebSocket event 수신률
- VU별 `eventId` 중복 수
- VU별 `bidId` 누락 수
- VU별 직전 `bidId`보다 작은 값의 역순 수신 수
- 같은 `bidId`에 서로 다른 `eventId`가 연결된 수
- 입찰 accepted 시각부터 WebSocket frame 수신까지의 p95·p99

예상 결과 형식:

```text
accepted bid: 1001 → 1002 → 1003
subscriber A: 1001 → 1002 → 1003
subscriber B: 1001 → 1003 → 1002  (역순 관찰)
```

역순 frame이 관찰되더라도 실제 화면 client가 `bidId` 비교로 과거 상태를 적용하지 않는지는 별도
browser 검증값으로 기록한다. 이 시험은 DB commit부터 모든 브라우저까지 전역 순서를 증명하는
시험이 아니다.

### 결과 기록

| 구독자 VU | 입찰 성공률 | 이벤트 수신률 | 전달 p95/p99 | 누락 | 중복 | 역순 | 판정 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 300 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 실측 전 | 미측정 |

### 결과로 얻는 것

- 실제 사용자 요청이 WebSocket까지 도달하는 end-to-end 성공률
- 구독자 증가에 따른 이벤트 전달 지연과 수신률 변화
- 입찰 API·DB 병목과 WebSocket 전달 병목을 구분할 근거
- 연결별 이벤트 순서·누락·중복·역순 수신 현황
- 현재 인스턴스에서 안정적으로 지원할 수 있는 구독자 수 후보

## 6. 시나리오 3: 재연결 폭주

### 왜 측정하는가

서버 재시작이나 네트워크 장애로 많은 WebSocket이 동시에 끊기면 handshake·STOMP CONNECT·
SUBSCRIBE가 짧은 시간에 집중된다. 이번 시험은 backoff 없이 즉시 재연결하는 기준선을 측정하고,
기존 지수 backoff와 jitter 적용 결과와 비교한다.

이번 시험은 서버를 재시작하지 않고 k6 VU의 socket을 강제로 닫아 재연결 경로를 측정한다. 실제
EC2 재시작이나 네트워크 차단은 별도 장애 승인 없이는 실행하지 않는다.

### 조건과 실행

- 시나리오 1에서 통과한 최대 VU 단계 사용
- 모든 socket을 짧은 시간 안에 강제 종료
- 대기 없이 즉시 세 번 재연결
- 각 재연결 session을 20초 유지
- 재연결 시도·STOMP 재구독 기록

### 결과 기록

| 항목 | 결과 |
| --- | --- |
| 최대 1초 handshake 수 | 실측 전 |
| 재연결 성공률 | 실측 전 |
| 재구독 성공률 | 실측 전 |
| backoff·jitter 범위 준수 | 실측 전 |
| 재연결 중 CPU·heap p95 | 실측 전 |
| 복구 후 session 수 | 실측 전 |

### 결과로 얻는 것

- 재연결 시도가 특정 순간에 몰리는지
- backoff와 jitter가 handshake peak를 줄이는지
- 단일 인스턴스가 장애 직후 재연결을 어디까지 처리하는지
- 재연결 후 session이 누락되거나 남는지

## 7. 측정 결과 요약

아직 실측 전이다. 각 시나리오 실행 후 아래 표를 채운다.

| 항목 | 결과 |
| --- | --- |
| 유휴 WebSocket 최대 안정 VU | 실측 전 |
| 실제 입찰·WebSocket 최대 안정 구독자 수 | 실측 전 |
| 최초 포화 자원 | 실측 전 |
| 실제 이벤트 전달 p99 | 실측 전 |
| 이벤트 누락·중복·역순 결과 | 실측 전 |
| 정상·강제 종료 후 자원 회수 시간 | 실측 전 |
| 재연결 handshake 최대 처리량 | 실측 전 |
| 재연결 성공률 | 실측 전 |

## 8. 범위 밖의 측정

- Redis synthetic event 발행과 fan-out만 따로 측정하는 시험
- 실제 입찰자 수를 크게 늘리는 DB 동시성 시험
- 다중 인스턴스 분산·Redis 인스턴스별 전달
- SharedWorker 효과
- Datadog API 자동 조회
