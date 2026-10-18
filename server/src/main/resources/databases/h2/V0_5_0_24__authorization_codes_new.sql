CREATE TABLE authorization_codes
(
    session_id      uuid      NOT NULL,
    code            text      NOT NULL,
    creation_date   timestamp NOT NULL,
    expiration_date timestamp NOT NULL,
    exchange_date   timestamp,
    grant_id        uuid      NOT NULL,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id),
    FOREIGN KEY (grant_id) REFERENCES grants (id)
);

CREATE INDEX authorization_codes__code ON authorization_codes (code);
CREATE INDEX authorization_codes__grant_id ON authorization_codes (grant_id);
