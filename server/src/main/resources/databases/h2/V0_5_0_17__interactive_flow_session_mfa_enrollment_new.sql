CREATE TABLE interactive_flow_session_mfa_enrollment
(
    session_id uuid NOT NULL,
    return_uri text NOT NULL,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
