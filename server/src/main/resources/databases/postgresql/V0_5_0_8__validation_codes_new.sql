CREATE TABLE validation_codes
(
    id              uuid      NOT NULL DEFAULT gen_random_uuid(),
    code            text      NOT NULL,

    user_id         uuid      NOT NULL,
    session_id      uuid      NOT NULL,
    media           text      NOT NULL,
    reasons         text[]    NOT NULL,

    creation_date   timestamp NOT NULL,
    resend_date     timestamp,
    validation_date timestamp,
    expiration_date timestamp NOT NULL,

    PRIMARY KEY (id),
    UNIQUE (session_id, code),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);

CREATE INDEX validation_codes__session_id ON validation_codes (session_id);
