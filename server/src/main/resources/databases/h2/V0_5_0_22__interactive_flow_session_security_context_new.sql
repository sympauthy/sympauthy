CREATE TABLE interactive_flow_session_security_context
(
    session_id    uuid      NOT NULL,
    fingerprint   text      NOT NULL,
    ip            text      NOT NULL,
    user_agent    text,
    country_code  text,
    region_code   text,
    region        text,
    city          text,
    time_zone     text,
    observed_date timestamp NOT NULL,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
