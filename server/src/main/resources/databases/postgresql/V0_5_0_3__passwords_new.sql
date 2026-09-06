CREATE TABLE passwords
(
    id              uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id         uuid      NOT NULL,

    salt            bytea     NOT NULL,
    hashed_password bytea     NOT NULL,

    creation_date   timestamp NOT NULL,
    expiration_date timestamp,
    session_id      uuid,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX passwords__user_id ON passwords (user_id);
CREATE INDEX passwords__session_id ON passwords (session_id) WHERE session_id IS NOT NULL;
