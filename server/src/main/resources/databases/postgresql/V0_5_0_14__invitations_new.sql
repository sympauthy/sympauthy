CREATE TABLE invitations
(
    id                  uuid      NOT NULL DEFAULT gen_random_uuid(),
    audience_id         text      NOT NULL,
    token_lookup_hash   bytea     NOT NULL,
    hashed_token        bytea     NOT NULL,
    salt                bytea     NOT NULL,
    token_prefix        text      NOT NULL,
    claims              json,
    note                text,
    status              text      NOT NULL,
    created_by          text      NOT NULL,
    created_by_id       text,
    consumed_by_user_id uuid,
    created_at          timestamp NOT NULL,
    expires_at          timestamp NOT NULL,
    consumed_at         timestamp,
    revoked_at          timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (consumed_by_user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX invitations__token_lookup_hash ON invitations (token_lookup_hash);
CREATE INDEX invitations__audience_id ON invitations (audience_id);
CREATE INDEX invitations__status ON invitations (status);
