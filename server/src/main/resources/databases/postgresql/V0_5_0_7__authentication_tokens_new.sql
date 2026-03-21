CREATE TABLE authentication_tokens
(
    id                   uuid      NOT NULL DEFAULT gen_random_uuid(),
    type                 text      NOT NULL,
    user_id              uuid,
    client_id            text      NOT NULL,
    granted_scopes       text[]    NOT NULL,
    granted_at           timestamp,
    granted_by           text,
    consented_scopes     text[]    NOT NULL DEFAULT '{}',
    consented_at         timestamp,
    consented_by         text,
    client_scopes        text[]    NOT NULL DEFAULT '{}',
    authorize_attempt_id uuid,
    grant_type           text      NOT NULL,

    revoked_at           timestamp,
    revoked_by           text,
    revoked_by_id        uuid,
    issue_date           timestamp NOT NULL,
    expiration_date      timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);
