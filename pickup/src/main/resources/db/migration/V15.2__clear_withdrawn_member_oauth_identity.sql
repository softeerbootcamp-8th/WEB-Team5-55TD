UPDATE member
SET oauth_provider = NULL,
    oauth_subject = NULL,
    external_profile_image_url = NULL
WHERE status = 'WITHDRAWN';
