-- Family members who log in to the family app.
CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    app_language  VARCHAR(10)  NOT NULL DEFAULT 'en',
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
