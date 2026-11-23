CREATE TABLE queue_items
(
    id                    uuid      NOT NULL DEFAULT random_uuid(),
    destination           text      NOT NULL,
    kind                  text      NOT NULL,
    payload               json      NOT NULL,
    creation_date         timestamp NOT NULL,
    expiration_date       timestamp,
    attempt_count         integer   NOT NULL DEFAULT 0,
    next_attempt_date     timestamp NOT NULL,
    holder                text,
    lease_expiration_date timestamp,
    PRIMARY KEY (id)
);

CREATE INDEX queue_items__next_attempt_date ON queue_items (next_attempt_date);
CREATE INDEX queue_items__holder ON queue_items (holder);
