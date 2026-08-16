import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const resultsDirectory = process.argv[2] || 'results';
const outputPath = path.join(resultsDirectory, 'websocket-capacity-report.md');

function metricValue(metrics, name, field, fallback = '-') {
  return metrics[name]?.[field] ?? fallback;
}

function milliseconds(value) {
  return typeof value === 'number' ? `${value.toFixed(1)}ms` : '-';
}

function hasFailedThreshold(metrics) {
  return Object.values(metrics).some((metric) =>
    Object.values(metric.thresholds || {}).some(
      (hasFailed) => hasFailed === true,
    ),
  );
}

function scenarioName(fileName) {
  if (fileName.startsWith('idle-')) return '유휴 연결';
  if (fileName.startsWith('bid-e2e-')) return '입찰 E2E';
  if (fileName.startsWith('reconnect-before-')) return '재연결 baseline';
  if (fileName.startsWith('reconnect-after-')) return '재연결 backoff+jitter';
  return '재연결';
}

const files = (await readdir(resultsDirectory))
  .filter((fileName) =>
    /^(idle|bid-e2e|reconnect|reconnect-before|reconnect-after)-(staged|\d+)\.json$/.test(
      fileName,
    ),
  )
  .sort((left, right) => left.localeCompare(right, 'en', { numeric: true }));

const rows = [];
for (const fileName of files) {
  const result = JSON.parse(
    await readFile(path.join(resultsDirectory, fileName), 'utf8'),
  );
  const metrics = result.metrics || {};
  const target = fileName.endsWith('-staged.json')
    ? '300→700→1,000'
    : fileName.match(/-(\d+)\.json$/)?.[1] || '-';
  rows.push({
    scenario: scenarioName(fileName),
    target,
    verdict: hasFailedThreshold(metrics) ? '실패' : '통과',
    opened: metricValue(
      metrics,
      'ws_open_success',
      'count',
      metricValue(metrics, 'initial_open_success', 'count'),
    ),
    stomp: metricValue(metrics, 'stomp_connected', 'count'),
    unexpectedCloses: metricValue(metrics, 'ws_unexpected_closes', 'count', 0),
    bidSuccess: metricValue(metrics, 'bid_success', 'count'),
    bidFailures: metricValue(metrics, 'bid_failures', 'count'),
    droppedIterations: metricValue(metrics, 'dropped_iterations', 'count', 0),
    events: metricValue(metrics, 'ws_events_received', 'count'),
    duplicates: metricValue(metrics, 'ws_duplicate_events', 'count'),
    orderErrors: metricValue(metrics, 'ws_order_errors', 'count'),
    reconnectSuccess: metricValue(metrics, 'reconnect_success', 'count'),
    reconnectFailures: metricValue(metrics, 'reconnect_failures', 'count'),
    forcedReconnectFailures: metricValue(
      metrics,
      'forced_reconnect_failures',
      'count',
    ),
    initialHandshakeP95: milliseconds(
      metricValue(metrics, 'initial_handshake_latency', 'p(95)', null),
    ),
    handshakeP95: milliseconds(
      metricValue(
        metrics,
        'ws_handshake_latency',
        'p(95)',
        metricValue(metrics, 'reconnect_handshake_latency', 'p(95)', null),
      ),
    ),
    deliveryP95: milliseconds(
      metricValue(metrics, 'ws_delivery_latency', 'p(95)', null),
    ),
    deliveryP99: milliseconds(
      metricValue(metrics, 'ws_delivery_latency', 'p(99)', null),
    ),
    bidHttpP95: milliseconds(
      metricValue(metrics, 'bid_http_duration', 'p(95)', null),
    ),
    holdDelivery: [300, 700, 1000].map((targetVus) => ({
      targetVus,
      p95: milliseconds(
        metricValue(
          metrics,
          `ws_delivery_latency{stage:hold-${targetVus}}`,
          'p(95)',
          null,
        ),
      ),
      p99: milliseconds(
        metricValue(
          metrics,
          `ws_delivery_latency{stage:hold-${targetVus}}`,
          'p(99)',
          null,
        ),
      ),
    })),
    openRecoveryP95: milliseconds(
      metricValue(metrics, 'reconnect_open_recovery_latency', 'p(95)', null),
    ),
    stompRecoveryP95: milliseconds(
      metricValue(metrics, 'reconnect_stomp_recovery_latency', 'p(95)', null),
    ),
    backoffP95: Array.from({ length: 6 }, (_, index) =>
      milliseconds(
        metricValue(
          metrics,
          `reconnect_backoff_delay{attempt:${index + 1}}`,
          'p(95)',
          null,
        ),
      ),
    ),
  });
}

const lines = [
  '# WebSocket capacity test report',
  '',
  `- Generated at: ${new Date().toISOString()}`,
  '',
  '## 전체 단계',
  '',
  '| 시나리오 | 목표 session | 결과 | open | STOMP | 조기 종료 | handshake p95 |',
  '| --- | ---: | --- | ---: | ---: | ---: | ---: |',
  ...rows.map(
    (row) =>
      `| ${row.scenario} | ${row.target} | ${row.verdict} | ${row.opened} | ${row.stomp} | ${row.unexpectedCloses} | ${row.handshakeP95} |`,
  ),
  '',
  '## 입찰 E2E',
  '',
  '| 목표 session | 입찰 성공 | 입찰 실패 | 예약 누락 | 이벤트 수신 | 중복 | 역순 | HTTP p95 | 전파 p95 | 전파 p99 |',
  '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario === '입찰 E2E')
    .map(
      (row) =>
        `| ${row.target} | ${row.bidSuccess} | ${row.bidFailures} | ${row.droppedIterations} | ${row.events} | ${row.duplicates} | ${row.orderErrors} | ${row.bidHttpP95} | ${row.deliveryP95} | ${row.deliveryP99} |`,
    ),
  '',
  '## 입찰 알림 유지 구간',
  '',
  '| 유지 session | 전파 p95 | 전파 p99 |',
  '| ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario === '입찰 E2E')
    .flatMap((row) =>
      row.holdDelivery.map(
        (hold) => `| ${hold.targetVus} | ${hold.p95} | ${hold.p99} |`,
      ),
    ),
  '',
  '## 재연결',
  '',
  '| 기준 | 목표 session | 최초 handshake p95 | 재연결 성공 | 재연결 실패 | 재연결 handshake p95 | open 복구 p95 | STOMP 복구 p95 |',
  '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario.startsWith('재연결'))
    .map(
      (row) =>
        `| ${row.scenario} | ${row.target} | ${row.initialHandshakeP95} | ${row.reconnectSuccess} | ${row.reconnectFailures} | ${row.handshakeP95} | ${row.openRecoveryP95} | ${row.stompRecoveryP95} |`,
    ),
  '',
  '## 재연결 지수 backoff',
  '',
  '| 목표 session | 의도한 연결 실패 | 1차 p95 | 2차 p95 | 3차 p95 | 4차 p95 | 5차 p95 | 6차 p95 |',
  '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario.startsWith('재연결'))
    .map(
      (row) =>
        `| ${row.target} | ${row.forcedReconnectFailures} | ${row.backoffP95.join(' | ')} |`,
    ),
  '',
  '> 이 파일은 k6 요약이다. 최종 용량 판정에는 같은 시간대의 Datadog과 CloudWatch 지표를 함께 사용한다.',
  '',
];

await writeFile(outputPath, lines.join('\n'), 'utf8');
