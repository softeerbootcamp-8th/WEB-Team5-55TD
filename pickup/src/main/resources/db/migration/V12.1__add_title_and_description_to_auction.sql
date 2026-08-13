ALTER TABLE auction ADD COLUMN title VARCHAR(100);
ALTER TABLE auction ADD COLUMN description TEXT;

UPDATE auction
SET title = (
    SELECT cd.card_name
    FROM consignment c
    JOIN card cd ON c.card_id = cd.card_id
    WHERE c.consignment_id = auction.consignment_id
),
description = (
    SELECT cd.card_name
    FROM consignment c
    JOIN card cd ON c.card_id = cd.card_id
    WHERE c.consignment_id = auction.consignment_id
);

ALTER TABLE auction MODIFY title VARCHAR(100) NOT NULL;
