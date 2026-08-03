CREATE TABLE interactive_flow_session_reauthentication
(
    session_id                     uuid NOT NULL,
    primary_credential_proven_date timestamp,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
