#!/usr/bin/env python3
import json
import os
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

    import time
    now = int(time.time())
    start = now - 1800

    queries = [
        "avg:jvm.heap_memory{*} by {env,service,host}",
        "avg:container.cpu.usage{*} by {env,kube_namespace,pod_name,container_name}",
        "avg:kubernetes.pods.running{*} by {env,kube_namespace}",
        "avg:mysql.performance.threads_running{*} by {env,host}",
        "avg:redis.mem.used{*} by {env,host}"
    ]

    for q in queries:
        query_url = f"https://api.{site}/api/v1/query?from={start}&to={now}&query={urllib.parse.quote(q)}"
        req = urllib.request.Request(query_url, headers=headers)
        try:
            with urllib.request.urlopen(req) as resp:
                res = json.loads(resp.read().decode("utf-8"))
                series = res.get("series", [])
                print(f"\nQuery: {q}")
                for s in series[:5]:
                    print(f"  Scope/TagSet: {s.get('scope')} | TagList: {s.get('tag_set')}")
        except Exception as e:
            print(f"Query error: {e}")

if __name__ == "__main__":
    main()
