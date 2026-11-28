-- Presence now lives in Redis (shared by all instances, expires automatically when an
-- instance dies). The database column could only go stale, so it is removed.
ALTER TABLE users DROP COLUMN is_online;
