#!/usr/bin/env python3
"""
Query Datadog metrics collected during the K8s Dev High-Volume Load Test
"""
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
    start = now - 900 # last 15 min

    queries = [
        "avg:jvm.heap_memory{env:development}",
        "avg:container.cpu.usage{env:development}",
        "avg:container.memory.usage{env:development}",
        "avg:datadog.agent.running{env:development}",
        "avg:kubernetes.pods.running{env:development}"
    ]

    print("📊 Datadog Metrics Captured During Dev Load Test:")
    for q in queries:
        query_url = f"https://api.{site}/api/v1/query?from={start}&to={now}&query={urllib.parse.quote(q)}"
        req = urllib.request.Request(query_url, headers=headers)
        try:
            with urllib.request.urlopen(req) as resp:
                res = json.loads(resp.read().decode("utf-8"))
                series = res.get("series", [])
                print(f"\n[Query]: {q}")
                for s in series:
                    pts = s.get('pointlist', [])
                    latest_val = pts[-1][1] if pts else "N/A"
                    print(f"  • Series Metric: {s.get('metric')} | Scope: {s.get('scope')} | Latest Value: {latest_val}")
        except Exception as e:
            print(f"  Error: {e}")

if __name__ == "__main__":
    main()
