CREATE TABLE interactive_flow_sessions
(
    id                   uuid      NOT NULL DEFAULT gen_random_uuid(),
    purposes             text[]    NOT NULL,
    session_date         timestamp NOT NULL,
    flow_id              text,
    expiration_date      timestamp NOT NULL,

    user_id              uuid,
    signed_up            boolean   NOT NULL DEFAULT false,

    mfa_passed_date      timestamp,

    completed_purposes   text[]    NOT NULL DEFAULT '{}',
    complete_date        timestamp,

    error_date           timestamp,
    error_details_id     text,
    error_description_id text,
    error_values         json,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);
