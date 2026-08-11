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
    Object.values(metric.thresholds || {}).some((hasFailed) => hasFailed === true),
  );
}

function scenarioName(fileName) {
  if (fileName.startsWith('idle-')) return '유휴 연결';
  if (fileName.startsWith('bid-e2e-')) return '입찰 E2E';
  if (fileName.startsWith('reconnect-before-')) return '재연결 baseline';
  return '재연결';
}

const files = (await readdir(resultsDirectory))
  .filter((fileName) => /^(idle|bid-e2e|reconnect|reconnect-before)-\d+\.json$/.test(fileName))
  .sort((left, right) => left.localeCompare(right, 'en', { numeric: true }));

const rows = [];
for (const fileName of files) {
  const result = JSON.parse(await readFile(path.join(resultsDirectory, fileName), 'utf8'));
  const metrics = result.metrics || {};
  const target = fileName.match(/-(\d+)\.json$/)?.[1] || '-';
  rows.push({
    scenario: scenarioName(fileName),
    target,
    verdict: hasFailedThreshold(metrics) ? '실패' : '통과',
    opened: metricValue(metrics, 'ws_open_success', 'count', metricValue(metrics, 'initial_open_success', 'count')),
    stomp: metricValue(metrics, 'stomp_connected', 'count'),
    bidSuccess: metricValue(metrics, 'bid_success', 'count'),
    bidFailures: metricValue(metrics, 'bid_failures', 'count'),
    events: metricValue(metrics, 'ws_events_received', 'count'),
    duplicates: metricValue(metrics, 'ws_duplicate_events', 'count'),
    orderErrors: metricValue(metrics, 'ws_order_errors', 'count'),
    reconnectSuccess: metricValue(metrics, 'reconnect_success', 'count'),
    reconnectFailures: metricValue(metrics, 'reconnect_failures', 'count'),
    handshakeP95: milliseconds(
      metricValue(
        metrics,
        'ws_handshake_latency',
        'p(95)',
        metricValue(metrics, 'reconnect_handshake_latency', 'p(95)', null),
      ),
    ),
    deliveryP95: milliseconds(metricValue(metrics, 'ws_delivery_latency', 'p(95)', null)),
  });
}

const lines = [
  '# WebSocket capacity test report',
  '',
  `- Generated at: ${new Date().toISOString()}`,
  '',
  '## 전체 단계',
  '',
  '| 시나리오 | 목표 session | 결과 | open | STOMP | handshake p95 |',
  '| --- | ---: | --- | ---: | ---: | ---: |',
  ...rows.map((row) =>
    `| ${row.scenario} | ${row.target} | ${row.verdict} | ${row.opened} | ${row.stomp} | ${row.handshakeP95} |`,
  ),
  '',
  '## 입찰 E2E',
  '',
  '| 목표 session | 입찰 성공 | 입찰 실패 | 이벤트 수신 | 중복 | 역순 | 전달 p95 |',
  '| ---: | ---: | ---: | ---: | ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario === '입찰 E2E')
    .map((row) =>
      `| ${row.target} | ${row.bidSuccess} | ${row.bidFailures} | ${row.events} | ${row.duplicates} | ${row.orderErrors} | ${row.deliveryP95} |`,
    ),
  '',
  '## 재연결',
  '',
  '| 기준 | 목표 session | 재연결 성공 | 재연결 실패 | handshake p95 |',
  '| --- | ---: | ---: | ---: | ---: |',
  ...rows
    .filter((row) => row.scenario === '재연결' || row.scenario === '재연결 baseline')
    .map((row) =>
      `| ${row.scenario} | ${row.target} | ${row.reconnectSuccess} | ${row.reconnectFailures} | ${row.handshakeP95} |`,
    ),
  '',
  '> 이 파일은 k6 요약이다. 최종 용량 판정에는 같은 시간대의 Datadog과 CloudWatch 지표를 함께 사용한다.',
  '',
];

await writeFile(outputPath, lines.join('\n'), 'utf8');
