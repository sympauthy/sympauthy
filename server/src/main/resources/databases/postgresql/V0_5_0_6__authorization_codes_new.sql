CREATE TABLE authorization_codes
(
    session_id      uuid      NOT NULL,
    code            text      NOT NULL,
    creation_date   timestamp NOT NULL,
    expiration_date timestamp NOT NULL,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);

CREATE INDEX authorization_codes__code ON authorization_codes (code);
