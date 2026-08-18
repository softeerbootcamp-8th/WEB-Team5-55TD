# 운영 로그 수집 (Datadog Logs)

OOTD-403 조사 결과 운영 EC2에는 Datadog Agent가 APM/JMX 목적으로만 설치되어 있고(`datadog.yaml`의 `logs_enabled`가 기본값 `false`), 실제 로그(`journalctl -u pickup`)를 Datadog으로 전송하는 설정이 없었다. 이 문서는 그 로그 수집을 켜는 절차다.

## 설치

MySQL DBM(`mysql-dbm.yaml`)과 동일하게 **애플리케이션 배포(`deploy.sh`/백엔드 CD)와 분리해 수동으로 설치**한다. 커밋마다 Datadog Agent 자체를 재시작할 이유가 없고, 이 설정은 호스트 단위로 한 번만 적용하면 되기 때문이다.

- `deploy/journald-logs.yaml` → `/etc/datadog-agent/conf.d/journald.d/conf.yaml` (`pickup.service`의 journald 로그를 `service:pickup-api`, `source:java`로 수집)
- `deploy/datadog-agent-logs.env` → `/etc/datadog-agent/environment` (`datadog-agent.service`의 `EnvironmentFile`. `DD_LOGS_ENABLED=true`)

두 파일을 설치한 뒤 Datadog Agent를 재시작해야 적용된다.

```bash
sudo install -o dd-agent -g dd-agent -m 0644 deploy/journald-logs.yaml /etc/datadog-agent/conf.d/journald.d/conf.yaml
sudo install -o dd-agent -g dd-agent -m 0640 deploy/datadog-agent-logs.env /etc/datadog-agent/environment
sudo systemctl restart datadog-agent
```

개발 환경은 이 저장소에 상시 배포되는 백엔드 인프라가 없다(로컬 실행 또는 OOTD-370의 임시 부하테스트 k8s 클러스터뿐). 그래서 여기서는 운영만 다룬다. 개발 환경에 로그 수집이 필요해지면 그 시점의 배포 대상(예: 상시 개발 서버 또는 k8s)에 맞춰 별도로 설정한다.

## 검증

```bash
sudo datadog-agent configcheck | grep -A5 journald
sudo datadog-agent status | grep -A10 "Logs Agent"
sudo journalctl -u pickup -n 5 --no-pager   # 최근 로그가 실제로 있는지 확인
```

Datadog Logs Explorer에서 `service:pickup-api source:java`로 검색해 최근 로그가 들어오는지 확인한다. 이 저장소 CI에서 검증한 [OOTD-419](https://softeer5.atlassian.net/browse/OOTD-419)의 logback 프로파일별 레벨(운영 INFO)이 그대로 적용된 로그가 보여야 한다.
