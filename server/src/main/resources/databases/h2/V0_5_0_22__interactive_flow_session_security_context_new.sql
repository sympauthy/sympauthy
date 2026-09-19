CREATE TABLE interactive_flow_session_security_context
(
    id                uuid      NOT NULL DEFAULT random_uuid(),
    session_id        uuid      NOT NULL,
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
    proven_date       timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);

CREATE UNIQUE INDEX interactive_flow_session_security_context__session_id__fingerprint
    ON interactive_flow_session_security_context (session_id, fingerprint);
