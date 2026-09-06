CREATE TABLE security_contexts
(
    id                uuid      NOT NULL DEFAULT random_uuid(),
    user_id           uuid,
    fingerprint       text      NOT NULL,
    ip                text,
    user_agent        text,
    country           text,
    region            text,
    city              text,
    first_seen_date   timestamp NOT NULL,
    last_seen_date    timestamp NOT NULL,
    observation_count integer   NOT NULL DEFAULT 1,
    expiration_date   timestamp NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX security_contexts__user_id ON security_contexts (user_id);
CREATE INDEX security_contexts__expiration_date ON security_contexts (expiration_date);
