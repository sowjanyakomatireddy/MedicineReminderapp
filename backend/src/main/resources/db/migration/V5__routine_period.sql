-- Daily routine: calm spoken reminders (drink water, walk, lunch...). No taken/missed tracking.
CREATE TABLE routine_item (
    id           UUID PRIMARY KEY,
    family_id    UUID         NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    person_id    UUID         NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    time_of_day  TIME         NOT NULL,                  -- local time in the person's time zone
    days_of_week SMALLINT     NOT NULL CHECK (days_of_week BETWEEN 1 AND 127), -- Monday = 1 ... Sunday = 64
    label        VARCHAR(100) NOT NULL,                  -- for the family, e.g. "Drink a glass of water"
    spoken_text  VARCHAR(200),                           -- what the voice says, in the person's language
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_routine_item_person ON routine_item (person_id);

-- Period reminders: off unless the family turns them on for a person.
ALTER TABLE person ADD COLUMN period_tracking BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE person ADD COLUMN cycle_length_days SMALLINT NOT NULL DEFAULT 28;  -- used until enough dates are recorded
ALTER TABLE person ADD COLUMN period_length_days SMALLINT NOT NULL DEFAULT 5;
ALTER TABLE person ADD COLUMN period_remind_days SMALLINT NOT NULL DEFAULT 2;  -- spoken reminder N days before
ALTER TABLE person ADD COLUMN period_remind_time TIME NOT NULL DEFAULT '09:00';
ALTER TABLE person ADD CONSTRAINT ck_person_cycle_length CHECK (cycle_length_days BETWEEN 15 AND 60);
ALTER TABLE person ADD CONSTRAINT ck_person_period_length CHECK (period_length_days BETWEEN 1 AND 10);
ALTER TABLE person ADD CONSTRAINT ck_person_period_remind CHECK (period_remind_days BETWEEN 0 AND 10);

-- Recorded period start dates ("Period started today").
CREATE TABLE period_cycle (
    id         UUID PRIMARY KEY,
    family_id  UUID NOT NULL REFERENCES family (id) ON DELETE CASCADE,
    person_id  UUID NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_period_cycle_start UNIQUE (person_id, start_date)
);
