#!/usr/bin/env python3
import base64
import json
import os
import urllib.request
import urllib.error

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

NOTION_TOKEN = os.getenv("NOTION_OOTD_API_KEY", "")
PAGE_ID = "3b628610-22fb-80fd-b092-f09af94b29f5"

def test_notion_page():
    url = f"https://api.notion.com/v1/pages/{PAGE_ID}"
    headers = {
        "Authorization": f"Bearer {NOTION_TOKEN}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28"
    }
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("Notion Page Title / Info:")
            props = data.get("properties", {})
            title = props.get("title", {}).get("title", [{}])[0].get("plain_text", "No title")
            print(f"Page Title: {title}")
            return data
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8")
        print(f"Notion Page HTTP Error {e.code}: {err}")
    except Exception as e:
        print(f"Notion Page Error: {e}")

if __name__ == "__main__":
    test_notion_page()
