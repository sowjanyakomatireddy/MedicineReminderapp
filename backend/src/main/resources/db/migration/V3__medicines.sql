-- A medicine for one person. Photos are stored by PhotoStorage; only the key is kept here.
CREATE TABLE medicine (
    id         UUID PRIMARY KEY,
    family_id  UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    person_id  UUID         NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    voice_name VARCHAR(100),
    shape      VARCHAR(10) CHECK (shape IN ('ROUND', 'OVAL', 'CAPSULE', 'OTHER')),
    color      VARCHAR(10) CHECK (color IN ('WHITE', 'YELLOW', 'ORANGE', 'PINK', 'RED', 'BLUE', 'GREEN', 'BROWN', 'MIXED')),
    notes      VARCHAR(500),
    photo_key  VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_medicine_person ON medicine (person_id);
CREATE INDEX ix_medicine_family ON medicine (family_id);

-- When a medicine rings. time_of_day is local time in the person's time zone.
-- days_of_week is a bit mask: Monday = 1, Tuesday = 2, ... Sunday = 64; 127 = every day.
CREATE TABLE schedule (
    id                UUID PRIMARY KEY,
    medicine_id       UUID       NOT NULL REFERENCES medicine (id) ON DELETE CASCADE,
    time_of_day       TIME       NOT NULL,
    days_of_week      SMALLINT   NOT NULL CHECK (days_of_week BETWEEN 1 AND 127),
    food_rule         VARCHAR(6) NOT NULL CHECK (food_rule IN ('BEFORE', 'AFTER', 'ANY')),
    remind_before_min SMALLINT   NOT NULL DEFAULT 5 CHECK (remind_before_min BETWEEN 0 AND 120),
    repeat_every_min  SMALLINT   NOT NULL DEFAULT 5 CHECK (repeat_every_min BETWEEN 1 AND 60),
    stop_after_min    SMALLINT   NOT NULL DEFAULT 30 CHECK (stop_after_min BETWEEN 0 AND 240),
    active            BOOLEAN    NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_schedule_medicine ON schedule (medicine_id);

ALTER TABLE person ADD COLUMN photo_key VARCHAR(100);
