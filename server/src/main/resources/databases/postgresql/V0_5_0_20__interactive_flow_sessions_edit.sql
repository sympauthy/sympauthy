CREATE INDEX interactive_flow_sessions__expiration_date ON interactive_flow_sessions (expiration_date);

CREATE INDEX interactive_flow_sessions__user_id ON interactive_flow_sessions (user_id) WHERE user_id IS NOT NULL;
