CREATE TABLE passwords
(
    id              uuid      NOT NULL DEFAULT random_uuid(),
    user_id         uuid      NOT NULL,

    salt            bytea     NOT NULL,
    hashed_password bytea     NOT NULL,

    creation_date   timestamp NOT NULL,
    expiration_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX passwords__user_id ON passwords (user_id);
