CREATE TABLE reauthentication_attempts
(
    id              uuid      NOT NULL DEFAULT random_uuid(),
    target_user_id  uuid      NOT NULL,
    purpose         text      NOT NULL,
    attempt_date    timestamp NOT NULL,
    expiration_date timestamp NOT NULL,

    passed_date     timestamp,
    passed_method   text,

    PRIMARY KEY (id),
    FOREIGN KEY (target_user_id) REFERENCES users (id)
);

CREATE INDEX reauthentication_attempts__target_user_id ON reauthentication_attempts (target_user_id);
