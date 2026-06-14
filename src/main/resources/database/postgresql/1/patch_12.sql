-- Rename ExchangeStatus.EXCHANGED -> DONE.
-- The status is stored as TEXT in three columns; rewrite every occurrence
-- so existing rows match the new enum constant name.

UPDATE ember_schema.equipment_exchange_request
SET status = 'DONE'
WHERE status = 'EXCHANGED';

UPDATE ember_schema.equipment_exchange_log
SET old_status = 'DONE'
WHERE old_status = 'EXCHANGED';

UPDATE ember_schema.equipment_exchange_log
SET new_status = 'DONE'
WHERE new_status = 'EXCHANGED';

-- News view tracking: each row records that a member fully saw a news entry
-- in their viewport. Distinct from news_acknowledgement (which is a deliberate
-- "I've read this" gesture); news_view is silent telemetry recorded by an
-- IntersectionObserver on the client.
CREATE TABLE ember_schema.news_view
(
    news_id   INTEGER     NOT NULL REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    member_id INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (news_id, member_id)
);
CREATE INDEX idx_news_view_member ON ember_schema.news_view (member_id);

COMMENT ON TABLE ember_schema.news_view IS 'Tracks when a member fully saw a news entry in their viewport (auto-recorded by the client).';
COMMENT ON COLUMN ember_schema.news_view.news_id IS 'References the news article.';
COMMENT ON COLUMN ember_schema.news_view.member_id IS 'References the station member who saw the article.';
COMMENT ON COLUMN ember_schema.news_view.seen_at IS 'When the news first became fully visible in the member''s viewport.';

-- Backfill page permissions: PAGE_EDIT and PAGE_MANAGER exist in the StationPermission enum
-- but were never inserted into station_permission, so they couldn't be granted from the UI.
INSERT INTO ember_schema.station_permission (name) VALUES
    ('PAGE_EDIT'),
    ('PAGE_MANAGER')
ON CONFLICT (name) DO NOTHING;

-- Optional display color for an event category. Shown as the chip background in the calendar
-- view. Stored as a 7-char hex string (#RRGGBB); NULL means "no custom color" — the calendar
-- falls back to the default primary tint.
ALTER TABLE ember_schema.event_category
    ADD COLUMN color TEXT;

COMMENT ON COLUMN ember_schema.event_category.color IS 'Optional display color for the category as a #RRGGBB hex string. Used as the calendar chip background; NULL falls back to the default primary tint.';
