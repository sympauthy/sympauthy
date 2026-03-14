ALTER TABLE authentication_tokens ADD COLUMN revoked_at    timestamp;
ALTER TABLE authentication_tokens ADD COLUMN revoked_by    text;
ALTER TABLE authentication_tokens ADD COLUMN revoked_by_id uuid;

-- Migrate existing revoked tokens: set revoked_at to issue_date as best approximation
UPDATE authentication_tokens SET revoked_at = issue_date WHERE revoked = true;

ALTER TABLE authentication_tokens DROP COLUMN revoked;
