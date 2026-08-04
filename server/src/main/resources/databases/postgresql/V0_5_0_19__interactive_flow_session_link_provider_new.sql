CREATE TABLE interactive_flow_session_link_provider
(
    session_id  uuid NOT NULL,
    provider_id text NOT NULL,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
