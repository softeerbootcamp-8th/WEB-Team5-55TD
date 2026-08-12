ALTER TABLE auction ADD COLUMN title VARCHAR(100);
ALTER TABLE auction ADD COLUMN description TEXT;

UPDATE auction SET title = card_name, description = card_name;

ALTER TABLE auction MODIFY title VARCHAR(100) NOT NULL;
