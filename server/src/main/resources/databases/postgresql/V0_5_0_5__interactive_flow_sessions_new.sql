CREATE TABLE interactive_flow_sessions
(
    id                   uuid      NOT NULL DEFAULT gen_random_uuid(),
    purpose              text      NOT NULL,
    session_date         timestamp NOT NULL,
    flow_id              text,
    expiration_date      timestamp NOT NULL,

    user_id              uuid,

    mfa_passed_date      timestamp,

    complete_date        timestamp,

    error_date           timestamp,
    error_details_id     text,
    error_description_id text,
    error_values         json,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);
