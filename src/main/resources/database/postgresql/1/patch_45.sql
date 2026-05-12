CREATE TABLE ember_schema.news_comment (
    id SERIAL PRIMARY KEY,
    news_id INTEGER NOT NULL REFERENCES ember_schema.news(id) ON DELETE CASCADE,
    parent_id INTEGER REFERENCES ember_schema.news_comment(id) ON DELETE CASCADE,
    author_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_news_comment_news ON ember_schema.news_comment(news_id);
CREATE INDEX idx_news_comment_parent ON ember_schema.news_comment(parent_id);
