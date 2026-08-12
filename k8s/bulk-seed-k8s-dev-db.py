#!/usr/bin/env python3
"""
Bulk Data Seeder for Kubernetes Development Environment MySQL Database.
Generates 25,000+ valid JPA-compliant records across auction, card, consignment, certificate, bid, and member tables.
"""

import subprocess
import sys
import time

def generate_bulk_sql(count=2500):
    print(f"Generating 100% JPA-compliant SQL seed data for {count} entities...")
    lines = ["USE pickup;", "SET FOREIGN_KEY_CHECKS=0;"]

    # 1. Members
    lines.append("-- Members")
    member_vals = []
    for i in range(1, count + 1):
        member_vals.append(f"({i}, 'dev_user_{i}', '$2a$10$wE9dummyhash', '개발유저_{i}', NOW(), NOW(), 'profile_{i}.jpg')")
        if len(member_vals) >= 1000:
            lines.append("INSERT IGNORE INTO member (member_id, login_id, password, nickname, joined_at, updated_at, profile_image_object_key) VALUES " + ",\n".join(member_vals) + ";")
            member_vals = []
    if member_vals:
        lines.append("INSERT IGNORE INTO member (member_id, login_id, password, nickname, joined_at, updated_at, profile_image_object_key) VALUES " + ",\n".join(member_vals) + ";")

    # 2. Cards
    lines.append("-- Cards")
    card_vals = []
    card_types = ["Pikachu", "Charizard", "Mewtwo", "Gengar", "Rayquaza", "Eevee", "Lugia", "Blastoise", "Venusaur", "Snorlax"]
    languages = ["KOREAN", "ENGLISH", "JAPANESE"]

    for i in range(1, (count * 2) + 1):
        name = f"{card_types[i % len(card_types)]} Special #{i}"
        set_name = f"Set-{(i % 50) + 1}"
        lang = languages[i % len(languages)]
        rarity = "MINT"
        img = f"https://media.pickup-ootd.com/cards/{i}.jpg"
        num = f"{(i % 200) + 1}/200"
        card_vals.append(f"({i}, '{name}', '{set_name}', '{lang}', '{rarity}', '{img}', '{num}', 0)")
        if len(card_vals) >= 1000:
            lines.append("INSERT IGNORE INTO card (card_id, card_name, set_name, language, rarity, image_url, card_number, is_deleted) VALUES " + ",\n".join(card_vals) + ";")
            card_vals = []
    if card_vals:
        lines.append("INSERT IGNORE INTO card (card_id, card_name, set_name, language, rarity, image_url, card_number, is_deleted) VALUES " + ",\n".join(card_vals) + ";")

    # 3. Consignments
    lines.append("-- Consignments")
    cons_vals = []
    for i in range(1, (count * 2) + 1):
        card_id = i
        seller_id = (i % count) + 1
        status = "AUCTION_ONGOING"
        cons_vals.append(f"({i}, {card_id}, {seller_id}, '이상 없음', '{status}')")
        if len(cons_vals) >= 1000:
            lines.append("INSERT IGNORE INTO consignment (consignment_id, card_id, seller_member_id, major_defect, status) VALUES " + ",\n".join(cons_vals) + ";")
            cons_vals = []
    if cons_vals:
        lines.append("INSERT IGNORE INTO consignment (consignment_id, card_id, seller_member_id, major_defect, status) VALUES " + ",\n".join(cons_vals) + ";")

    # 4. Certificates
    lines.append("-- Certificates")
    cert_vals = []
    bodies = ["PSA", "BGS", "CGC"]
    grades = ["GEM_MINT", "MINT", "NM_MT", "NM"]
    for i in range(1, (count * 2) + 1):
        cons_id = i
        sn = f"CERT{1000000 + i}"
        grade = grades[i % len(grades)]
        body = bodies[i % len(bodies)]
        cert_vals.append(f"({i}, {cons_id}, '{sn}', '{grade}', '{body}', '2025-01-01')")
        if len(cert_vals) >= 1000:
            lines.append("INSERT IGNORE INTO certificate (certificate_id, consignment_id, serial_number, grade, certification_body, inspected_at) VALUES " + ",\n".join(cert_vals) + ";")
            cert_vals = []
    if cert_vals:
        lines.append("INSERT IGNORE INTO certificate (certificate_id, consignment_id, serial_number, grade, certification_body, inspected_at) VALUES " + ",\n".join(cert_vals) + ";")

    # 5. Auctions
    lines.append("-- Auctions")
    auction_vals = []
    astatuses = ["ONGOING", "ENDED"]
    for i in range(1, (count * 2) + 1):
        cons_id = i
        status = "ONGOING" if (i % 5 != 0) else "ENDED"
        s_price = ((i % 100) + 1) * 10000
        r_price = s_price * 2
        inc = 10000
        auction_vals.append(f"({i}, {cons_id}, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', '{status}', {s_price}, {r_price}, {inc}, {s_price}, '2026-01-01 00:00:00')")
        if len(auction_vals) >= 1000:
            lines.append("INSERT IGNORE INTO auction (auction_id, consignment_id, winning_bid_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, winning_price, created_at) VALUES " + ",\n".join(auction_vals) + ";")
            auction_vals = []
    if auction_vals:
        lines.append("INSERT IGNORE INTO auction (auction_id, consignment_id, winning_bid_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, winning_price, created_at) VALUES " + ",\n".join(auction_vals) + ";")

    # 6. Bids
    lines.append("-- Bids")
    bid_vals = []
    for i in range(1, (count * 5) + 1):
        auction_id = (i % (count * 2)) + 1
        bidder_id = (i % count) + 1
        price = 100000 + (i * 1000)
        bid_vals.append(f"({i}, {auction_id}, {bidder_id}, {price}, 'SUCCESS', '2026-02-01 12:00:00')")
        if len(bid_vals) >= 1000:
            lines.append("INSERT IGNORE INTO bid (bid_id, auction_id, member_id, bid_price, bid_status, created_at) VALUES " + ",\n".join(bid_vals) + ";")
            bid_vals = []
    if bid_vals:
        lines.append("INSERT IGNORE INTO bid (bid_id, auction_id, member_id, bid_price, bid_status, created_at) VALUES " + ",\n".join(bid_vals) + ";")

    lines.append("SET FOREIGN_KEY_CHECKS=1;")
    lines.append("UPDATE certificate SET grade='GEM_MINT' WHERE grade NOT IN ('GEM_MINT', 'MINT', 'NM_MT', 'NM');")
    lines.append("SELECT 'MEMBER_COUNT', COUNT(*) FROM member;")
    lines.append("SELECT 'CARD_COUNT', COUNT(*) FROM card;")
    lines.append("SELECT 'AUCTION_COUNT', COUNT(*) FROM auction;")
    lines.append("SELECT 'BID_COUNT', COUNT(*) FROM bid;")

    return "\n".join(lines)

def main():
    print("🚀 Bulk Seeding 25,000+ 100% JPA-compliant records into K8s Dev MySQL...")
    start_time = time.time()
    sql_script = generate_bulk_sql(count=2500)

    cmd = ["kubectl", "exec", "-i", "-n", "pickup", "deployment/pickup-mysql", "--", "mysql", "-upickup", "-ppickuppass", "pickup"]
    proc = subprocess.run(cmd, input=sql_script, text=True, capture_output=True)
    
    elapsed = round(time.time() - start_time, 2)
    if proc.returncode == 0:
        print(f"✅ BULK SEEDING SUCCESSFUL! (Took {elapsed}s)")
        print(proc.stdout)
    else:
        print(f"❌ BULK SEEDING FAILED: {proc.stderr}")
        sys.exit(1)

if __name__ == "__main__":
    main()
