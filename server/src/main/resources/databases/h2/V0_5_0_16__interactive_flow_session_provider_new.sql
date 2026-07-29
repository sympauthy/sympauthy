CREATE TABLE interactive_flow_session_provider
(
    session_id                       uuid NOT NULL,
    provider_id                      text NOT NULL,
    provider_nonce_json_web_token_id uuid,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
