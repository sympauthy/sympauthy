CREATE TABLE authorize_attempts
(
    id                    uuid      NOT NULL DEFAULT gen_random_uuid(),
    attempt_date         timestamp NOT NULL,

    client_id        text,
    redirect_uri     text,
    requested_scopes text[],
    state                 text,
    nonce                 text,

    authorization_flow_id text,

    user_id               uuid,
    granted_scopes        text[],
    consented_scopes      text[],

    mfa_passed_date      timestamp,

    code_challenge        text,
    code_challenge_method text,

    error_date           timestamp,
    error_details_id     text,
    error_description_id text,
    error_values         json,

    complete_date        timestamp,
    expiration_date       timestamp NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX authorize_attempts__state ON authorize_attempts (state);
