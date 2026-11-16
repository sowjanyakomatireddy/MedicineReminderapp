-- A family groups the people we remind and the family members who get alerts.
CREATE TABLE family (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- A logged-in user's membership. One family per user in this release (user_id is unique).
CREATE TABLE family_member (
    id         UUID PRIMARY KEY,
    family_id  UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    role       VARCHAR(10) NOT NULL CHECK (role IN ('EDITOR', 'VIEWER')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_family_member_family ON family_member (family_id);

-- Someone we remind (e.g. the grandfather). They do not log in.
CREATE TABLE person (
    id             UUID PRIMARY KEY,
    family_id      UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    name           VARCHAR(60) NOT NULL,
    voice_language VARCHAR(10) NOT NULL,
    time_zone      VARCHAR(40) NOT NULL DEFAULT 'Asia/Kolkata',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_person_family ON person (family_id);

-- A phone linked to a person by a pairing code.
CREATE TABLE device (
    id           UUID PRIMARY KEY,
    family_id    UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    person_id    UUID        NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    name         VARCHAR(80),
    platform     VARCHAR(20) NOT NULL,
    fcm_token    VARCHAR(4096),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_sync_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_device_person ON device (person_id);

-- One-time codes. DEVICE codes link a phone to a person; MEMBER codes let a family member join.
-- Only a SHA-256 hash of the code is stored.
CREATE TABLE pairing_code (
    id         UUID PRIMARY KEY,
    family_id  UUID        NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL UNIQUE,
    purpose    VARCHAR(10) NOT NULL CHECK (purpose IN ('DEVICE', 'MEMBER')),
    role       VARCHAR(10) CHECK (role IN ('EDITOR', 'VIEWER')),
    created_by UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_pairing_code_family ON pairing_code (family_id);
