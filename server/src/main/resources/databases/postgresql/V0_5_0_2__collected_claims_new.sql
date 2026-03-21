CREATE TABLE collected_claims
(
    id                uuid      NOT NULL DEFAULT gen_random_uuid(),
    user_id           uuid      NOT NULL,
    collection_date   timestamp NOT NULL,
    claim             text      NOT NULL,
    value             text,
    verified          boolean,
    verification_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE (user_id, claim)
);

CREATE INDEX collected_user_info__user_id ON collected_claims (user_id);
CREATE INDEX collected_user_info__login_claims ON collected_claims (claim, value) WHERE claim = 'preferred_username' OR claim = 'email' OR claim = 'phone_number';
