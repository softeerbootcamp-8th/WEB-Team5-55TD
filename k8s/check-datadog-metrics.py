#!/usr/bin/env python3
import json
import os
import time
import urllib.request
import urllib.parse

def main():
    credentials_file = "/home/coder/.config/agent-secrets/credentials.env"
    if os.path.exists(credentials_file):
        with open(credentials_file, "r") as cf:
            for line in cf:
                line = line.strip()
                if line.startswith("export "):
                    line = line[7:]
                if "=" in line and not line.startswith("#"):
                    k, v = line.split("=", 1)
                    v = v.strip('"\'')
                    os.environ[k] = v

    api_key = os.getenv("DD_API_KEY") or os.getenv("DD_OOTD_API_KEY")
    app_key = os.getenv("DD_APP_KEY") or os.getenv("DD_OOTD_APP_KEY", "")
    site = os.getenv("DD_SITE") or os.getenv("DD_OOTD_SITE", "datadoghq.com")

    headers = {
        "Content-Type": "application/json",
        "DD-API-KEY": api_key,
        "DD-APPLICATION-KEY": app_key
    }

    now = int(time.time())
    start = now - 1800 # 30 min ago

    test_queries = [
        "avg:system.cpu.user{*}",
        "avg:container.cpu.usage{*}",
        "avg:container.memory.usage{*}",
        "avg:jvm.heap_memory{*}",
        "avg:docker.containers.running{*}",
        "avg:kubernetes.pods.running{*}",
        "avg:kubernetes_state.pod.status_phase{*}",
        "avg:datadog.agent.running{*}",
        "sum:trace.servlet.request.hits{*}",
        "avg:mysql.performance.threads_running{*}",
        "avg:redis.mem.used{*}"
    ]

    for q in test_queries:
        query_url = f"https://api.{site}/api/v1/query?from={start}&to={now}&query={urllib.parse.quote(q)}"
        req = urllib.request.Request(query_url, headers=headers)
        try:
            with urllib.request.urlopen(req) as resp:
                res = json.loads(resp.read().decode("utf-8"))
                series = res.get("series", [])
                print(f"\n[Query]: {q}")
                print(f"  Series Count: {len(series)}")
                for idx, s in enumerate(series[:5]):
                    print(f"  - Series {idx+1} Metric: {s.get('metric')}")
                    print(f"    Tags: {s.get('tag_set')}")
                    print(f"    Point count: {len(s.get('pointlist', []))}")
                    if s.get('pointlist'):
                        print(f"    Latest point: {s.get('pointlist')[-1]}")
        except Exception as e:
            print(f"Query '{q}' error: {e}")

if __name__ == "__main__":
    main()
