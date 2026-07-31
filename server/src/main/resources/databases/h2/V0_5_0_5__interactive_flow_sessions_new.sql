CREATE TABLE interactive_flow_sessions
(
    id                   uuid      NOT NULL DEFAULT random_uuid(),
    purposes             text array NOT NULL,
    session_date         timestamp NOT NULL,
    flow_id              text,
    expiration_date      timestamp NOT NULL,

    user_id              uuid,
    signed_up            boolean   NOT NULL DEFAULT false,

    mfa_passed_date      timestamp,

    success_redirect_uri text,
    redirect_type        text,
    cancel_redirect_uri  text,

    completed_purposes   text array NOT NULL DEFAULT ARRAY[],
    complete_date        timestamp,

    cancel_date          timestamp,

    error_date           timestamp,
    error_details_id     text,
    error_description_id text,
    error_values         json,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);
