# WebSocket 즉시 재연결 baseline 시험 계획

## 1. 목적

프론트엔드의 지수 backoff와 jitter 적용 효과를 비교하기 전에 재시도 대기가 없는 baseline을
측정한다. 서버에 동시에 연결된 WebSocket client를 1,000명, 1,500명, 2,000명으로 늘리면서
동시 재연결 요청이 서버와 연결 안정성에 미치는 영향을 확인한다.

k6 VU 한 개는 서로 독립적인 WebSocket·STOMP client 한 개다. 따라서 이 시험의 2,000 VU는
동시에 열린 WebSocket 연결 2,000개를 뜻하며, 실제 브라우저 사용자의 렌더링이나 멀티탭
lifecycle까지 재현하지는 않는다.

## 2. 공통 조건

| 항목                  | 조건                                      |
| --------------------- | ----------------------------------------- |
| 서버                  | 단일 AWS EC2 t3.small                     |
| 부하 발생기           | GitHub Actions Ubuntu runner의 k6         |
| WebSocket             | 기존 CloudFront `/api/ws`                 |
| 실패 경로             | 기존 CloudFront `/api/ws-backoff-test`    |
| Origin                | `https://pick-up.store`                   |
| 대상 VU               | 1,000 → 1,500 → 2,000                     |
| 최초 연결 유지        | 각 VU 단계에서 60초                       |
| 의도한 재연결 실패    | VU당 5회                                  |
| 최종 복구 연결 유지   | 20초                                      |
| 재시도 대기           | 0ms                                       |
| 단계 사이 자원 회수   | 60초                                      |
| backoff+jitter 비교군 | 이번 실행에서 제외하고 다음 실행에서 측정 |

## 3. VU별 실행 과정

각 VU는 다음 순서로 동작한다.

1. 정상 WebSocket endpoint에 연결하고 STOMP topic을 구독한다.
2. 목표 VU의 최초 연결을 60초간 유지한다.
3. 연결을 닫고 실패 전용 endpoint에 대기 없이 5회 연속 연결한다.
4. 여섯 번째 시도에서 정상 endpoint에 즉시 연결한다.
5. STOMP 연결과 구독을 복구하고 20초간 유지한다.

실패 endpoint와 실패 횟수는 이후 backoff+jitter 시험에서도 동일하게 사용한다. 두 시험의 차이는
각 재연결 시도 사이의 대기 시간뿐이다.

## 4. 실행 순서

```text
즉시 재연결 1,000 VU -> 60초 자원 회수
즉시 재연결 1,500 VU -> 60초 자원 회수
즉시 재연결 2,000 VU -> 60초 자원 회수
```

각 단계는 독립적인 k6 실행이다. 앞 단계의 연결을 유지한 채 다음 단계에 추가하는 누적 시험이
아니다.

## 5. 판정 지표

### 클라이언트 지표

- 최초 WebSocket open과 최종 재연결 성공률 99.9% 이상
- STOMP `CONNECTED` 성공률 99.9% 이상
- 의도한 연결 실패가 VU당 5회 발생
- 정상 endpoint 재연결 실패 0건
- socket error 0건
- 최초·복구 handshake p95 5초 미만
- 여섯 번의 재연결 대기 시간 p95가 모두 0ms
- WebSocket open과 STOMP 구독 복구 시간 p95

### 서버 지표

- process·system CPU 평균과 최대
- heap 사용량과 GC pause 평균·최대
- JVM thread 수와 시험 종료 후 회수 여부
- open file descriptor와 WebSocket session 회수 여부
- transport error와 STOMP protocol error
- T3 CPU credit 소진 여부

## 6. 산출물

GitHub Actions artifact에 다음 파일을 저장한다.

```text
reconnect-before-1000.json
reconnect-before-1500.json
reconnect-before-2000.json
scenario-timeline.csv
websocket-capacity-report.md
```

이후 같은 VU, 실패 횟수와 endpoint에서 backoff+jitter 시험을 실행하고 handshake 꼬리 지연,
socket error, 복구 시간, CPU와 GC 압력을 비교한다.
