# 입찰 동시성·WebSocket Datadog 설정

MySQL DB 락, HikariCP, Tomcat 요청 스레드와 JVM 프로파일에 더해 WebSocket 연결 상태와 다중 인스턴스 이벤트 전달 경계를 수집하기 위한 설정이다.

- 연결 수와 STOMP 세션 누계는 Spring의 `WebSocketMessageBrokerStats`를 JMX로 노출한다. 연결 이벤트마다 별도 애플리케이션 계측을 추가하지 않는다.
- Redis 발행, 인스턴스별 Redis 수신, WebSocket 브로커 발행은 프레임워크 기본 지표만으로 이어서 볼 수 없으므로 Micrometer 카운터로 계측한다.
- 사용자 ID, 경매 ID, 세션 ID는 태그로 사용하지 않는다. 모든 커스텀 카운터는 값의 종류가 제한된 `outcome`, `event_type`만 사용한다.

## 설치

백엔드 CD가 애플리케이션 JAR과 같은 리비전의 JMX 설정 3종을 S3에 올린다. EC2의 `deploy.sh`는 기존 설정을 백업하고 다음 경로에 원자적으로 설치하며, 헬스체크 실패로 애플리케이션을 롤백할 때 설정도 함께 복구한다.

- `deploy/hikaricp-jmx.yaml` → `/opt/datadog/jmx/hikaricp.yaml`
- `deploy/tomcat-jmx.yaml` → `/opt/datadog/jmx/tomcat.yaml`
- `deploy/websocket-jmx.yaml` → `/opt/datadog/jmx/websocket.yaml`

`DD_PROFILING_ENABLED`, `DD_LOGS_INJECTION`, `DD_JMXFETCH_CONFIG` 같은 dd-java-agent 설정은 systemd 드롭인이 아니라 다른 애플리케이션 환경변수와 함께 `/home/ubuntu/pickup/.env` 로 들어간다. 정적인 값은 `deploy/datadog.env` 에, 리비전마다 바뀌는 `DD_VERSION` 과 `DD_SERVICE`·`DD_ENV` 는 `.github/workflows/backend-cd.yml` 의 `APP_ENV_` 블록에 있다. `DD_JMXFETCH_CONFIG` 가 가리키는 파일이 `/opt/datadog/jmx` 에 실제로 깔렸는지는 `deploy.sh` 가 확인해 어긋나면 경고를 남긴다.

MySQL DBM 설정은 애플리케이션 배포와 분리한다. `deploy/mysql-dbm.yaml`을 `/etc/datadog-agent/conf.d/mysql.d/conf.yaml`로 설치하고, Datadog Agent 서비스에 `DB_HOST`, `DB_PASSWORD`를 제공한다. MySQL에는 Datadog 전용 계정과 공식 DBM 권한을 부여하고 `performance_schema` statement/wait consumer를 활성화한다.

Profiler와 JMX 수집에는 메모리 오버헤드가 있으므로 운영 적용 전에 인스턴스의 메모리와 스왑 여유를 확인한다. 이 설정은 별도의 allocation/heap profiler를 강제로 켜지 않는다.

기본 설정에서 Micrometer StatsD가 로컬 Datadog Agent의 DogStatsD UDP `8125` 포트로 커스텀 카운터를 보낸다. Agent가 다른 호스트나 포트를 사용하면 `DD_AGENT_HOST`, `DD_DOGSTATSD_PORT`를 지정한다. 테스트 프로필에서는 StatsD 전송이 비활성화된다.

## 검증

```bash
sudo datadog-agent configcheck
sudo datadog-agent check mysql
sudo datadog-agent status
sudo journalctl -u pickup -n 200 --no-pager
```

Datadog에서 다음 항목을 확인한다.

