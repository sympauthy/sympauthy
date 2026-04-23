CREATE TABLE consents
(
    id            uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id       uuid      NOT NULL,
    audience_id   text      NOT NULL,
    client_id     text      NOT NULL,
    scopes        text[]    NOT NULL,
    consented_at  timestamp NOT NULL,
    revoked_at    timestamp,
    revoked_by    text,
    revoked_by_id uuid,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX consents__user_id ON consents (user_id);
CREATE INDEX consents__audience_id ON consents (audience_id);
CREATE INDEX consents__client_id ON consents (client_id);
CREATE UNIQUE INDEX consents__active_user_audience ON consents (user_id, audience_id) WHERE revoked_at IS NULL;
