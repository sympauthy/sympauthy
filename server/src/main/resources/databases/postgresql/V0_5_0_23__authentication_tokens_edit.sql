CREATE INDEX authentication_tokens__user_id ON authentication_tokens (user_id) WHERE user_id IS NOT NULL;
