ALTER TABLE consignment_image
    CHANGE COLUMN image_url object_key VARCHAR(512) NOT NULL,
    ADD CONSTRAINT uk_consignment_image_object_key UNIQUE (object_key);
