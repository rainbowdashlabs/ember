CREATE TABLE news_comment (
    id SERIAL PRIMARY KEY,
    news_id INTEGER NOT NULL REFERENCES news(id) ON DELETE CASCADE,
    parent_id INTEGER REFERENCES news_comment(id) ON DELETE CASCADE,
    author_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_news_comment_news ON news_comment(news_id);
CREATE INDEX idx_news_comment_parent ON news_comment(parent_id);
