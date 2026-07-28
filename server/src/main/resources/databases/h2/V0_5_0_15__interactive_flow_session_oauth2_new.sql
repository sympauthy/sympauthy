CREATE TABLE interactive_flow_session_oauth2
(
    session_id            uuid NOT NULL,

    client_id             text,
    redirect_uri          text,
    requested_scopes      text array,
    state                 text,
    nonce                 text,
    code_challenge        text,
    code_challenge_method text,

    invitation_id         uuid,

    consented_scopes      text array,
    consented_at          timestamp,
    consented_by          text,

    granted_scopes        text array,
    granted_at            timestamp,
    granted_by            text,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);

CREATE INDEX interactive_flow_session_oauth2__state ON interactive_flow_session_oauth2 (state);
