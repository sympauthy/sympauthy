-- Add consented_scopes column to authorize_attempts
-- granted_scopes now only contains grantable scopes, consented_scopes contains consentable scopes.
ALTER TABLE authorize_attempts ADD COLUMN consented_scopes text[];

-- Rename scopes to granted_scopes and add consented_scopes/client_scopes to authentication_tokens
-- granted_scopes: grantable scopes, consented_scopes: consentable scopes, client_scopes: client_credentials scopes.
ALTER TABLE authentication_tokens RENAME COLUMN scopes TO granted_scopes;
ALTER TABLE authentication_tokens ADD COLUMN consented_scopes text[] NOT NULL DEFAULT '{}';
ALTER TABLE authentication_tokens ADD COLUMN client_scopes text[] NOT NULL DEFAULT '{}';
