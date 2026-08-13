ALTER TABLE member
    ADD COLUMN oauth_provider VARCHAR(32) NULL,
    ADD COLUMN oauth_subject VARCHAR(255) NULL,
    ADD COLUMN external_profile_image_url VARCHAR(2048) NULL,
    ADD CONSTRAINT uk_member_oauth_identity UNIQUE (oauth_provider, oauth_subject);
