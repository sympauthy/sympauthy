CREATE TABLE authorize_attempts
(
    id                               uuid      NOT NULL DEFAULT random_uuid(),
    attempt_date                     timestamp NOT NULL,
    authorization_flow_id            text,
    expiration_date                  timestamp NOT NULL,

    client_id                        text,
    redirect_uri                     text,
    requested_scopes                 text array,
    state                            text,
    nonce                            text,
    code_challenge                   text,
    code_challenge_method            text,

    provider_id                      text,
    provider_nonce_json_web_token_id uuid,

    invitation_id                    uuid,

    user_id                          uuid,

    consented_scopes                 text array,
    consented_at                     timestamp,
    consented_by                     text,

    mfa_passed_date                  timestamp,

    granted_scopes                   text array,
    granted_at                       timestamp,
    granted_by                       text,
    complete_date                    timestamp,

    error_date                       timestamp,
    error_details_id                 text,
    error_description_id             text,
    error_values                     json,

    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX authorize_attempts__state ON authorize_attempts (state);
