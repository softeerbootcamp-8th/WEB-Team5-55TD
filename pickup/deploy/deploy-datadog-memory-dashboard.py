#!/usr/bin/env python3
"""Create or update the production PickUp JVM/EC2 memory dashboard in Datadog."""

import json
import os
import sys
import urllib.error
import urllib.request


def load_credentials() -> None:
    credentials_file = "/home/coder/.config/agent-secrets/credentials.env"
    if not os.path.exists(credentials_file):
        return
    with open(credentials_file, encoding="utf-8") as credentials:
        for raw_line in credentials:
            line = raw_line.strip()
            if line.startswith("export "):
                line = line[7:]
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            os.environ.setdefault(key, value.strip("\"'"))


def main() -> None:
    load_credentials()
    api_key = os.getenv("DD_API_KEY") or os.getenv("DD_OOTD_API_KEY")
    app_key = os.getenv("DD_APP_KEY") or os.getenv("DD_OOTD_APP_KEY")
    site = os.getenv("DD_SITE") or os.getenv("DD_OOTD_SITE", "datadoghq.com")
    # 이미 생성된 운영 메모리 대시보드를 기본 대상으로 삼아 중복 생성을 막는다.
    dashboard_id = os.getenv("DD_MEMORY_DASHBOARD_ID", "jpp-yw4-yqp").strip()

    if not api_key or not app_key:
        raise SystemExit("DD API/Application key가 필요합니다.")

    dashboard_path = os.path.join(
        os.path.dirname(os.path.realpath(__file__)), "datadog-dashboard-memory-prod.json"
    )
    with open(dashboard_path, encoding="utf-8") as dashboard_file:
        dashboard = json.load(dashboard_file)

    if dashboard_id:
        endpoint = f"https://api.{site}/api/v1/dashboard/{dashboard_id}"
        method = "PUT"
    else:
        endpoint = f"https://api.{site}/api/v1/dashboard"
        method = "POST"

    headers = {
        "Content-Type": "application/json",
        "DD-API-KEY": api_key,
        "DD-APPLICATION-KEY": app_key,
    }
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(dashboard).encode("utf-8"),
        headers=headers,
        method=method,
    )

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8")
        print(f"Datadog dashboard API 실패 (HTTP {error.code}): {detail}", file=sys.stderr)
        raise SystemExit(1) from error

    print(f"title={result.get('title')}")
    print(f"id={result.get('id')}")
    if result.get("url"):
        print(f"url=https://app.{site}{result['url']}")


if __name__ == "__main__":
    main()
