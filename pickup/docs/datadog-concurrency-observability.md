# 입찰 동시성 부하 테스트용 Datadog 설정

애플리케이션 코드를 변경하지 않고 MySQL DB 락, HikariCP, Tomcat 요청 스레드와 JVM 프로파일을 수집하기 위한 배포 설정이다.

## 설치

1. `deploy/hikaricp-jmx.yaml`과 `deploy/tomcat-jmx.yaml`을 각각 `/opt/datadog/jmx/hikaricp.yaml`, `/opt/datadog/jmx/tomcat.yaml`로 복사하고 애플리케이션 실행 사용자가 읽을 수 있게 한다.
2. `deploy/pickup-datadog.conf`를 `/etc/systemd/system/pickup.service.d/datadog.conf`로 복사한다.
3. `deploy/mysql-dbm.yaml`을 `/etc/datadog-agent/conf.d/mysql.d/conf.yaml`로 복사한다.
4. Datadog Agent 서비스에 `DB_HOST`, `DB_PASSWORD` 환경변수를 제공한다.
5. MySQL에 Datadog 전용 계정과 공식 DBM 권한을 부여하고 `performance_schema` statement/wait consumer를 활성화한다.
6. `systemctl daemon-reload` 후 `datadog-agent`와 `pickup` 서비스를 재시작한다.

Profiler와 JMX 수집에는 메모리 오버헤드가 있으므로 운영 적용 전에 인스턴스의 메모리와 스왑 여유를 확인한다. 이 설정은 별도의 allocation/heap profiler를 강제로 켜지 않는다.

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
- Continuous Profiler에서 `service:pickup-api env:production` 데이터와 `http-nio-*` 스레드 wall time 확인

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
