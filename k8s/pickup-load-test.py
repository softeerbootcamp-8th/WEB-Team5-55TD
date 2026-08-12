#!/usr/bin/env python3
"""
PickUp Platform Automated Priority Load Testing Tool
Executes prioritized load tests against target server, measures RPS, Latency (avg, p50, p95, p99), and Error Rates.
Zero external dependencies (uses standard library math & urllib).
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

TARGET_HOST = os.getenv("PICKUP_TARGET_HOST", "http://ec2-3-39-149-176.ap-northeast-2.compute.amazonaws.com:8080")

def get_percentile(sorted_data, percentile):
    if not sorted_data:
        return 0.0
    k = (len(sorted_data) - 1) * (percentile / 100.0)
    f = int(k)
    c = f + 1
    if c >= len(sorted_data):
        return float(sorted_data[-1])
    d0 = sorted_data[f] * (c - k)
    d1 = sorted_data[c] * (k - f)
    return float(d0 + d1)

def send_request(method, url, data=None, headers=None, cookies=None):
    if headers is None:
        headers = {}
    if cookies and "Cookie" not in headers:
        headers["Cookie"] = "; ".join([f"{k}={v}" for k, v in cookies.items()])
    if data and isinstance(data, dict):
        data_bytes = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif data and isinstance(data, bytes):
        data_bytes = data
    else:
        data_bytes = None

    req = urllib.request.Request(url, data=data_bytes, headers=headers, method=method)
    start_time = time.perf_counter()
    status_code = 0
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            elapsed = (time.perf_counter() - start_time) * 1000.0 # ms
            status_code = resp.status
            resp.read()
            return status_code, elapsed, None
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - start_time) * 1000.0
        status_code = e.code
        return status_code, elapsed, str(e)
    except Exception as e:
        elapsed = (time.perf_counter() - start_time) * 1000.0
        return 0, elapsed, str(e)

def run_load_scenario(name, priority, method, endpoint, data=None, headers=None, cookies=None, concurrency=20, duration=10):
    url = f"{TARGET_HOST}{endpoint}"
    print(f"\n========================================================")
    print(f"🚀 Running Load Test: [{priority}] {name}")
    print(f"📌 Endpoint: {method} {url}")
    print(f"⚡ Concurrency: {concurrency} VUs | Duration: {duration}s")
    print(f"========================================================")

    results = []
    status_codes = {}
    errors_count = 0
    start_test = time.time()
    end_test = start_test + duration

    def worker():
        nonlocal errors_count
        while time.time() < end_test:
            code, lat, err = send_request(method, url, data=data, headers=headers, cookies=cookies)
            results.append(lat)
            status_codes[code] = status_codes.get(code, 0) + 1
            if err or code >= 400:
                errors_count += 1

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(worker) for _ in range(concurrency)]
        for f in as_completed(futures):
            pass

    total_reqs = len(results)
    actual_duration = time.time() - start_test
    rps = total_reqs / actual_duration if actual_duration > 0 else 0

    if results:
        sorted_results = sorted(results)
        avg_lat = sum(results) / float(len(results))
        p50 = get_percentile(sorted_results, 50)
        p90 = get_percentile(sorted_results, 90)
        p95 = get_percentile(sorted_results, 95)
        p99 = get_percentile(sorted_results, 99)
    else:
        avg_lat = p50 = p90 = p95 = p99 = 0.0

    error_rate = (errors_count / total_reqs * 100.0) if total_reqs > 0 else 0.0

    summary = {
        "scenario": name,
        "priority": priority,
        "method": method,
        "endpoint": endpoint,
        "concurrency": concurrency,
        "duration_sec": round(actual_duration, 2),
        "total_requests": total_reqs,
        "rps": round(rps, 2),
        "avg_latency_ms": round(avg_lat, 2),
        "p50_ms": round(p50, 2),
        "p90_ms": round(p90, 2),
        "p95_ms": round(p95, 2),
        "p99_ms": round(p99, 2),
        "error_count": errors_count,
        "error_rate_pct": round(error_rate, 2),
        "status_codes": status_codes
    }

    print(f"📊 Results Summary:")
    print(f"   • Total Requests: {total_reqs} reqs ({summary['rps']} req/s)")
    print(f"   • Latency: Avg={summary['avg_latency_ms']}ms | p50={summary['p50_ms']}ms | p95={summary['p95_ms']}ms | p99={summary['p99_ms']}ms")
    print(f"   • Error Rate: {summary['error_rate_pct']}% ({errors_count}/{total_reqs})")
    print(f"   • Status Codes: {status_codes}")
    return summary

def main():
    print(f"🔥 Starting PickUp Platform Priority Load Testing suite...")
    print(f"🎯 Target Server: {TARGET_HOST}")

    scenarios = [
        {
            "name": "Health Check Baseline",
            "priority": "P2 - System Baseline",
            "method": "GET",
            "endpoint": "/healthcheck",
            "concurrency": 50,
            "duration": 8
        },
        {
            "name": "Auction Detail Inquiry",
            "priority": "P0 - Critical Read",
            "method": "GET",
            "endpoint": "/auctions/298",
            "concurrency": 50,
            "duration": 10
        },
        {
            "name": "Featured Auction Inquiry",
            "priority": "P0 - Critical Read",
            "method": "GET",
            "endpoint": "/auctions/featured",
            "concurrency": 50,
            "duration": 10
        },
        {
            "name": "Auction List Search",
            "priority": "P0 - Critical Read",
            "method": "GET",
            "endpoint": "/auctions?page=0&size=10",
            "concurrency": 50,
            "duration": 10
        },
        {
            "name": "Real-Time Bidding Contention",
            "priority": "P0 - Critical Write",
            "method": "POST",
            "endpoint": "/auctions/298/bids",
            "data": {"bidPrice": 2000000000000035700},
            "concurrency": 30,
            "duration": 10
        }
    ]

    all_summaries = []
    for sc in scenarios:
        res = run_load_scenario(
            name=sc["name"],
            priority=sc["priority"],
            method=sc["method"],
            endpoint=sc["endpoint"],
            data=sc.get("data"),
            concurrency=sc["concurrency"],
            duration=sc["duration"]
        )
        all_summaries.append(res)
        time.sleep(2)

    report_file = os.path.join(os.path.dirname(__file__), "load_test_results.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(all_summaries, f, indent=2, ensure_ascii=False)
    print(f"\n✅ Load test suite execution completed! Saved results to {report_file}")

if __name__ == "__main__":
    main()
