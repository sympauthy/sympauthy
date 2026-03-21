CREATE TABLE users
(
    id            uuid      NOT NULL DEFAULT random_uuid(),
    status        text      NOT NULL,
    creation_date timestamp NOT NULL,

    PRIMARY KEY (id)
);
