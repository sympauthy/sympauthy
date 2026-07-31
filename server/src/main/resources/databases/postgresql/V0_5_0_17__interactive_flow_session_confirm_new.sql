CREATE TABLE interactive_flow_session_confirm
(
    session_id     uuid NOT NULL,
    action         text NOT NULL,
    client_id      text,
    confirmed_date timestamp,

    PRIMARY KEY (session_id),
    FOREIGN KEY (session_id) REFERENCES interactive_flow_sessions (id)
);
