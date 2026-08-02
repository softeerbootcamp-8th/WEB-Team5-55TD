ALTER TABLE member
    CHANGE COLUMN profile_image_url profile_image_object_key VARCHAR(512),
    ADD CONSTRAINT uk_member_profile_image_object_key UNIQUE (profile_image_object_key);
