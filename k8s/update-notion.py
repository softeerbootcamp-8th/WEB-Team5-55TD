#!/usr/bin/env python3
"""
Automated script to:
1. Update Notion page '1.1.0 부하테스트' (ID: 3b628610-22fb-80fd-b092-f09af94b29f5) with full load test report and links to created Jira tickets.
"""

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

NOTION_TOKEN = os.getenv("NOTION_OOTD_API_KEY", "")
NOTION_PAGE_ID = "3b628610-22fb-80fd-b092-f09af94b29f5"

def get_notion_headers():
    return {
        "Authorization": f"Bearer {NOTION_TOKEN}",
        "Content-Type": "application/json",
        "Notion-Version": "2022-06-28"
    }

def append_blocks_to_notion(page_id, blocks):
    url = f"https://api.notion.com/v1/blocks/{page_id}/children"
    payload = {"children": blocks}
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=get_notion_headers(), method="PATCH")
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"✅ Successfully appended {len(blocks)} blocks to Notion page!")
            return data
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8")
        print(f"❌ Notion Append Failed ({e.code}): {err}")
        return None
    except Exception as e:
        print(f"❌ Error updating Notion page: {e}")
        return None

def main():
    print("🔥 Updating Notion Page '1.1.0 부하테스트'...")

    jira_tickets = [
        {
            "key": "OOTD-368",
            "url": "https://softeer5.atlassian.net/browse/OOTD-368",
            "summary": "[Performance] GET /auctions 목록 조회 및 검색 쿼리 지연 (Multi-Join 및 Index/Pagination 병목)"
        },
        {
            "key": "OOTD-369",
            "url": "https://softeer5.atlassian.net/browse/OOTD-369",
            "summary": "[Performance] GET /auctions/featured 대표 경매 조회 DB 병목 및 Redis 캐싱 미적용"
        },
        {
            "key": "OOTD-370",
            "url": "https://softeer5.atlassian.net/browse/OOTD-370",
            "summary": "[Load Test] 수십~수백만 건 경매 및 수억 건 입찰 데이터 스케일 부하 테스트 환경 구축"
        }
    ]

    notion_blocks = [
        {
            "object": "block",
            "type": "heading_1",
            "heading_1": {
                "rich_text": [{"type": "text", "text": {"content": "⚡ PickUp 개발환경 엔드포인트별 부하 테스트 결과 보고서"}}]
            }
        },
        {
            "object": "block",
            "type": "paragraph",
            "paragraph": {
                "rich_text": [
                    {"type": "text", "text": {"content": "Datadog 개발 환경(env:development) 및 Kubernetes 개발 DB(25,000+ 레코드)에서 수행된 엔드포인트별 부하 테스트 분석 결과입니다.\n"}},
                    {"type": "text", "text": {"content": "📊 Datadog 대시보드 바로가기: "}},
                    {
                        "type": "text",
                        "text": {
                            "content": "[DEV] PickUp Platform Core Monitoring Dashboard",
                            "link": {"url": "https://app.us5.datadoghq.com/dashboard/w3i-8m9-jbn/dev-pickup-platform-core-monitoring-dashboard"}
                        }
                    }
                ]
            }
        },
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {
                "rich_text": [{"type": "text", "text": {"content": "📊 엔드포인트별 부하 테스트 성과 지표 (Sample Count: 20,423 reqs)"}}]
            }
        },
        {
            "object": "block",
            "type": "callout",
            "callout": {
                "rich_text": [
                    {"type": "text", "text": {"content": "• Dev Health Check: 456.97 RPS | p50: 247.89ms | p95: 379.63ms | 에러율 0.0%\n"}},
                    {"type": "text", "text": {"content": "• Dev Auction Search (5,000 DB Auctions): 43.49 RPS | p50: 2639.54ms | p95: 5186.50ms | 에러율 3.18%\n"}},
                    {"type": "text", "text": {"content": "• Dev Auction Detail (500 ID Pool): 105.10 RPS | p50: 1088.99ms | p95: 2707.92ms | 에러율 0.0%\n"}},
                    {"type": "text", "text": {"content": "• Dev Featured Auction: 33.48 RPS | p50: 3488.09ms | p95: 4363.25ms | 에러율 1.85%\n"}},
                    {"type": "text", "text": {"content": "• Dev Real-Time Bidding Contention: 569.97 RPS | p50: 122.25ms | p95: 232.42ms (Auth 필요)\n"}},
                    {"type": "text", "text": {"content": "• Dev Mixed Traffic Spike Test: 91.81 RPS | p50: 1626.41ms | p95: 3349.47ms | 에러율 9.32%"}}
                ],
                "icon": {"emoji": "📈"}
            }
        },
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {
                "rich_text": [{"type": "text", "text": {"content": "📦 초대형 데이터 스케일 부하 목표 (User Requirements)"}}]
            }
        },
        {
            "object": "block",
            "type": "paragraph",
            "paragraph": {
                "rich_text": [
                    {"type": "text", "text": {"content": "실제 서비스 운영 환경의 조회 성능을 다각도로 검증하기 위해 "}},
                    {"type": "text", "text": {"content": "경매 상품 수십만~수백만 건"}, "annotations": {"bold": True}},
                    {"type": "text", "text": {"content": " 및 "}},
                    {"type": "text", "text": {"content": "입찰 데이터 수억 건"}, "annotations": {"bold": True}},
                    {"type": "text", "text": {"content": " 규모의 초대형 데이터베이스 환경 구축 및 Partitioning/Sharding 성능 검증 파이프라인을 수립할 예정입니다."}}
                ]
            }
        },
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {
                "rich_text": [{"type": "text", "text": {"content": "🛠️ 해결과제 (Action Items & Jira Bug Tickets)"}}]
            }
        }
    ]

    for tk in jira_tickets:
        notion_blocks.append({
            "object": "block",
            "type": "to_do",
            "to_do": {
                "rich_text": [
                    {"type": "text", "text": {"content": f"[{tk['key']}] "}, "annotations": {"bold": True}},
                    {
                        "type": "text",
                        "text": {
                            "content": tk["summary"],
                            "link": {"url": tk["url"]}
                        }
                    }
                ],
                "checked": False
            }
        })

    append_blocks_to_notion(NOTION_PAGE_ID, notion_blocks)

if __name__ == "__main__":
    main()
