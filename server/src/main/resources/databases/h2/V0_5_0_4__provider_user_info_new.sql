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
