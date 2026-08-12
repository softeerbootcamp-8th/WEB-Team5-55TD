# 입찰 요청 간헐 지연 원인 분석 리포트

- 분석 일시: 2026-08-12 16:25 KST
- 대상: production `pickup-api`, EC2 `i-05e05b4ef4e94669c` (`t3.micro`)
- 분석 범위: Datadog 최근 24시간, 2026-08-11 18:15~18:40 KST 고해상도 구간, EC2/JVM 현재 상태
- 대시보드: https://app.us5.datadoghq.com/dashboard/jpp-yw4-yqp/prod-pickup-jvm--ec2-memory--gc

## 1. 결론

입찰 요청 지연의 직접 원인은 **Old Gen 포화에 따른 Serial Full GC의 장시간 Stop-The-World와, 호스트 메모리 부족으로 발생한 swap I/O가 겹친 것**이다.

2026-08-11 18:29~18:31 KST에 다음 현상이 같은 시간축에서 확인됐다.

- Old Gen 사용량이 최대 261,871,112 bytes로 상승했다. 최대 268,435,456 bytes 대비 **97.6%**다.
- `allocation_failure` Major GC가 **41.898초** 발생했다.
- 입찰 API `POST /auctions/{auctionId}/bids` 최대 응답시간이 **44.122초**였다.
- 같은 시각 EC2 CPU I/O wait가 **90.2%**까지 상승했다.
- swap 사용량은 약 **1 GiB**였고, 호스트 가용 메모리는 **5.8~12.3%** 구간까지 하락했다.
- JVM 스레드는 60개 수준에서 최대 249개까지 증가했다.
- 같은 구간 HikariCP pending은 계속 **0**이었다.

따라서 DB 커넥션 풀 대기보다, JVM이 Full GC로 멈춘 시간과 swap으로 인한 디스크 대기가 요청 지연시간을 지배했다. 41.9초 GC와 44.1초 입찰 응답시간이 거의 일치하는 것이 가장 강한 근거다.

## 2. 핵심 관측값

| 영역 | 관측값 | 판정 |
|---|---:|---|
| 입찰 API 최대 응답시간 | 44.122초 | 장애 증상 확인 |
| 전체 servlet 최대 응답시간 | 83.324초 | 입찰 외 API도 같은 자원 고갈 영향 |
| Major GC 최대 pause | 41.898초 (`allocation_failure`) | 직접 원인 |
| Code Cache 유발 Major GC | 최대 37.279초 (`codecache_gc_threshold`) | 별도 GC 기여 요인 |
| GC overhead | 고해상도 구간 최대 67.4%, 입찰 지연 구간 40~55% | 애플리케이션 실행시간 상당 부분을 GC가 점유 |
| Heap 최대 사용량(24시간 5분 집계) | 약 294.1 MB / 389.3 MB, 75.5% | 전체 Heap 비율만 보면 심각도가 가려짐 |
| Old Gen 최대 사용량(고해상도) | 261.9 MB / 268.4 MB, 97.6% | Full GC 직접 유발 |
| EC2 가용 메모리 | 최저 약 5.8%, 24시간 집계 최저 약 10% | 심각한 호스트 메모리 압박 |
| EC2 I/O wait | 고해상도 최대 90.2% | swap/page-in으로 GC와 요청 처리 지연 증폭 |
| JVM 스레드 | 부하 구간 최대 249개 | native memory 및 스케줄링 비용 증가 |
| Hikari pending | 장애 구간 0 | DB 풀 고갈은 1차 원인 아님 |

## 3. 장애 타임라인

### 2026-08-11 17:16~17:25 KST

- `pickup.service`가 재기동됐고 애플리케이션 시작에 약 44.6초가 걸렸다.
- 재기동 약 8분 후 JVM 스레드, system load, I/O wait가 동시에 급등했다.
- servlet 요청 최대시간이 83.3초까지 증가했다.

### 2026-08-11 18:23~18:28 KST

- Old Gen이 약 106 MB에서 252.8 MB로 빠르게 증가했다.
- JVM 스레드는 60개에서 153개 이상으로 증가했다.
- swap 사용량도 약 424 MB에서 956 MB로 빠르게 증가했다.
- 18:28:10 입찰 요청에서 19.0초 지연이 먼저 관측됐다.

