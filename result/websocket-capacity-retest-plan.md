# WebSocket 300 session 용량 재시험 계획

## 1. 시험 목적

기존 시험은 1,000 session부터 시작해 handshake tail latency와 socket error가 발생했다. 그러나
더 낮은 단계를 실행하지 않아 최초 실패점과 안전 운용 한계를 찾지는 못했다.

이번 시험은 단일 t3.micro 인스턴스의 동시 WebSocket session을 최대 300개로 가정하고 다음
조건에서 연결 유지, 입찰 이벤트 전달과 재연결 안정성을 확인한다.

```text
300 sessions
```

세 시나리오는 300 session으로 한 번씩 실행한다. 통과하더라도 최대 용량이라고 단정하지 않고,
현재 가정한 동시 session 규모에서 기준을 충족했다고 기록한다.

## 2. 시나리오 1: 유휴 연결

입찰과 업무 이벤트 없이 WebSocket, STOMP subscription과 heartbeat만 유지한다.

```text
60초 ramp-up
→ 120초 유지
→ 연결 종료
→ 60초 자원 회수 확인
```

WebSocket open과 STOMP `CONNECTED`가 각각 99.9% 이상이고, 연결 p95가 5초 미만이며 connection
error가 없어야 통과한다. 서버에서는 현재 session, CPU, heap, GC, file descriptor, Tomcat
connection과 CPU credit을 확인한다.

## 3. 시나리오 2: 실제 입찰 E2E

WebSocket observer를 목표 session까지 연결한 상태에서 한 명의 bidder만 실제 입찰 API를
호출한다.

```text
GET /api/auctions/346
→ nextMinBid와 경매 상태 확인
→ 테스트 계정 로그인
→ 60초 observer ramp-up
→ 120초 동안 입찰 응답 완료 후 1초 대기하며 반복
→ 60초 자원 회수 확인
```

- 첫 입찰가: 2,800,000원과 현재 `nextMinBid` 중 큰 값
- 증가액: 5,000원
- 입찰 주기: 1초
- bidder: 1 VU
- 경매의 최소 증가액이 5,000원보다 크면 시험을 중단한다.

입찰 성공률 100%, 연결 성공률 99.9% 이상, 전달 p95 500ms·p99 1초 이하, 중복과 역순 0건을
통과 기준으로 사용한다. 성공 입찰, Redis publish·receive, Broker publish와 client 수신량을 같은
시간축에서 비교한다.

## 4. 시나리오 3: 동시 재연결

기존 지수 backoff+jitter 적용 결과는 보존한다. 이번 baseline은 목표 session을 연결한 뒤 공통
시각에 socket을 닫고 대기 없이 즉시 세 번 재연결한다.

```text
최초 연결
→ 공통 시각에 종료
→ 대기 없이 즉시 재연결
→ 20초 유지
→ 대기 없이 즉시 재연결
→ 20초 유지
→ 대기 없이 즉시 재연결
→ 20초 유지
```

재연결과 STOMP 재구독 성공률 99.9% 이상, handshake p95 5초 이하, connection error 0건을
통과 기준으로 사용한다. endpoint 자체를 중단하는 장애 복구 시험은 실제 사용자에게 영향을 줄
수 있어 이번 범위에서 제외한다.

## 5. 결과와 중단 기준

시나리오별 결과는 별도 JSON으로 저장한다.

```text
idle-{sessions}.json
bid-e2e-{sessions}.json
reconnect-before-{sessions}.json
```

다음 조건에서는 실행을 중단한다.

- k6 threshold 위반
- 서버 health check 실패
- CPU 90% 이상이 1분간 지속
- 목표 session까지 증가하지 않음
- transport error 또는 send-limit 종료가 지속 증가
- 입찰 업무 오류로 이벤트 생성 전제가 깨짐

최종 결과 문서는 문제 상황, 가설, 실험 조건, 시나리오별 결과, 가설 비교, 원인 분석, 개선사항과
그래프 순서로 작성한다. k6 결과만으로 병목 위치를 단정하지 않고 같은 시간대의 Datadog과
CloudWatch 지표를 함께 사용한다.
