-- User-related tables

CREATE TABLE users
(
    id            uuid      NOT NULL DEFAULT random_uuid(),
    status        text      NOT NULL,
    creation_date timestamp NOT NULL,

    PRIMARY KEY (id)
);

CREATE TABLE collected_claims
(
    id                uuid      NOT NULL DEFAULT random_uuid(),
    user_id           uuid      NOT NULL,
    collection_date   timestamp NOT NULL,
    claim             text      NOT NULL,
    `value`           text,
    verified          boolean,
    verification_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    UNIQUE (user_id, claim)
);

CREATE INDEX collected_user_info__user_id ON collected_claims (user_id);
CREATE INDEX collected_user_info__login_claims ON collected_claims (claim, `value`);

CREATE TABLE passwords
(
    id              uuid      NOT NULL DEFAULT random_uuid(),
    user_id         uuid      NOT NULL,

    salt            bytea     NOT NULL,
    hashed_password bytea     NOT NULL,

    creation_date   timestamp NOT NULL,
    expiration_date timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX passwords__user_id ON passwords (user_id);

-- Provider-related tables

CREATE TABLE provider_user_info
(
    provider_id           text      NOT NULL,
    user_id               uuid      NOT NULL,
    fetch_date            timestamp NOT NULL,
    change_date           timestamp NOT NULL,

    subject               text      NOT NULL,

    name                  text,
    given_name            text,
    family_name           text,
    middle_name           text,
    nickname              text,

    preferred_username    text,
    profile               text,
    picture               text,
    website               text,

    email                 text,
    email_verified        boolean,

    gender                text,
    birth_date            date,

    zone_info             text,
    locale                text,

    phone_number          text,
    phone_number_verified boolean,

    updated_at            timestamp,

    PRIMARY KEY (provider_id, user_id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX provider_user_info__user_id ON provider_user_info (user_id);

-- Auth table

CREATE TABLE authorize_attempts
(
    id               uuid      NOT NULL DEFAULT random_uuid(),
    attempt_date         timestamp NOT NULL,

    client_id        text,
    redirect_uri     text,
    requested_scopes text array,
    state                 text,
    nonce                 text,

    authorization_flow_id text,

    user_id               uuid,
    granted_scopes        text array,

    error_date           timestamp,
    error_details_id     text,
    error_description_id text,
    error_values         json,

    complete_date        timestamp,
    expiration_date  timestamp NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX authorize_attempts__state ON authorize_attempts (state);

CREATE TABLE authorization_codes
(
    attempt_id      uuid      NOT NULL,
    code            text      NOT NULL,
    creation_date   timestamp NOT NULL,
    expiration_date timestamp NOT NULL,

    PRIMARY KEY (attempt_id),
    FOREIGN KEY (attempt_id) REFERENCES authorize_attempts (id)
);

CREATE INDEX authorization_codes__code ON authorization_codes (code);

CREATE TABLE authentication_tokens
(
    id                   uuid       NOT NULL DEFAULT random_uuid(),
    type                 text       NOT NULL,
    user_id              uuid,
    client_id            text       NOT NULL,
    scopes               text array NOT NULL,
    authorize_attempt_id uuid,
    grant_type           text       NOT NULL,

    revoked              boolean    NOT NULL,
    issue_date           timestamp  NOT NULL,
    expiration_date      timestamp,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Validation codes

CREATE TABLE validation_codes
(
    id              uuid       NOT NULL DEFAULT random_uuid(),
    code            text       NOT NULL,

    user_id         uuid       NOT NULL,
    attempt_id      uuid       NOT NULL,
    media           text       NOT NULL,
    reasons         text array NOT NULL,

    creation_date   timestamp  NOT NULL,
    resend_date     timestamp,
    validation_date timestamp,
    expiration_date timestamp  NOT NULL,

    PRIMARY KEY (id),
    UNIQUE (attempt_id, code),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (attempt_id) REFERENCES authorize_attempts (id)
);

CREATE INDEX validation_codes__attempt_id ON validation_codes (attempt_id);

-- Cryptographic keys table

CREATE TABLE crypto_keys
(
    name               text      NOT NULL,
    algorithm          text      NOT NULL,

    public_key         bytea,
    public_key_format  text,

    private_key        bytea     NOT NULL,
    private_key_format text      NOT NULL,

    creation_date      timestamp NOT NULL,

    PRIMARY KEY (name)
);

CREATE TABLE indexed_crypto_keys
(
    name               text      NOT NULL,
    index              int       NOT NULL AUTO_INCREMENT,
    algorithm          text      NOT NULL,

    public_key         bytea,
    public_key_format  text,

    private_key        bytea     NOT NULL,
    private_key_format text      NOT NULL,

    creation_date      timestamp NOT NULL,

    PRIMARY KEY (name, index)
);

CREATE INDEX indexed_crypto_keys__name ON indexed_crypto_keys (name);