### 2026-08-11 18:29~18:35 KST

- 18:29:10 `allocation_failure` Full GC: 41.898초
- 18:29:30 Old Gen: 약 257.5 MB
- 18:30:20 CPU I/O wait: 90.2%
- 18:30:30 입찰 API: 44.122초
- 18:31:10 Full GC: 36.439초
- 18:31:20 입찰 API: 43.358초
- 이후에도 34.386초, 17.731초 등 Major GC가 반복됐다.
- JVM 스레드는 최대 249개, GC overhead는 40~55% 구간을 유지했다.

## 4. 현재 상태에서도 재현되는 증거

2026-08-12 16:24 KST 운영 인스턴스를 확인한 결과다.

- JVM 기동 시간: 약 2시간 9분
- GC: Young GC 193회/15.041초, Full GC 10회/104.640초
- Full GC 평균 pause: 약 **10.46초**
- JVM: Java 21, `-Xms256m`, `-Xmx384m`, `UseSerialGC`
- 프로세스 RSS: 약 391 MiB
- 프로세스 swap: 약 378 MiB
- systemd 서비스 cgroup swap: 약 736 MiB
- 호스트: 총 909 MiB, available 156 MiB, swap 972.5 MiB 사용
- Metaspace: 209,645 KiB / committed 211,200 KiB
- Compressed Class Space: 26,332 KiB / committed 27,136 KiB
- Code Cache: 100,285 KiB / 245,760 KiB, `full_count=0`

재기동 후 두 시간 만에 Full GC 누적 정지가 104초라는 점에서 일회성 현상이 아니다. Code Cache 자체는 현재 40.8% 사용으로 가득 차지 않았으므로 `codecache_gc_threshold`는 Code Cache OOM이 아니라 컴파일/클래스 로딩에 의해 GC 임계 이벤트가 발생한 것으로 해석해야 한다.

## 5. 근본 원인과 기여 요인

### 5.1 1차 원인: 인스턴스 메모리 용량 부족

운영체제가 인식하는 메모리는 909 MiB인데 JVM은 Heap 외에도 다음 메모리를 사용한다.

- 최대 Heap 384 MiB
- Metaspace 약 205 MiB
- Code Cache 약 98 MiB 사용
- 스레드 스택, direct buffer, JVM native memory
- Datadog Java agent 및 Datadog host agent
- 운영체제와 journald

Heap, Metaspace, Code Cache만 합쳐도 약 687 MiB다. 여기에 native/agent/OS 메모리가 추가되므로 `t3.micro`에서 정상적인 여유 공간을 확보하기 어렵다. Heap만 보고 75%라서 안전하다고 판단하면 안 된다.

### 5.2 1차 원인: Old Gen 포화와 SerialGC

부하 구간에 Old Gen이 97.6%까지 빠르게 상승했고 `allocation_failure` Full GC가 반복됐다. SerialGC의 Full GC는 애플리케이션 스레드를 모두 멈추며, 현재 환경에서는 한 번에 수십 초가 걸렸다. 입찰 요청 지연시간과 Full GC pause가 거의 동일하다.

### 5.3 증폭 요인: swap과 높은 I/O wait

메모리 부족으로 JVM 메모리 페이지 일부가 swap으로 밀려났다. Full GC는 Old Gen과 메타데이터를 폭넓게 접근하므로 swap된 페이지를 다시 읽는 동안 디스크 대기가 발생한다. 장애 구간의 I/O wait 90.2%는 GC pause가 일반적인 수백 ms가 아니라 수십 초로 늘어난 주요 증폭 요인이다.

### 5.4 증폭 요인: API·스케줄러·SQS consumer의 단일 JVM 공존

동일 JVM과 동일 EC2에서 API, 경매 스케줄러, SQS consumer가 Heap, 스레드, CPU, DB pool, 디스크 I/O를 공유한다. background 작업의 객체 할당과 GC는 API 요청도 함께 멈춘다. 역할 분리는 이번 문제에서 효과가 큰 구조적 개선이다.

### 5.5 지속 부하: 실패 SQS 메시지 무한 재처리

