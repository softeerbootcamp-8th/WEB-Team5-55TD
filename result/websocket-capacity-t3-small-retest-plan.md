# WebSocket t3.small 단계별 용량 재시험 계획

## 1. 목적

t3.micro에서 t3.small로 서버 사양을 높인 뒤 실제 CloudFront 경로에서 WebSocket 연결 수 증가가
연결 안정성과 입찰 알림 전파 지연에 미치는 영향을 확인한다. 유휴 연결과 입찰 알림 시험은
300, 700, 1,000 session을 한 번의 계단형 시험으로 측정하고, 재연결은 700과 1,000 session만
독립적으로 측정한다.

## 2. 공통 조건

| 항목          | 조건                                   |
| ------------- | -------------------------------------- |
| 서버          | 단일 t3.small 인스턴스                 |
| 부하 발생기   | GitHub Actions Ubuntu runner의 k6      |
| API·WebSocket | 기존 CloudFront `/api`, `/api/ws` 경로 |
| Origin        | `https://pick-up.store`                |
| 대상 경매     | 진행 중인 부하테스트 전용 경매 12458   |
| 입찰자        | 테스트 계정 1명                        |
| 자원 회수     | 각 시험 종료 후 60초                   |

테스트 계정은 GitHub Actions Secret으로만 주입하며 로그와 결과 파일에 저장하지 않는다.

## 3. 6분 계단형 부하

유휴 연결과 입찰 알림 시험에 같은 VU 단계를 적용한다.

```text
0 -> 300 VU 증가       1분
300 VU 유지            1분
300 -> 700 VU 증가     1분
700 VU 유지            1분
700 -> 1,000 VU 증가   1분
1,000 VU 유지          1분
```

각 목표 VU의 유지 구간을 주된 비교 대상으로 사용하고 증가 구간은 warm-up으로 구분한다.

## 4. 시나리오 1: 유휴 연결

업무 이벤트 없이 목표 수의 WebSocket 연결, STOMP 구독과 10초 heartbeat를 유지할 수 있는지
확인한다.

통과 기준은 WebSocket open과 STOMP `CONNECTED` 99.9% 이상, handshake p95 5초 미만,
연결 실패·socket error·예기치 않은 조기 종료 0건이다.

## 5. 시나리오 2: 초당 1건 입찰 알림

관전자를 6분 계단형으로 늘리면서 한 명의 입찰자가 전체 시험 동안 매초 입찰 한 건을 시작한다.
k6 `constant-arrival-rate`를 사용하므로 이전 응답이 끝난 뒤 1초를 기다리지 않는다. 전체 예약
입찰은 약 360건이며 입찰가는 실행 순번마다 43,000원씩 증가한다.

통과 기준은 예약 누락과 입찰 실패 0건, WebSocket open과 STOMP 연결 99.9% 이상, 이벤트 중복과
`bidId` 역순 0건, 알림 전파 지연 p95 500ms 미만과 p99 1초 미만이다.

REST 입찰 처리 시간과 이벤트 `occurredAt`부터 WebSocket 수신까지의 알림 전파 지연은 별도
지표로 보고한다. 후자를 REST 요청 시작부터의 단일 E2E 지연으로 표현하지 않는다.

## 6. 시나리오 3: 700·1,000 동시 재연결

700 VU와 1,000 VU를 각각 연결해 60초 유지한 뒤 연결을 종료한다. 각 VU는 실패 전용 경로로
5회 연속 연결에 실패하고 1~~2초, 2~~3초, 4~~5초, 8~~9초, 16~17초를 차례로 기다린다. 6차는
30초 뒤 정상 WebSocket 경로로 접속해 STOMP 연결과 구독을 20초 유지한다.

통과 기준은 최초 연결과 최종 재연결 99.9% 이상, 의도한 실패 5회, 실패 경로의 예상하지 않은
성공 0건, 최종 복구 실패 0건, handshake p95 5초 미만과 단계별 backoff 범위 준수다.

이 시험은 실제 heartbeat timeout을 만들지 않는다. 프론트 정책을 모사한 대규모 재연결 부하와
정상 endpoint 복귀 시 서버의 연결 복구를 검증한다.

## 7. 실행 순서와 산출물

```text
유휴 연결 6분 -> 60초 회수
입찰 알림 6분 -> 60초 회수
재연결 700 -> 60초 회수
재연결 1,000 -> 60초 회수
```

GitHub Actions artifact에는 다음 파일을 저장한다.

```text
idle-staged.json
bid-e2e-staged.json
reconnect-after-700.json
reconnect-after-1000.json
scenario-timeline.csv
websocket-capacity-report.md
```

최종 판정에는 같은 시간대의 process/system CPU, heap, GC, open file descriptor와 T3 CPU credit을
함께 사용한다. 이번 결과는 확인한 workload의 통과 여부이며 t3.small의 최대 접속자 보증으로
해석하지 않는다.
