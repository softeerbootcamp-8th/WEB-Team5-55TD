#!/usr/bin/env python3
"""
Integration script for Jira Ticket Creation and Notion Page Update.
"""

import base64
import json
import os
import sys
import urllib.request
import urllib.error

# Load secrets from credentials.env
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

NOTION_TOKEN = os.getenv("NOTION_OOTD_API_KEY", "")
NOTION_PAGE_ID = "3b628610-22fb-80fd-b092-f09af94b29f5"

def get_jira_headers():
    auth_str = f"{JIRA_EMAIL}:{JIRA_TOKEN}"
    b64_auth = base64.b64encode(auth_str.encode("utf-8")).decode("utf-8")
    return {
        "Authorization": f"Basic {b64_auth}",
        "Content-Type": "application/json",
        "Accept": "application/json"
    }

def get_notion_headers():
    return {
        "Authorization": f"Bearer {NOTION_TOKEN}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28"
    }

def test_jira_projects():
    url = f"{JIRA_SITE}/rest/api/3/project"
    req = urllib.request.Request(url, headers=get_jira_headers())
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("Jira Projects:")
            for p in data:
                print(f" - Key: {p.get('key')}, Name: {p.get('name')}, ID: {p.get('id')}")
            return data
    except Exception as e:
        print(f"Error fetching Jira projects: {e}")
        return []

if __name__ == "__main__":
    test_jira_projects()
