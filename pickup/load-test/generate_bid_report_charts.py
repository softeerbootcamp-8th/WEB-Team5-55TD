#!/usr/bin/env python3
"""Generate dependency-free SVG charts for the bid-mode load-test report."""

from __future__ import annotations

import json
import math
from datetime import datetime, timezone, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ASSET_DIR = ROOT / "pickup/docs/assets/bid-mode-load-test-2026-08-12"
RESULT_DIR = ROOT / "pickup/docs"
KST = timezone(timedelta(hours=9))

COLORS = {
    "active": "#2563eb",
    "pending": "#dc2626",
    "idle": "#16a34a",
    "timeout": "#9333ea",
    "sync": "#ea580c",
    "async": "#2563eb",
    "e2e": "#7c3aed",
    "cpu_user": "#dc2626",
    "cpu_system": "#f59e0b",
    "memory": "#16a34a",
    "swap": "#7c3aed",
    "grid": "#d1d5db",
    "text": "#111827",
    "muted": "#6b7280",
    "panel": "#f9fafb",
}


def esc(value: object) -> str:
  return (
      str(value)
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace('"', "&quot;")
  )


class Svg:
  def __init__(self, width: int, height: int, title: str, subtitle: str):
    self.width = width
    self.height = height
    self.parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{esc(title)}">',
        "<style>text{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans KR',sans-serif}.title{font-size:22px;font-weight:700;fill:#111827}.subtitle{font-size:13px;fill:#6b7280}.axis{font-size:11px;fill:#6b7280}.legend{font-size:12px;fill:#374151}.value{font-size:12px;font-weight:600;fill:#111827}.note{font-size:11px;fill:#6b7280}</style>",
        f'<rect width="{width}" height="{height}" fill="white" rx="12"/>',
        f'<text x="28" y="34" class="title">{esc(title)}</text>',
        f'<text x="28" y="57" class="subtitle">{esc(subtitle)}</text>',
    ]

  def add(self, value: str) -> None:
    self.parts.append(value)

  def finish(self, path: Path) -> None:
    self.parts.append("</svg>")
    path.write_text("\n".join(self.parts) + "\n", encoding="utf-8")


def load_dd(path: Path) -> dict[str, list[tuple[float, float]]]:
  payload = json.loads(path.read_text())
  result: dict[str, list[tuple[float, float]]] = {}
  for series in payload["series"]:
    metric = series["metric"]
    result[metric] = [
        (timestamp / 1000, float(value))
        for timestamp, value in series["pointlist"]
        if value is not None
    ]
  return result


def max_value(series: list[tuple[float, float]]) -> float:
  return max((value for _, value in series), default=0)


def min_value(series: list[tuple[float, float]]) -> float:
  return min((value for _, value in series), default=0)


