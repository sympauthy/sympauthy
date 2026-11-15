CREATE TABLE interactive_flow_session_password_reset
(
    session_id           uuid      NOT NULL,
    login                text      NOT NULL,
    user_id              uuid,
    requested_date       timestamp NOT NULL,
    code_proven_date     timestamp,

    salt                 bytea     NOT NULL,
    hashed_password      bytea     NOT NULL,
    password_chosen_date timestamp,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
