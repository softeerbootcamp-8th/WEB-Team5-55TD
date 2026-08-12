#!/usr/bin/env python3
"""
Ultra-Scale Data Loader for Kubernetes Development Environment MySQL Database (OOTD-370)
Generates 100,000+ Auctions, 100,000+ Cards, 100,000+ Consignments, 100,000+ Certificates, and 500,000+ Bids.
"""

import subprocess
import sys
import time

def stream_bulk_data(total_auctions=100000, total_bids=500000):
    print(f"🔥 Starting Ultra-Scale Data Seeding: {total_auctions:,} Auctions/Cards/Consignments & {total_bids:,} Bids...")
    start_time = time.time()

    member_count = 10000
    batch_size = 2000

    cmd = ["kubectl", "exec", "-i", "-n", "pickup", "deployment/pickup-mysql", "--", "mysql", "-upickup", "-ppickuppass", "pickup"]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    def write_sql(sql_str):
        proc.stdin.write(sql_str + "\n")

    write_sql("USE pickup;")
    write_sql("SET FOREIGN_KEY_CHECKS=0;")
    write_sql("SET UNIQUE_CHECKS=0;")

    # 1. Seed Members (1 to 10,000)
    print("  • Seeding 10,000 Members...")
    m_vals = []
    for i in range(1, member_count + 1):
        m_vals.append(f"({i}, 'user_{i}', '$2a$10$wE9dummy', '유저_{i}', NOW(), NOW(), 'profile_{i}.jpg')")
        if len(m_vals) >= batch_size:
            write_sql("INSERT IGNORE INTO member (member_id, login_id, password, nickname, joined_at, updated_at, profile_image_object_key) VALUES " + ",".join(m_vals) + ";")
            m_vals = []
    if m_vals:
        write_sql("INSERT IGNORE INTO member (member_id, login_id, password, nickname, joined_at, updated_at, profile_image_object_key) VALUES " + ",".join(m_vals) + ";")

    # 2. Seed Cards, Consignments, Certificates, Auctions (1 to total_auctions)
    print(f"  • Seeding {total_auctions:,} Cards, Consignments, Certificates, and Auctions...")
    card_types = ["Pikachu", "Charizard", "Mewtwo", "Gengar", "Rayquaza", "Eevee", "Lugia", "Blastoise", "Venusaur", "Snorlax", "Lucario", "Gardevoir", "Umbreon", "Espeon", "Sylveon"]
    languages = ["KOREAN", "ENGLISH", "JAPANESE"]
    bodies = ["PSA", "BGS", "CGC", "SGC", "ACE"]
    grades = ["GEM_MINT", "MINT", "NM_MT", "NM", "EX_MT"]

    c_vals, cons_vals, cert_vals, auc_vals = [], [], [], []

    for i in range(1, total_auctions + 1):
        name = f"{card_types[i % len(card_types)]} Ultra-Scale #{i}"
        set_name = f"Set-{(i % 200) + 1}"
        lang = languages[i % len(languages)]
        rarity = "MINT"
        img = f"https://media.pickup-ootd.com/cards/{i}.jpg"
        num = f"{(i % 300) + 1}/300"
        c_vals.append(f"({i}, '{name}', '{set_name}', '{lang}', '{rarity}', '{img}', '{num}', 0)")

        seller_id = (i % member_count) + 1
        cons_vals.append(f"({i}, {i}, {seller_id}, '이상 없음', 'AUCTION_ONGOING')")

        sn = f"CERT{2000000 + i}"
        grade = grades[i % len(grades)]
        body = bodies[i % len(bodies)]
        cert_vals.append(f"({i}, {i}, '{sn}', '{grade}', '{body}', '2025-01-01')")

        status = "ONGOING" if (i % 4 != 0) else "ENDED"
        s_price = ((i % 500) + 1) * 10000
        r_price = s_price * 2
        inc = 10000
        auc_vals.append(f"({i}, {i}, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', '{status}', {s_price}, {r_price}, {inc}, {s_price}, '2026-01-01 00:00:00')")

        if len(c_vals) >= batch_size:
            write_sql("INSERT IGNORE INTO card (card_id, card_name, set_name, language, rarity, image_url, card_number, is_deleted) VALUES " + ",".join(c_vals) + ";")
            write_sql("INSERT IGNORE INTO consignment (consignment_id, card_id, seller_member_id, major_defect, status) VALUES " + ",".join(cons_vals) + ";")
            write_sql("INSERT IGNORE INTO certificate (certificate_id, consignment_id, serial_number, grade, certification_body, inspected_at) VALUES " + ",".join(cert_vals) + ";")
            write_sql("INSERT IGNORE INTO auction (auction_id, consignment_id, winning_bid_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, winning_price, created_at) VALUES " + ",".join(auc_vals) + ";")
            c_vals, cons_vals, cert_vals, auc_vals = [], [], [], []

    if c_vals:
        write_sql("INSERT IGNORE INTO card (card_id, card_name, set_name, language, rarity, image_url, card_number, is_deleted) VALUES " + ",".join(c_vals) + ";")
        write_sql("INSERT IGNORE INTO consignment (consignment_id, card_id, seller_member_id, major_defect, status) VALUES " + ",".join(cons_vals) + ";")
        write_sql("INSERT IGNORE INTO certificate (certificate_id, consignment_id, serial_number, grade, certification_body, inspected_at) VALUES " + ",".join(cert_vals) + ";")
        write_sql("INSERT IGNORE INTO auction (auction_id, consignment_id, winning_bid_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, winning_price, created_at) VALUES " + ",".join(auc_vals) + ";")

    # 3. Seed Bids (1 to total_bids)
    print(f"  • Seeding {total_bids:,} Bids...")
    bid_vals = []
    for i in range(1, total_bids + 1):
        auc_id = (i % total_auctions) + 1
        bidder_id = (i % member_count) + 1
        price = 100000 + (i * 100)
        bid_vals.append(f"({i}, {auc_id}, {bidder_id}, {price}, 'SUCCESS', '2026-02-01 12:00:00')")
        if len(bid_vals) >= batch_size:
            write_sql("INSERT IGNORE INTO bid (bid_id, auction_id, member_id, bid_price, bid_status, created_at) VALUES " + ",".join(bid_vals) + ";")
            bid_vals = []
    if bid_vals:
        write_sql("INSERT IGNORE INTO bid (bid_id, auction_id, member_id, bid_price, bid_status, created_at) VALUES " + ",".join(bid_vals) + ";")

    write_sql("SET FOREIGN_KEY_CHECKS=1;")
    write_sql("SET UNIQUE_CHECKS=1;")
    write_sql("SELECT 'RESULT_MEMBER', COUNT(*) FROM member;")
    write_sql("SELECT 'RESULT_CARD', COUNT(*) FROM card;")
    write_sql("SELECT 'RESULT_AUCTION', COUNT(*) FROM auction;")
    write_sql("SELECT 'RESULT_BID', COUNT(*) FROM bid;")

    stdout_data, stderr_data = proc.communicate()
    elapsed = round(time.time() - start_time, 2)

    if proc.returncode == 0:
        print(f"\n✅ ULTRA-SCALE SEEDING COMPLETE in {elapsed}s!")
        print(stdout_data)
    else:
        print(f"\n❌ SEEDING ERROR ({proc.returncode}): {stderr_data}")
        sys.exit(1)

if __name__ == "__main__":
    stream_bulk_data(total_auctions=100000, total_bids=500000)
