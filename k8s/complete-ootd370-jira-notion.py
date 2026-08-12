#!/usr/bin/env python3
"""
Complete OOTD-370 in Jira and update Notion page with 820,000+ DB scale load test results.
"""

import base64
import json
import os
import sys
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

def add_jira_comment(issue_key, comment_text):
    url = f"{JIRA_SITE}/rest/api/3/issue/{issue_key}/comment"
    payload = {
        "body": {
            "type": "doc",
            "version": 1,
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {
                            "type": "text",
                            "text": comment_text
                        }
                    ]
                }
            ]
        }
    }
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=get_jira_headers(), method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"✅ Added comment to Jira issue {issue_key}")
            return data
    except Exception as e:
        print(f"❌ Error adding comment to Jira {issue_key}: {e}")
        return None

def transition_jira_issue(issue_key):
    # First get transitions
    url = f"{JIRA_SITE}/rest/api/3/issue/{issue_key}/transitions"
    req = urllib.request.Request(url, headers=get_jira_headers())
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            transitions = data.get("transitions", [])
            print(f"Available transitions for {issue_key}:")
            done_id = None
            for t in transitions:
                print(f" - ID: {t.get('id')}, Name: {t.get('name')}")
                if t.get('name') in ["완료", "Done", "Done / 완료", "진행 완료"]:
                    done_id = t.get('id')
            if not done_id and transitions:
                done_id = transitions[-1].get('id')
            
            if done_id:
                t_url = f"{JIRA_SITE}/rest/api/3/issue/{issue_key}/transitions"
                t_payload = {"transition": {"id": done_id}}
                t_req = urllib.request.Request(t_url, data=json.dumps(t_payload).encode("utf-8"), headers=get_jira_headers(), method="POST")
                with urllib.request.urlopen(t_req) as t_resp:
                    print(f"✅ Transitioned {issue_key} to Done (Transition ID: {done_id})")
    except Exception as e:
        print(f"❌ Error transitioning Jira {issue_key}: {e}")

def update_notion_with_ootd370():
    url = f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children"
    notion_blocks = [
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {
                "rich_text": [{"type": "text", "text": {"content": "🔥 [OOTD-370] 820,000+ 건 DB 초고부하 스케일 성능 결과"}}]
            }
        },
        {
            "object": "block",
            "type": "callout",
            "callout": {
                "rich_text": [
                    {"type": "text", "text": {"content": "• K8s Dev DB 세팅: Member 10,000 / Card 100,000 / Auction 100,000 / Bid 500,000 (총 820,000+ 레코드)\n"}},
                    {"type": "text", "text": {"content": "• Dev Health Check Baseline: 620.98 RPS | p50: 167.90ms | p95: 309.82ms | 에러율 0.0%\n"}},
                    {"type": "text", "text": {"content": "• 10만 건 Auction Search (120 VUs): 16.13 RPS | p50: 5079.83ms | p95: 9550.14ms (Full Join 병목 발생)\n"}},
                    {"type": "text", "text": {"content": "• 1만 건 Auction Detail (120 VUs): 181.36 RPS | p50: 611.04ms | p95: 1507.52ms (HikariCP 커넥션 경합)\n"}},
                    {"type": "text", "text": {"content": "• DB Composite Index 추가: idx_auction_status_created_at (auction_status, created_at, auction_id) 생성 적용 완료"}}
                ],
                "icon": {"emoji": "🚀"}
            }
        }
    ]
    payload = {"children": notion_blocks}
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=get_notion_headers(), method="PATCH")
    try:
        with urllib.request.urlopen(req) as resp:
            print("✅ Successfully updated Notion page with OOTD-370 820,000+ DB scale benchmark results!")
    except Exception as e:
        print(f"❌ Error updating Notion page for OOTD-370: {e}")

def main():
    comment_text = """[OOTD-370 수행 완료 보고]
1. DB 데이터셋 대용량 세팅:
   - 회원 10,000건, 카드 100,000건, 위탁/감정서 100,000건, 경매 100,000건, 입찰 500,000건 (총 820,000+ 레코드)

2. DB 인덱스 최적화:
   - auction 테이블 복합 인덱스 idx_auction_status_created_at(auction_status, created_at DESC, auction_id DESC) 생성

3. 820,000건 DB 부하 테스트 결과 (120~150 VUs):
   - Health Check Baseline: 620.98 RPS (p50: 167ms, p95: 309ms)
   - 10만 건 Auction Search: Multi-Join 쿼리 부하로 지연 발생 (p50: 5,079ms, HikariCP 커넥션 점유 증가)
   - 1만 건 Auction Detail: 181.36 RPS (p50: 611ms, p95: 1,507ms)

4. 후속 조치 권장사항:
   - READ-WRITE 분리 DB 및 Redis 캐싱(@Cacheable) 적용 권장
   - 수억 건 입찰 데이터 스케일 대비 Partitioning / Sharding 기법 도입 제안"""

    add_jira_comment("OOTD-370", comment_text)
    transition_jira_issue("OOTD-370")
    update_notion_with_ootd370()

if __name__ == "__main__":
    main()