현재 6개 실패 메시지가 약 30초마다 반복 처리되고 있다. 최근 1시간에 각 messageId가 120회씩, 총 **720회** 실패했고 매번 stack trace가 기록된다. 확인된 예외는 포인트 예약 누락 및 포인트 부족이다.

이는 입찰 지연의 단독 원인은 아니지만 다음 비용을 지속 발생시킨다.

- 예외/stack trace 객체 할당
- journald 및 Datadog 로그 I/O
- consumer 스레드, DB/SQS 호출
- 정상 메시지 처리 방해 가능성

DLQ와 최대 재시도 횟수가 없어 poison message가 운영 자원을 계속 소비하는 상태다.

## 6. 배제하거나 우선순위가 낮은 가설

### DB 커넥션 풀 고갈

장애 구간 내내 `hikaricp.connections.pending=0`이었다. DB 쿼리 자체의 개별 병목 가능성까지 배제할 수는 없지만, 이번 40초대 입찰 지연의 1차 원인은 아니다.

### Code Cache 용량 고갈

`codecache_gc_threshold` Major GC는 관측됐지만 현재 Code Cache는 245,760 KiB 중 100,285 KiB 사용이고 `full_count=0`이다. 따라서 Code Cache 고갈보다는 잦은 클래스 로딩/컴파일과 Datadog instrumentation이 GC를 추가 유발한 기여 요인으로 보는 것이 타당하다.

### 가상 스레드로 즉시 해결

가상 스레드는 platform thread stack/native memory를 줄이는 데 도움을 줄 수 있지만 Full GC와 swap을 제거하지 않는다. 동시 요청 허용량만 늘리면 객체 할당과 Old Gen 압력이 커져 현상이 악화될 수도 있다. 용량 확보, 역할 분리, backpressure를 먼저 적용한 뒤 제한된 동시성 하에서 검토해야 한다.

## 7. 권고 조치

### P0: 즉시

1. `t3.micro` 사용을 중단한다.
   - 단일 인스턴스에서 모든 역할을 유지한다면 최소 `t3.medium`급 4 GiB를 권장한다.
   - 역할 분리를 병행하면 API는 부하 시험 후 더 작은 규격을 선택할 수 있다.
2. SQS DLQ와 `maxReceiveCount`를 설정하고 현재 poison message 6건을 격리한다.
3. API와 scheduler/consumer를 별도 프로세스 또는 별도 인스턴스로 분리한다.
4. Tomcat 및 비동기 executor 동시성을 명시적으로 제한하고 bounded queue/backpressure를 적용한다.

### P1: 단기

1. 인스턴스 증설 후 Java 21 기본 GC(G1GC)와 현재 SerialGC를 동일 부하로 비교한다.
2. Heap만 키우지 말고 host usable memory 25~30% 이상이 유지되도록 전체 native/non-heap 예산을 잡는다.
3. `-XX:NativeMemoryTracking=summary`를 시험 환경에 적용해 JVM native memory 기준선을 수집한다.
4. 클래스 로딩과 Metaspace 증가 원인을 확인한다. Datadog agent를 제외한 비교 시험도 수행한다.
5. 입찰 부하 시험에 동시 사용자 수, RPS, allocation rate, Old Gen after-GC, GC pause를 함께 기록한다.

### P2: 모니터/알람

- Major GC pause: 1초 warning, 3초 critical
- Old Gen: 75% warning, 85% critical
- host usable memory: 20% warning, 10% critical
- swap in/out: 5분 이상 0 초과 시 warning
- GC overhead: 10% warning, 20% critical
- JVM thread count: 150 warning, 200 critical
- Hikari pending: 0 초과가 1분 지속되면 warning
- SQS receive count 또는 동일 messageId 반복 실패: DLQ 전환과 함께 알림

## 8. 최종 판정

이번 현상은 애플리케이션의 단일 느린 쿼리보다 **작은 EC2에서 여러 역할을 한 JVM에 몰아넣은 상태로 동시 부하를 받아 Old Gen, native memory, swap, I/O가 연쇄 포화된 장애**다.

가장 효과가 큰 순서는 다음과 같다.

1. poison message 격리
2. 인스턴스 메모리 증설
3. API와 background worker 역할 분리
4. 동시성 제한 및 backpressure
5. 그 다음 GC와 가상 스레드 비교 시험

