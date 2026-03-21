CREATE TABLE totp_enrollments
(
    id             uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id        uuid      NOT NULL,
    secret         bytea     NOT NULL,
    creation_date  timestamp NOT NULL,
    confirmed_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX totp_enrollments__user_id ON totp_enrollments (user_id);
