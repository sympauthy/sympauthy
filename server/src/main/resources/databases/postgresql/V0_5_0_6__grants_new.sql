CREATE TABLE grants
(
    id                        uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id                   uuid,
    client_id                 text      NOT NULL,
    audience_id               text      NOT NULL,
    grant_type                text      NOT NULL,
    granted_scopes            text[]    NOT NULL,
    granted_at                timestamp,
    granted_by                text,
    consented_scopes          text[]    NOT NULL DEFAULT '{}',
    consented_at              timestamp,
    consented_by              text,
    client_scopes             text[]    NOT NULL DEFAULT '{}',
    authentication_date       timestamp,

    requested_id_token_claims text[]    NOT NULL DEFAULT '{}',
    requested_userinfo_claims text[]    NOT NULL DEFAULT '{}',

    session_id                uuid,
    actor_grant_id            uuid,

    creation_date             timestamp NOT NULL,
    expiration_date           timestamp,
    revoked_at                timestamp,
    revoked_by                text,
    revoked_by_id             uuid,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX grants__user_id ON grants (user_id);
CREATE UNIQUE INDEX grants__session_id ON grants (session_id);
CREATE INDEX grants__actor_grant_id ON grants (actor_grant_id);
