SET @has_profile_image_url = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'member'
      AND column_name = 'profile_image_url'
);

SET @sql = IF(
    @has_profile_image_url > 0,
    'UPDATE member SET profile_image_url = NULL WHERE profile_image_url IS NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    @has_profile_image_url > 0,
    'ALTER TABLE member CHANGE COLUMN profile_image_url profile_image_object_key VARCHAR(512)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_profile_object_key_unique = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'member'
      AND constraint_name = 'uk_member_profile_image_object_key'
);

SET @sql = IF(
    @has_profile_object_key_unique = 0,
    'ALTER TABLE member ADD CONSTRAINT uk_member_profile_image_object_key UNIQUE (profile_image_object_key)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
