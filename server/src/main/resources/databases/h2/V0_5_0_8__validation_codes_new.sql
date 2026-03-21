CREATE TABLE validation_codes
(
    id              uuid       NOT NULL DEFAULT random_uuid(),
    code            text       NOT NULL,

    user_id         uuid       NOT NULL,
    attempt_id      uuid       NOT NULL,
    media           text       NOT NULL,
    reasons         text array NOT NULL,

    creation_date   timestamp  NOT NULL,
    resend_date     timestamp,
    validation_date timestamp,
    expiration_date timestamp  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE (attempt_id, code),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (attempt_id) REFERENCES authorize_attempts (id)
);

CREATE INDEX validation_codes__attempt_id ON validation_codes (attempt_id);
