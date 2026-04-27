CREATE TABLE consents
(
    id            uuid      NOT NULL DEFAULT random_uuid(),
    user_id       uuid      NOT NULL,
    audience_id   text      NOT NULL,
    prompted_by_client_id     text      NOT NULL,
    scopes        text ARRAY NOT NULL,
    consented_at  timestamp NOT NULL,
    revoked_at    timestamp,
    revoked_by    text,
    revoked_by_id uuid,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX consents__user_id ON consents (user_id);
CREATE INDEX consents__audience_id ON consents (audience_id);
