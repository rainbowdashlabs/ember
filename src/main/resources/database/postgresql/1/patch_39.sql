-- A news entry that belongs to no station.
--
-- Everything the instance has to say to everyone was, until now, something each station had to be
-- told separately. A system entry is written once and read in every station: one row, so correcting
-- it corrects it everywhere and withdrawing it withdraws it everywhere, and so the comments under
-- it are one conversation the instance can see whole while each station sees its own part of it.
--
-- Dropping the station is all that takes. The entry keeps its own restrictions, its own blocks and
-- its own comments exactly as a station entry does, and the comments already record which station
-- their author wrote from, which is what lets a station be shown its own and nobody else's.
--
-- An entry with no station is not on any station's public blog and does not travel to partner
-- stations, because both of those are read through the station it belongs to and it belongs to
-- none.
ALTER TABLE ember_schema.news
    ALTER COLUMN station_id DROP NOT NULL;

COMMENT ON COLUMN ember_schema.news.station_id IS
    'The station that published the entry. Null for a system entry, which the instance publishes to every station at once.';

-- Reading a station's news reads its own and the system entries together, so the two are one index.
CREATE INDEX idx_news_station_published ON ember_schema.news (station_id, published_at DESC);
