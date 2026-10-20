CREATE TABLE authorization_codes
(
    grant_id              uuid      NOT NULL,
    code                  text      NOT NULL,
    code_challenge        text      NOT NULL,
    code_challenge_method text      NOT NULL,
    nonce                 text,
    creation_date         timestamp NOT NULL,
    expiration_date       timestamp NOT NULL,
    exchange_date         timestamp,

    PRIMARY KEY (grant_id),
    FOREIGN KEY (grant_id) REFERENCES grants (id)
);

CREATE INDEX authorization_codes__code ON authorization_codes (code);
CREATE INDEX authorization_codes__expiration_date ON authorization_codes (expiration_date);
