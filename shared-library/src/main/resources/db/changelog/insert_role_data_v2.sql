CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

INSERT INTO role (id, name) VALUES
  (uuid_generate_v4(), 'HQ_ADMIN'),
  (uuid_generate_v4(), 'MANAGER'),
  (uuid_generate_v4(), 'WAITER'),
  (uuid_generate_v4(), 'CASHIER'),
  (uuid_generate_v4(), 'KDS')
ON CONFLICT (name) DO NOTHING;
