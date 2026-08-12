#!/usr/bin/env python3
"""
Datadog Dashboard Importer for PickUp Development Environment
Usage:
    export DD_API_KEY="your_api_key"
    export DD_APP_KEY="your_app_key" # optional, if required by your Datadog setup
    export DD_SITE="datadoghq.com"   # or datadoghq.eu, us3.datadoghq.com, etc.
    python3 deploy-datadog-dashboard.py
"""

import json
import os
import sys
import urllib.request
import urllib.error

def main():
    script_dir = os.path.dirname(os.path.realpath(__file__))
    json_path = os.path.join(script_dir, "datadog-dashboard-dev.json")

    if not os.path.exists(json_path):
        print(f"Error: {json_path} file not found.")
        sys.exit(1)

    # Load secrets from credentials.env if available
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

    if not api_key:
        print("Notice: Neither DD_API_KEY nor DD_OOTD_API_KEY environment variable is set.")
        print("You can manually import 'datadog-dashboard-dev.json' into Datadog via UI:")
        print("Datadog UI -> Dashboards -> New Dashboard / Import Dashboard -> Upload JSON")
        sys.exit(0)

    with open(json_path, "r", encoding="utf-8") as f:
        dashboard_data = json.load(f)

    dashboard_id = os.getenv("DD_DASHBOARD_ID", "w3i-8m9-jbn")
    if dashboard_id:
        url = f"https://api.{site}/api/v1/dashboard/{dashboard_id}"
        method = "PUT"
    else:
        url = f"https://api.{site}/api/v1/dashboard"
        method = "POST"

    headers = {
        "Content-Type": "application/json",
        "DD-API-KEY": api_key,
    }
    if app_key:
        headers["DD-APPLICATION-KEY"] = app_key

    req = urllib.request.Request(url, data=json.dumps(dashboard_data).encode("utf-8"), headers=headers, method=method)

    try:
        with urllib.request.urlopen(req) as resp:
            res_json = json.loads(resp.read().decode("utf-8"))
            dashboard_id = res_json.get("id")
            dashboard_title = res_json.get("title")
            url_path = res_json.get("url")
            print("✅ Datadog Dashboard successfully created/updated!")
            print(f"📌 Title: {dashboard_title}")
            print(f"🆔 Dashboard ID: {dashboard_id}")
            if url_path:
                print(f"🔗 URL: https://app.{site}{url_path}")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"❌ Failed to create Datadog dashboard (HTTP {e.code}): {err_body}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
