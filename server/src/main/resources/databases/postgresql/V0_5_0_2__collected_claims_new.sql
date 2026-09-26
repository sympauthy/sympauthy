CREATE TABLE collected_claims
(
    id                uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id           uuid      NOT NULL,
    collection_date   timestamp NOT NULL,
    claim             text      NOT NULL,
    value             text,
    folded_equality_hash bigint,
    verified          boolean,
    verification_date timestamp,
    session_id        uuid,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE (user_id, claim)
);

CREATE INDEX collected_claims__user_id ON collected_claims (user_id);
CREATE INDEX collected_claims__claim_folded_equality_hash__where_committed ON collected_claims (claim, folded_equality_hash) WHERE session_id IS NULL AND (claim = 'preferred_username' OR claim = 'email' OR claim = 'phone_number');