- DBM Databases에 `pickup-mysql` 인스턴스와 query/activity sample이 나타나는지 확인
- DBM Activity에서 `dbms:mysql @mysql.blocking_thread_id:>0`로 waiter와 blocker 확인
- `hikaricp.connections.active`, `pending`, `max`가 `pool:pickup-pool` 태그로 수집되는지 확인
- `tomcat.threads.busy`, `count`, `max`가 수집되는지 확인
- `tomcat.connections.current`, `keep_alive`, `max` 중 현재 Tomcat 버전이 노출하는 지표를 확인
- `tomcat.request_count`, `error_count`, `processing_time`이 수집되는지 확인
- `pickup.websocket.sessions.current`와 `pickup.websocket.stomp.*`가 수집되는지 확인
- 입찰 갱신 후 `pickup.redis.notification.publish`, `pickup.redis.notification.receive`, `pickup.websocket.broker.publish`이 `event_type:AUCTION_BID_UPDATED` 태그로 수집되는지 확인
- Continuous Profiler에서 `service:pickup-api env:production` 데이터와 `http-nio-*` 스레드 wall time 확인

커스텀 카운터의 `outcome` 값은 다음과 같다.

| 지표 | `outcome` | 의미 |
| --- | --- | --- |
| `pickup.redis.notification.publish` | `success`, `failure`, `no_subscribers`, `rejected` | Redis 발행 성공, 직렬화·발행 실패, 구독자 0명, 비동기 발행 실행기 거절 |
| `pickup.redis.notification.receive` | `success`, `deserialize_failure`, `channel_mismatch` | 각 인스턴스의 수신 성공, 역직렬화 실패, 채널과 이벤트 타입 불일치 |
| `pickup.websocket.broker.publish` | `success`, `failure` | Redis 수신 후 STOMP 브로커 전달 성공·실패 |

현재 Redis 이벤트 중 WebSocket 브로커까지 이어지는 것은 `AUCTION_BID_UPDATED`이므로 세 경계의 수치를 비교할 때 이 이벤트 타입으로 제한한다. Redis Pub/Sub은 구독 인스턴스마다 메시지를 전달하므로 정상적인 다중 인스턴스 환경에서는 `receive`와 `broker.publish` 합계가 `publish`보다 인스턴스 수만큼 클 수 있다. 인스턴스별 누락을 찾을 때는 Datadog이 자동 부여하는 `host` 태그로 나눠 확인한다.

## 부하 테스트 대시보드 쿼리

- `max:hikaricp.connections.active{env:$env} / max:hikaricp.connections.max{env:$env}`
- `max:hikaricp.connections.pending{env:$env} by {host,pool}`
- `max:tomcat.threads.busy{env:$env} / max:tomcat.threads.max{env:$env}`
- `max:tomcat.connections.current{env:$env} / max:tomcat.connections.max{env:$env}`
- `sum:tomcat.error_count{env:$env}.as_rate()`
- `sum:tomcat.request_count{env:$env}.as_rate()`
- `sum:mysql.innodb.row_lock_waits{env:$env}.as_rate()`
- `sum:mysql.innodb.row_lock_time{env:$env}`
- `sum:mysql.innodb.deadlocks{env:$env}.as_count()`
- `avg:mysql.performance.threads_connected{env:$env}`
- `avg:mysql.performance.threads_running{env:$env}`
- `avg:pickup.websocket.sessions.current{env:$env} by {host}`
- `sum:pickup.websocket.sessions.connect_failures{env:$env}.as_rate() by {host}`
- `sum:pickup.redis.notification.publish{env:$env,outcome:failure}.as_rate() by {event_type}`
- `sum:pickup.redis.notification.publish{env:$env,outcome:no_subscribers}.as_rate() by {event_type}`
- `sum:pickup.redis.notification.publish{env:$env,outcome:rejected}.as_rate() by {event_type}`
- `sum:pickup.redis.notification.receive{env:$env,outcome:deserialize_failure}.as_rate() by {host}`
- `sum:pickup.redis.notification.receive{env:$env,outcome:channel_mismatch}.as_rate() by {host,event_type}`
- `sum:pickup.websocket.broker.publish{env:$env,outcome:failure}.as_rate() by {host,event_type}`
