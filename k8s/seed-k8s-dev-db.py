#!/usr/bin/env python3
"""
Seed dummy data into Kubernetes Development Environment MySQL database.
"""
import subprocess
import sys

SQL_SCRIPT = """
USE pickup;

-- Insert Members
INSERT IGNORE INTO member (member_id, login_id, password, nickname, joined_at, updated_at, profile_image_object_key) VALUES
(1, 'dev_user_1', '$2a$10$wE9dummy', '개발유저1', NOW(), NOW(), 'profile1.jpg'),
(2, 'dev_user_2', '$2a$10$wE9dummy', '개발유저2', NOW(), NOW(), 'profile2.jpg'),
(3, 'dev_user_3', '$2a$10$wE9dummy', '개발유저3', NOW(), NOW(), 'profile3.jpg'),
(4, 'dev_user_4', '$2a$10$wE9dummy', '개발유저4', NOW(), NOW(), 'profile4.jpg'),
(5, 'dev_user_5', '$2a$10$wE9dummy', '개발유저5', NOW(), NOW(), 'profile5.jpg');

-- Insert Cards
INSERT IGNORE INTO card (card_id, card_name, set_name, language, rarity, image_url, card_number, is_deleted) VALUES
(1, 'Pikachu Illustrator', 'Promo', '한국어', 'GEM-MT', 'https://media.pickup-ootd.com/cards/1.jpg', '001/050', 0),
(2, 'Charizard 1st Edition', 'Base Set', '영어', 'MINT', 'https://media.pickup-ootd.com/cards/2.jpg', '004/102', 0),
(3, 'Mewtwo Gold Star', 'EX Dragon Frontiers', '일본어', 'NM-MT', 'https://media.pickup-ootd.com/cards/3.jpg', '101/101', 0),
(4, 'Gengar VMAX High Class', 'VMAX Climax', '한국어', 'EX-MT', 'https://media.pickup-ootd.com/cards/4.jpg', '271/184', 0),
(5, 'Rayquaza Gold Star', 'EX Deoxys', '영어', 'GEM-MT', 'https://media.pickup-ootd.com/cards/5.jpg', '107/107', 0);

-- Insert Consignments
INSERT IGNORE INTO consignment (consignment_id, card_id, seller_member_id, major_defect, status) VALUES
(1, 1, 1, '없음', 'AUCTION_REGISTERED'),
(2, 2, 2, '모서리 미세 눌림', 'AUCTION_REGISTERED'),
(3, 3, 3, '없음', 'AUCTION_REGISTERED'),
(4, 4, 4, '표면 스크래치', 'AUCTION_REGISTERED'),
(5, 5, 5, '없음', 'AUCTION_REGISTERED');

-- Insert Certificates
INSERT IGNORE INTO certificate (certificate_id, consignment_id, serial_number, grade, certification_body, inspected_at) VALUES
(1, 1, 'PSA1000001', 'PSA 10', 'PSA', '2025-01-10'),
(2, 2, 'PSA1000002', 'BGS 9.5', 'BGS', '2025-01-11'),
(3, 3, 'PSA1000003', 'CGC 9.0', 'CGC', '2025-01-12'),
(4, 4, 'PSA1000004', 'PSA 8.0', 'PSA', '2025-01-13'),
(5, 5, 'PSA1000005', 'PSA 10', 'PSA', '2025-01-14');

-- Insert Auctions (ONGOING)
INSERT IGNORE INTO auction (auction_id, consignment_id, winning_bid_id, started_at, ended_at, auction_status, starting_price, reserve_price, bid_increment, winning_price, created_at) VALUES
(1, 1, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ONGOING', 100000, 500000, 10000, 100000, '2026-01-01 00:00:00'),
(2, 2, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ONGOING', 250000, 1000000, 20000, 250000, '2026-01-01 00:00:00'),
(3, 3, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ONGOING', 500000, 2000000, 50000, 500000, '2026-01-01 00:00:00'),
(4, 4, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ONGOING', 80000, 300000, 5000, 80000, '2026-01-01 00:00:00'),
(5, 5, NULL, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 'ONGOING', 1500000, 5000000, 100000, 1500000, '2026-01-01 00:00:00');

SELECT auction_id, consignment_id, auction_status, starting_price, bid_increment FROM auction;
"""

def main():
    cmd = ["kubectl", "exec", "-i", "-n", "pickup", "deployment/pickup-mysql", "--", "mysql", "-upickup", "-ppickuppass", "pickup"]
    proc = subprocess.run(cmd, input=SQL_SCRIPT, text=True, capture_output=True)
    if proc.returncode == 0:
        print("✅ Successfully seeded test auctions into K8s Dev MySQL database!")
        print(proc.stdout)
    else:
        print(f"❌ Failed to seed K8s Dev DB: {proc.stderr}")
        sys.exit(1)

if __name__ == "__main__":
    main()
