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
    consented_scopes      text array,

    mfa_passed_date      timestamp,

    code_challenge        text,
    code_challenge_method text,

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
