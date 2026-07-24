CREATE TABLE IF NOT EXISTS card (
                      card_id    BIGINT       AUTO_INCREMENT,
                      card_name  VARCHAR(255) NOT NULL,
                      set_name   VARCHAR(255) NOT NULL,
                      language   VARCHAR(255) NOT NULL,
                      rarity     VARCHAR(255) NOT NULL,
                      image_url  VARCHAR(255) NOT NULL,
                      PRIMARY KEY (card_id)
);

ALTER TABLE card
    ADD INDEX IF NOT EXISTS idx_card_card_name (card_name);