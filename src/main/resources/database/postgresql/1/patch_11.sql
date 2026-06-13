-- Track when station events were last modified so the personal iCal feed can compute a
-- stable ETag and answer conditional GETs (If-None-Match / If-Modified-Since) with 304
-- instead of re-rendering every poll.

ALTER TABLE ember_schema.station_event
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

COMMENT ON COLUMN ember_schema.station_event.updated_at IS 'When the event was last modified. Used as a freshness signal for cached feed renders.';

-- Persisted observability for the personal feed endpoints. Two tables:
--   feed_metric_daily — one row per (day, type, status) with a count, total/average duration,
--                       total entries rendered, and a fixed-bucket histogram so the admin
--                       dashboard can chart "is the feed slow?" without a true time-series DB.
--   feed_user_agent_stat — global aggregate of feed reader User-Agents. Globally aggregated,
--                          never per-token, so a station admin with DB access cannot use it to
--                          fingerprint individual members by which RSS reader they use.

CREATE TABLE ember_schema.feed_metric_daily (
    day               DATE    NOT NULL,
    type              TEXT    NOT NULL,
    status            INTEGER NOT NULL,
    count             BIGINT  NOT NULL DEFAULT 0,
    total_duration_ms BIGINT  NOT NULL DEFAULT 0,
    total_entries     BIGINT  NOT NULL DEFAULT 0,
    bucket_lt_50      BIGINT  NOT NULL DEFAULT 0,
    bucket_lt_200     BIGINT  NOT NULL DEFAULT 0,
    bucket_lt_1000    BIGINT  NOT NULL DEFAULT 0,
    bucket_lt_5000    BIGINT  NOT NULL DEFAULT 0,
    bucket_gte_5000   BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (day, type, status)
);

CREATE INDEX idx_feed_metric_daily_day ON ember_schema.feed_metric_daily (day DESC);

COMMENT ON TABLE ember_schema.feed_metric_daily IS 'Daily rollup of feed render volume, duration histogram, and entry counts. Pruned by configurable retention.';
COMMENT ON COLUMN ember_schema.feed_metric_daily.type IS 'Feed kind: ics, rss, atom.';
COMMENT ON COLUMN ember_schema.feed_metric_daily.status IS 'HTTP status code of the response (200, 304, 429, 500, …).';
COMMENT ON COLUMN ember_schema.feed_metric_daily.total_entries IS 'Sum of entries rendered across the requests in this row.';

CREATE TABLE ember_schema.feed_user_agent_stat (
    ua_hash       CHAR(16)                 PRIMARY KEY,
    ua_string     TEXT                     NOT NULL,
    request_count BIGINT                   NOT NULL DEFAULT 0,
    first_seen    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_seen     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_feed_user_agent_stat_last_seen ON ember_schema.feed_user_agent_stat (last_seen DESC);

COMMENT ON TABLE ember_schema.feed_user_agent_stat IS 'Global aggregate of feed reader User-Agents. No per-token attribution by design.';
COMMENT ON COLUMN ember_schema.feed_user_agent_stat.ua_hash IS 'First 8 bytes of SHA-256(ua_string) as 16-char hex.';
COMMENT ON COLUMN ember_schema.feed_user_agent_stat.ua_string IS 'Verbatim User-Agent header (truncated by the application before insert).';

-- Date-aware event comments for recurring events. Comments on one-time events keep
-- event_date = NULL; comments on a specific occurrence of a recurring event carry the
-- ISO date so threads stay scoped to the right week/month/etc. instead of merging across
-- every occurrence.
ALTER TABLE ember_schema.event_comment
    ADD COLUMN event_date DATE;

CREATE INDEX idx_event_comment_event_date ON ember_schema.event_comment (event_id, event_date);

COMMENT ON COLUMN ember_schema.event_comment.event_date IS 'For comments on a specific occurrence of a recurring event. NULL for one-time events or for whole-event-level comments.';
