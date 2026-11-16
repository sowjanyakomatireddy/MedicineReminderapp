-- Phone numbers for the "Call" buttons (both optional).
ALTER TABLE person ADD COLUMN phone VARCHAR(20);        -- the person's own phone ("Call Thatha")
ALTER TABLE person ADD COLUMN family_phone VARCHAR(20); -- who the person's phone calls ("Call family")

-- One row per dose: TAKEN or MISSED. The id comes from the phone, so uploading the same log twice is harmless.
-- schedule_id + dose_date is unique: one answer per dose (a late TAKEN replaces a MISSED).
CREATE TABLE dose_log (
    id            UUID PRIMARY KEY,
    family_id     UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    person_id     UUID         NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    schedule_id   UUID REFERENCES schedule (id) ON DELETE SET NULL,
    medicine_id   UUID REFERENCES medicine (id) ON DELETE SET NULL,
    medicine_name VARCHAR(100) NOT NULL,   -- kept so history still reads well if the medicine is deleted
    dose_date     DATE         NOT NULL,   -- in the person's time zone
    dose_time     TIME         NOT NULL,
    status        VARCHAR(8)   NOT NULL CHECK (status IN ('TAKEN', 'MISSED')),
    source        VARCHAR(8)   NOT NULL CHECK (source IN ('PHONE', 'FAMILY', 'SERVER')),
    logged_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_dose_log_dose UNIQUE (schedule_id, dose_date)
);
CREATE INDEX ix_dose_log_person_date ON dose_log (person_id, dose_date);
CREATE INDEX ix_dose_log_family_date ON dose_log (family_id, dose_date);

-- Push tokens of family members' browsers/phones (web push through Firebase).
CREATE TABLE user_push_token (
    id           UUID PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token        VARCHAR(1024) NOT NULL UNIQUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_user_push_token_user ON user_push_token (user_id);
