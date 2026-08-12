#!/usr/bin/env python3
import base64
import json
import os
import urllib.request

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

JIRA_SITE = os.getenv("JIRA_OOTD_SITE", "https://softeer5.atlassian.net")
JIRA_EMAIL = os.getenv("JIRA_OOTD_EMAIL", "delphox60@gmail.com")
JIRA_TOKEN = os.getenv("JIRA_OOTD_API_TOKEN", "")

def get_jira_headers():
    auth_str = f"{JIRA_EMAIL}:{JIRA_TOKEN}"
    b64_auth = base64.b64encode(auth_str.encode("utf-8")).decode("utf-8")
    return {
        "Authorization": f"Basic {b64_auth}",
        "Content-Type": "application/json",
        "Accept": "application/json"
    }

def get_issue_types():
    url = f"{JIRA_SITE}/rest/api/3/issuetype"
    req = urllib.request.Request(url, headers=get_jira_headers())
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("Jira Issue Types:")
            for t in data:
                print(f" - Name: {t.get('name')}, ID: {t.get('id')}")
    except Exception as e:
        print(f"Error fetching issue types: {e}")

if __name__ == "__main__":
    get_issue_types()
