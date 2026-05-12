INSERT INTO ember_schema.role (name) VALUES ('NEWS_MANAGEMENT') ON CONFLICT DO NOTHING;

CREATE TABLE ember_schema.news (
    id SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content_markdown TEXT NOT NULL,
    content_html TEXT NOT NULL,
    author_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE ember_schema.news_group_restriction (
    news_id INTEGER NOT NULL REFERENCES ember_schema.news(id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    PRIMARY KEY(news_id, group_id)
);

CREATE TABLE ember_schema.user_settings (
    member_id INTEGER PRIMARY KEY REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    notify_news BOOLEAN NOT NULL DEFAULT TRUE,
    notify_new_events BOOLEAN NOT NULL DEFAULT TRUE,
    notify_event_status BOOLEAN NOT NULL DEFAULT TRUE
);
