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