def panel(svg: Svg, x: float, y: float, width: float, height: float, title: str) -> None:
  svg.add(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="8" fill="{COLORS["panel"]}" stroke="#e5e7eb"/>')
  svg.add(f'<text x="{x + 14}" y="{y + 23}" class="value">{esc(title)}</text>')


def plot_lines(
    svg: Svg,
    bounds: tuple[float, float, float, float],
    data: list[tuple[str, list[tuple[float, float]], str]],
    start: float,
    end: float,
    y_max: float,
    y_label: str,
    shades: list[tuple[float, float, str, str]] | None = None,
) -> None:
  x, y, width, height = bounds
  if shades:
    for shade_start, shade_end, color, label in shades:
      sx = x + (shade_start - start) / (end - start) * width
      sw = (shade_end - shade_start) / (end - start) * width
      svg.add(f'<rect x="{sx:.1f}" y="{y}" width="{sw:.1f}" height="{height}" fill="{color}" opacity="0.08"/>')
      svg.add(f'<text x="{sx + 5:.1f}" y="{y + 14}" class="axis" fill="{color}">{esc(label)}</text>')
  for tick in range(5):
    value = y_max * tick / 4
    py = y + height - height * tick / 4
    svg.add(f'<line x1="{x}" y1="{py:.1f}" x2="{x + width}" y2="{py:.1f}" stroke="{COLORS["grid"]}" stroke-dasharray="3 4"/>')
    svg.add(f'<text x="{x - 8}" y="{py + 4:.1f}" text-anchor="end" class="axis">{value:.0f}</text>')
  for tick in range(5):
    timestamp = start + (end - start) * tick / 4
    px = x + width * tick / 4
    label = datetime.fromtimestamp(timestamp, KST).strftime("%H:%M:%S")
    svg.add(f'<text x="{px:.1f}" y="{y + height + 18}" text-anchor="middle" class="axis">{label}</text>')
  svg.add(f'<text x="{x}" y="{y - 8}" class="axis">{esc(y_label)}</text>')
  for name, points, color in data:
    visible = [(ts, value) for ts, value in points if start <= ts <= end]
    if not visible:
      continue
    coords = []
    for timestamp, value in visible:
      px = x + (timestamp - start) / (end - start) * width
      py = y + height - min(value, y_max) / y_max * height
      coords.append(f"{px:.1f},{py:.1f}")
    svg.add(f'<polyline points="{" ".join(coords)}" fill="none" stroke="{color}" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/>')


def legend(svg: Svg, x: float, y: float, items: list[tuple[str, str]]) -> None:
  offset = 0
  for label, color in items:
    svg.add(f'<circle cx="{x + offset}" cy="{y - 4}" r="5" fill="{color}"/>')
    svg.add(f'<text x="{x + offset + 10}" y="{y}" class="legend">{esc(label)}</text>')
    offset += max(100, len(label) * 8 + 35)


def create_fk_chart(present: dict, removed: dict) -> None:
  svg = Svg(1120, 430, "FK 검증 락 제거 전후 Hikari 비교", "Datadog 1초 rollup · 동일 경매 행을 12초 잠근 상태에서 비동기 요청 40건")
  panels = [
      (present, 1786535280, 1786535298, 36, "FK 존재: 202 10건 / 500 30건"),
      (removed, 1786535868, 1786535885, 576, "FK 제거: 202 40건 / 500 0건"),
  ]
  for data, start, end, x, title in panels:
    panel(svg, x, 78, 508, 294, title)
    plot_lines(
        svg,
        (x + 48, 120, 438, 205),
        [
            ("active", data.get("hikaricp.connections.active", []), COLORS["active"]),
            ("pending", data.get("hikaricp.connections.pending", []), COLORS["pending"]),
            ("idle", data.get("hikaricp.connections.idle", []), COLORS["idle"]),
        ],
        start,
        end,
        36,
        "connections",
    )
  legend(svg, 305, 405, [("active", COLORS["active"]), ("pending", COLORS["pending"]), ("idle", COLORS["idle"])])
  svg.finish(ASSET_DIR / "01-fk-hikari-comparison.svg")


def create_fixed_hikari_chart(data: dict) -> None:
  start, end = 1786536595, 1786536636
  svg = Svg(1120, 430, "Fixed-load 200 VU: Hikari 커넥션 풀", "Datadog 1초 rollup · 주황 음영=동기, 파랑 음영=비동기")
  panel(svg, 36, 78, 1048, 294, "동기 active 10 / pending 7 vs 비동기 active 3 / pending 0")
  plot_lines(
      svg,
      (90, 120, 960, 205),
      [
          ("active", data.get("hikaricp.connections.active", []), COLORS["active"]),
          ("pending", data.get("hikaricp.connections.pending", []), COLORS["pending"]),
          ("idle", data.get("hikaricp.connections.idle", []), COLORS["idle"]),
      ],
      start,
      end,
      10,
      "connections",
      [
          (1786536600.47, 1786536610.53, COLORS["sync"], "동기 2,000건"),
          (1786536620.53, 1786536631.10, COLORS["async"], "비동기 2,000건"),
      ],
  )
  legend(svg, 330, 405, [("active", COLORS["active"]), ("pending", COLORS["pending"]), ("idle", COLORS["idle"])])
  svg.finish(ASSET_DIR / "02-fixed-hikari-timeseries.svg")


def bar_chart(
    svg: Svg,
    bounds: tuple[float, float, float, float],
    categories: list[str],
    groups: list[tuple[str, list[float | None], str]],
    y_max: float,
    unit: str,
) -> None:
  x, y, width, height = bounds
  for tick in range(5):
    value = y_max * tick / 4
    py = y + height - height * tick / 4
    svg.add(f'<line x1="{x}" y1="{py:.1f}" x2="{x + width}" y2="{py:.1f}" stroke="{COLORS["grid"]}" stroke-dasharray="3 4"/>')
    svg.add(f'<text x="{x - 8}" y="{py + 4:.1f}" text-anchor="end" class="axis">{value:.0f}</text>')
  category_width = width / len(categories)
  bar_width = min(48, category_width / (len(groups) + 1))
  for category_index, category in enumerate(categories):
    center = x + category_width * (category_index + 0.5)
    for group_index, (_, values, color) in enumerate(groups):
      value = values[category_index]
      if value is None:
        continue
      bx = center + (group_index - (len(groups) - 1) / 2) * (bar_width + 8) - bar_width / 2
      bh = min(value, y_max) / y_max * height
      by = y + height - bh
      svg.add(f'<rect x="{bx:.1f}" y="{by:.1f}" width="{bar_width:.1f}" height="{bh:.1f}" rx="4" fill="{color}"/>')
      svg.add(f'<text x="{bx + bar_width / 2:.1f}" y="{by - 6:.1f}" text-anchor="middle" class="value">{value:.2f}</text>')
    svg.add(f'<text x="{center:.1f}" y="{y + height + 22}" text-anchor="middle" class="axis">{esc(category)}</text>')
  svg.add(f'<text x="{x}" y="{y - 8}" class="axis">{esc(unit)}</text>')


def create_latency_chart() -> None:
  svg = Svg(1120, 470, "HTTP 응답과 비동기 최종 실패 확정 레이턴시", "부하 생성기 HTTP 측정 + DB bid_request.created_at → processed_at · p95 기준")
  panel(svg, 36, 78, 1048, 330, "비동기는 HTTP 접수를 보호하지만 최종 실패 결과는 큐 적체만큼 늦어진다")
  bar_chart(
      svg,
      (100, 125, 930, 225),
      ["Burst 300", "Fixed 200 VU", "Ramp 합계"],
      [
          ("동기 HTTP", [2.035, 1.397, 1.365], COLORS["sync"]),
          ("비동기 HTTP", [1.175, 1.309, 1.384], COLORS["async"]),
          ("비동기 최종 처리", [4.122, 22.159, 4.447], COLORS["e2e"]),
      ],
      24,
      "seconds (p95)",
  )
  legend(svg, 225, 440, [("동기 HTTP", COLORS["sync"]), ("비동기 HTTP", COLORS["async"]), ("비동기 최종 처리", COLORS["e2e"])])
  svg.finish(ASSET_DIR / "03-latency-http-vs-final.svg")


def create_host_chart(data: dict) -> None:
  start, end = 1786536595, 1786536636
  svg = Svg(1120, 670, "Fixed-load 호스트 자원", "Datadog 1초 rollup · t3.small 단일 인스턴스")
  panel(svg, 36, 78, 1048, 250, "CPU user/system")
  plot_lines(
      svg,
      (90, 118, 960, 155),
      [
          ("CPU user", data.get("system.cpu.user", []), COLORS["cpu_user"]),
          ("CPU system", data.get("system.cpu.system", []), COLORS["cpu_system"]),
      ],
      start,
      end,
      70,
      "%",
      [
          (1786536600.47, 1786536610.53, COLORS["sync"], "동기"),
          (1786536620.53, 1786536631.10, COLORS["async"], "비동기"),
      ],
  )
  panel(svg, 36, 350, 1048, 250, "가용 메모리와 Swap 사용량")
  memory = [(ts, value * 100) for ts, value in data.get("system.mem.pct_usable", [])]
  swap = [(ts, value / 1024 / 1024) for ts, value in data.get("system.swap.used", [])]
  plot_lines(
      svg,
      (90, 390, 960, 155),
      [("가용 메모리 %", memory, COLORS["memory"]), ("Swap MiB", swap, COLORS["swap"])],
      start,
      end,
      140,
      "% / MiB (공통 축)",
      [
          (1786536600.47, 1786536610.53, COLORS["sync"], "동기"),
          (1786536620.53, 1786536631.10, COLORS["async"], "비동기"),
      ],
  )
  legend(svg, 155, 635, [("CPU user", COLORS["cpu_user"]), ("CPU system", COLORS["cpu_system"]), ("가용 메모리 %", COLORS["memory"]), ("Swap MiB", COLORS["swap"])])
  svg.finish(ASSET_DIR / "04-fixed-host-resources.svg")


def create_ramp_chart() -> None:
  ramp = json.loads((RESULT_DIR / "bid-mode-ramp-20-400-2026-08-12.json").read_text())
  sync = []
  async_values = []
  for result in ramp["results"]:
    vus = int(result["name"].rsplit("-", 1)[1])
    point = (vus, result["latencyMs"]["p95"])
    (sync if result["name"].startswith("sync") else async_values).append(point)
  svg = Svg(1120, 440, "Ramp-to-break HTTP p95", "20 → 400 VU · 400 VU까지 양쪽 모두 HTTP 5xx/429 0건")
  panel(svg, 36, 78, 1048, 300, "100 VU 이후 p95가 수렴하며, 400 VU에서도 명확한 오류 임계점은 관측되지 않음")
  x, y, width, height = 100, 125, 930, 205
  y_max = 1500
  for tick in range(6):
    value = y_max * tick / 5
    py = y + height - height * tick / 5
    svg.add(f'<line x1="{x}" y1="{py:.1f}" x2="{x + width}" y2="{py:.1f}" stroke="{COLORS["grid"]}" stroke-dasharray="3 4"/>')
    svg.add(f'<text x="{x - 8}" y="{py + 4:.1f}" text-anchor="end" class="axis">{value:.0f}</text>')
  for values, color in ((sync, COLORS["sync"]), (async_values, COLORS["async"])):
    coords = []
    for vu, value in values:
      px = x + (vu - 20) / 380 * width
      py = y + height - value / y_max * height
      coords.append(f"{px:.1f},{py:.1f}")
      svg.add(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="4" fill="{color}"/>')
    svg.add(f'<polyline points="{" ".join(coords)}" fill="none" stroke="{color}" stroke-width="2.5"/>')
  for vu in (20, 50, 100, 200, 300, 400):
    px = x + (vu - 20) / 380 * width
    svg.add(f'<text x="{px:.1f}" y="{y + height + 20}" text-anchor="middle" class="axis">{vu}</text>')
  svg.add(f'<text x="{x}" y="{y - 8}" class="axis">milliseconds (p95)</text>')
  svg.add(f'<text x="{x + width}" y="{y + height + 40}" text-anchor="end" class="axis">concurrent VU</text>')
  legend(svg, 430, 410, [("동기", COLORS["sync"]), ("비동기", COLORS["async"])])
  svg.finish(ASSET_DIR / "05-ramp-p95.svg")


def main() -> None:
  ASSET_DIR.mkdir(parents=True, exist_ok=True)
  fk_present = load_dd(RESULT_DIR / "data/bid-mode-load-test-2026-08-12/hikari-fk-present.json")
  fk_removed = load_dd(RESULT_DIR / "data/bid-mode-load-test-2026-08-12/hikari-fk-removed.json")
  fixed_hikari = load_dd(RESULT_DIR / "data/bid-mode-load-test-2026-08-12/hikari-fixed.json")
  fixed_host = load_dd(RESULT_DIR / "data/bid-mode-load-test-2026-08-12/host-fixed.json")
  create_fk_chart(fk_present, fk_removed)
  create_fixed_hikari_chart(fixed_hikari)
  create_latency_chart()
  create_host_chart(fixed_host)
  create_ramp_chart()
  for path in sorted(ASSET_DIR.glob("*.svg")):
    print(path.relative_to(ROOT))


if __name__ == "__main__":
  main()
