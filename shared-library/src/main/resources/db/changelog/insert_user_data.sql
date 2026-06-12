CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

INSERT INTO users (
  id,
  first_name,
  last_name,
  email,
  password,
  contact_number,
  photo_url,
  employment_type,
  is_status_locked,
  created_at,
  created_by,
  updated_at,
  updated_by,
  status,
  is_deleted,
  profile_update_request_status,
  role_id,
  language_id,
  restaurant_id,
  must_change_password
) VALUES (
  uuid_generate_v4(),                -- id
  'HQ',                              -- first_name
  'Admin',                           -- last_name
  'hqadmin@chain.com',               -- email
  'admin123',                        -- password (replace with hashed password in production)
  '1234567890',                      -- contact_number
  'https://example.com/photo.png',   -- photo_url
  'FULL_TIME',                       -- employment_type (adjust as needed)
  FALSE,                             -- is_status_locked
  CURRENT_TIMESTAMP,                 -- created_at
  NULL,                              -- created_by (set to another user UUID if needed)
  CURRENT_TIMESTAMP,                 -- updated_at
  NULL,                              -- updated_by (set to another user UUID if needed)
  'ACTIVE',                          -- status (adjust as needed)
  FALSE,                             -- is_deleted
  NULL,                              -- profile_update_request_status (set to a valid enum if needed)
  (SELECT id FROM role WHERE name = 'HQ_ADMIN'),      -- role_id
  (SELECT id FROM language WHERE code = 'en'),        -- language_id
  NULL,                              -- restaurant_id (set to a restaurant UUID if needed)
  TRUE                               -- must_change_password
);