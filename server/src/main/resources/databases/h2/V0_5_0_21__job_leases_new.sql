CREATE TABLE job_leases
(
    name            text      NOT NULL,
    holder          text,
    acquired_at     timestamp,
    expiration_date timestamp NOT NULL,

    PRIMARY KEY (name)
);

INSERT INTO job_leases (name, expiration_date)
VALUES ('clean_expired_interactive_flow_sessions', TIMESTAMP '1970-01-01 00:00:00'),
       ('clean_abandoned_accounts', TIMESTAMP '1970-01-01 00:00:00');
