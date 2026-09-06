CREATE TABLE users
(
    id            uuid      NOT NULL DEFAULT gen_random_uuid(),
    status        text      NOT NULL,
    creation_date timestamp NOT NULL,
    session_id    uuid,

    PRIMARY KEY (id)
);

CREATE INDEX users__session_id ON users (session_id) WHERE session_id IS NOT NULL;
