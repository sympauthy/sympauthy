CREATE TABLE mail_queue (
    id              uuid      NOT NULL DEFAULT gen_random_uuid(),
    template        text      NOT NULL,
    locale          text      NOT NULL,
    receiver        text      NOT NULL,
    subject_key     text      NOT NULL,
    parameters      json      NOT NULL,
    creation_date   timestamp NOT NULL,
    expiration_date timestamp,
    PRIMARY KEY (id)
);
