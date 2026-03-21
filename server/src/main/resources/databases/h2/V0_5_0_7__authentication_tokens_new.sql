CREATE TABLE authentication_tokens
(
    id                   uuid       NOT NULL DEFAULT random_uuid(),
    type                 text       NOT NULL,
    user_id              uuid,
    client_id            text       NOT NULL,
    granted_scopes       text array NOT NULL,
    consented_scopes     text array NOT NULL DEFAULT ARRAY[],
    client_scopes        text array NOT NULL DEFAULT ARRAY[],
    authorize_attempt_id uuid,
    grant_type           text       NOT NULL,

    revoked_at           timestamp,
    revoked_by           text,
    revoked_by_id        uuid,
    issue_date           timestamp  NOT NULL,
    expiration_date      timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);
