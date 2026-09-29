CREATE TABLE users (
    user_id        UUID                     PRIMARY KEY,
    email          VARCHAR(320)             NOT NULL,
    mobile         VARCHAR(32)              NOT NULL,
    status         VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    client_context TEXT                     NULL
);

CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
CREATE UNIQUE INDEX uk_users_mobile ON users (mobile);
