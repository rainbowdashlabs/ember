-- Somebody entered in the register as Maximilian is called Max by everybody at the station.
--
-- The name they are called by belongs to the membership rather than to the account: the same person
-- may be Max at one station and Maximilian at another, and the register name stays where it is,
-- untouched, on the account.
--
-- Who wrote it is kept beside it. A member may set their own and a manager may set one for somebody
-- they look after, so a nickname nobody chose for themselves has to be traceable to whoever did.

ALTER TABLE ember_schema.station_member
    ADD COLUMN IF NOT EXISTS nickname            TEXT,
    ADD COLUMN IF NOT EXISTS nickname_set_by     INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS nickname_set_at     TIMESTAMP;

COMMENT ON COLUMN ember_schema.station_member.nickname
    IS 'The name this member is called by at this station, replacing their first name on screen. Null where they have none.';
COMMENT ON COLUMN ember_schema.station_member.nickname_set_by
    IS 'The member who last wrote the nickname, which is the member themselves or one of their managers.';
COMMENT ON COLUMN ember_schema.station_member.nickname_set_at
    IS 'When it was last written.';

-- The comment this column carried described a feature nobody had built. What it actually holds is
-- the name of somebody who has left, frozen at the moment they left, because the account it was
-- read from is detached then and there is nothing left to read afterwards.
COMMENT ON COLUMN ember_schema.station_member.display_name
    IS 'The name of a former member, frozen when they left and no longer readable from any account. Empty for everybody else.';

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS nicknames_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN ember_schema.station.nicknames_enabled
    IS 'Whether this station reads the names its members are called by. Switched off, every nickname is kept but none is read.';
