CREATE TABLE user_security_contexts
(
    id                uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id           uuid      NOT NULL,
    fingerprint       text      NOT NULL,
    ip                text      NOT NULL,
    user_agent        text,
    country_code      text,
    region_code       text,
    region            text,
    city              text,
    time_zone         text,
    first_seen_date   timestamp NOT NULL,
    last_seen_date    timestamp NOT NULL,
    observation_count integer   NOT NULL DEFAULT 1,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX user_security_contexts__last_seen_date ON user_security_contexts (last_seen_date);
CREATE UNIQUE INDEX user_security_contexts__user_id__fingerprint
    ON user_security_contexts (user_id, fingerprint);
