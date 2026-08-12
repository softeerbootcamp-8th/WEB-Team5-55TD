#!/usr/bin/env python3
"""
PickUp Platform High-Volume Priority Load Testing Suite (K8s Dev Environment)
Executes large-scale request volume load testing against K8s Dev Server with 25,000+ DB records.
Measures RPS, Latency (Avg, p50, p90, p95, p99), Status Codes, and Database Contention.
"""

import json
import os
import random
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

TARGET_HOST = os.getenv("PICKUP_TARGET_HOST", "http://localhost:18080")

# Available auction IDs from our seeded dataset (5,000 auctions)
AUCTION_IDS = list(range(4500, 5001))

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

def send_request(method, url, data=None, headers=None, cookies=None, timeout=10):
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
        with urllib.request.urlopen(req, timeout=timeout) as resp:
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

def run_load_scenario(name, priority, get_request_fn, concurrency=100, target_requests=10000, duration=15):
    print(f"\n========================================================")
    print(f"🚀 Dev Load Scenario: [{priority}] {name}")
    print(f"📌 Target Server: {TARGET_HOST}")
    print(f"⚡ Target Requests: ~{target_requests} | Concurrency: {concurrency} VUs | Max Duration: {duration}s")
    print(f"========================================================")

    results = []
    status_codes = {}
    errors_count = 0
    start_test = time.time()
    end_test = start_test + duration

    def worker():
        nonlocal errors_count
        while time.time() < end_test and len(results) < target_requests:
            method, url, data, headers, cookies = get_request_fn()
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

    print(f"📊 Scenario Summary:")
    print(f"   • Total Requests Sampled: {total_reqs} reqs ({summary['rps']} req/s)")
    print(f"   • Latency: Avg={summary['avg_latency_ms']}ms | p50={summary['p50_ms']}ms | p95={summary['p95_ms']}ms | p99={summary['p99_ms']}ms")
    print(f"   • Error Rate: {summary['error_rate_pct']}% ({errors_count}/{total_reqs})")
    print(f"   • Status Codes Distribution: {status_codes}")
    return summary

def main():
    print(f"🔥 Starting PickUp Platform High-Volume Priority Load Testing (K8S DEV ENVIRONMENT with 25,000+ DB Records)...")
    print(f"🎯 Target Server: {TARGET_HOST}")

    def req_health():
        return ("GET", f"{TARGET_HOST}/actuator/health", None, None, None)

    def req_auction_detail():
        aid = random.choice(AUCTION_IDS)
        return ("GET", f"{TARGET_HOST}/auctions/{aid}", None, None, None)

    def req_featured_auction():
        return ("GET", f"{TARGET_HOST}/auctions/featured", None, None, None)

    def req_auction_list():
        page = random.randint(0, 10)
        size = random.choice([10, 20, 50])
        q = random.choice(["", "Pikachu", "Charizard", "Mewtwo", "Set-1", "Set-5"])
        url = f"{TARGET_HOST}/auctions?size={size}"
        if q:
            url += f"&q={q}"
        return ("GET", url, None, None, None)

    def req_bidding():
        aid = random.choice(AUCTION_IDS)
        price = random.randint(1000, 1000000) * 1000
        return ("POST", f"{TARGET_HOST}/auctions/{aid}/bids", {"bidPrice": price}, None, None)

    def req_mixed_traffic():
        r = random.random()
        if r < 0.40:
            return req_auction_list()
        elif r < 0.70:
            return req_auction_detail()
        elif r < 0.85:
            return req_featured_auction()
        elif r < 0.95:
            return req_bidding()
        else:
            return req_health()

    scenarios = [
        # Scenario 1: Baseline High Throughput
        {
            "name": "Dev Health Check Baseline",
            "priority": "P2 - System Baseline",
            "fn": req_health,
            "concurrency": 120,
            "target_requests": 15000,
            "duration": 15
        },
        # Scenario 2: P0 Auction Search over 5,000 DB Auctions
        {
            "name": "Dev Auction Search & Pagination (5,000 DB Auctions)",
            "priority": "P0 - Critical Read",
            "fn": req_auction_list,
            "concurrency": 120,
            "target_requests": 15000,
            "duration": 15
        },
        # Scenario 3: P0 Auction Detail Inquiry
        {
            "name": "Dev Auction Detail Inquiry (500 Auction ID pool)",
            "priority": "P0 - Critical Read",
            "fn": req_auction_detail,
            "concurrency": 120,
            "target_requests": 15000,
            "duration": 15
        },
        # Scenario 4: P0 Featured Auction Inquiry
        {
            "name": "Dev Featured Auction Inquiry",
            "priority": "P0 - Critical Read",
            "fn": req_featured_auction,
            "concurrency": 120,
            "target_requests": 15000,
            "duration": 15
        },
        # Scenario 5: P0 Real-Time Bidding Contention
        {
            "name": "Dev Real-Time Bidding Write Contention",
            "priority": "P0 - Critical Write",
            "fn": req_bidding,
            "concurrency": 80,
            "target_requests": 10000,
            "duration": 15
        },
        # Scenario 6: Mixed Realistic Production Spike Test (70% Read / 25% Write / 5% Sys)
        {
            "name": "Dev Mixed Production Spike Test",
            "priority": "P0 - Overall Spike",
            "fn": req_mixed_traffic,
            "concurrency": 150,
            "target_requests": 25000,
            "duration": 20
        }
    ]

    all_summaries = []
    total_samples = 0

    for sc in scenarios:
        res = run_load_scenario(
            name=sc["name"],
            priority=sc["priority"],
            get_request_fn=sc["fn"],
            concurrency=sc["concurrency"],
            target_requests=sc["target_requests"],
            duration=sc["duration"]
        )
        all_summaries.append(res)
        total_samples += res["total_requests"]
        time.sleep(2)

    report_file = os.path.join(os.path.dirname(__file__), "dev_high_volume_load_test_results.json")
    with open(report_file, "w", encoding="utf-8") as f:
        json.dump(all_summaries, f, indent=2, ensure_ascii=False)

    print(f"\n========================================================")
    print(f"🎉 DEV ENVIRONMENT HIGH-VOLUME LOAD TEST SUITE COMPLETE!")
    print(f"📊 Total Request Samples Executed: {total_samples} requests")
    print(f"📁 JSON Results: {report_file}")
    print(f"========================================================")

if __name__ == "__main__":
    main()
