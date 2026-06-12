-- Track when station events were last modified so the personal iCal feed can compute a
-- stable ETag and answer conditional GETs (If-None-Match / If-Modified-Since) with 304
-- instead of re-rendering every poll.

ALTER TABLE ember_schema.station_event
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

COMMENT ON COLUMN ember_schema.station_event.updated_at IS 'When the event was last modified. Used as a freshness signal for cached feed renders.';
