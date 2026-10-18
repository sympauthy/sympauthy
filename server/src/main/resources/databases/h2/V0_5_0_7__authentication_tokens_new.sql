CREATE TABLE authentication_tokens
(
    id              uuid      NOT NULL DEFAULT random_uuid(),
    type            text      NOT NULL,
    grant_id        uuid      NOT NULL,
    grant_type      text      NOT NULL,
    dpop_jkt        text,
    issue_date      timestamp NOT NULL,
    expiration_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (grant_id) REFERENCES grants (id)
);

CREATE INDEX authentication_tokens__grant_id_expiration_date ON authentication_tokens (grant_id, expiration_date);
CREATE INDEX authentication_tokens__expiration_date ON authentication_tokens (expiration_date);
