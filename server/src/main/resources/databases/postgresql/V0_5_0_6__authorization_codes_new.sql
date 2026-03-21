CREATE TABLE authorization_codes
(
    attempt_id      uuid      NOT NULL,
    code            text      NOT NULL,
    creation_date   timestamp NOT NULL,
    expiration_date timestamp NOT NULL,

    PRIMARY KEY (attempt_id),
    FOREIGN KEY (attempt_id) REFERENCES authorize_attempts (id)
);

CREATE INDEX authorization_codes__code ON authorization_codes (code);
