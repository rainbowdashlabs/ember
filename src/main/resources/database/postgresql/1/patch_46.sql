-- Equipment exchange requests
CREATE TABLE equipment_exchange_request (
    id SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES station(id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    item_id INTEGER REFERENCES inventory_item(id) ON DELETE SET NULL,
    inventory_id INTEGER NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    size_id INTEGER REFERENCES inventory_size(id) ON DELETE SET NULL,
    status TEXT NOT NULL DEFAULT 'ANNOUNCED',
    reason TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_exchange_request_station ON equipment_exchange_request(station_id);
CREATE INDEX idx_exchange_request_member ON equipment_exchange_request(member_id);

-- Exchange status history
CREATE TABLE equipment_exchange_log (
    id SERIAL PRIMARY KEY,
    request_id INTEGER NOT NULL REFERENCES equipment_exchange_request(id) ON DELETE CASCADE,
    old_status TEXT NOT NULL,
    new_status TEXT NOT NULL,
    changed_by INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    changed_at TIMESTAMP NOT NULL DEFAULT now(),
    note TEXT NOT NULL DEFAULT ''
);

-- Equipment procurement requests
CREATE TABLE equipment_procurement (
    id SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES station(id) ON DELETE CASCADE,
    inventory_id INTEGER NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    size_id INTEGER REFERENCES inventory_size(id) ON DELETE SET NULL,
    notes TEXT NOT NULL DEFAULT '',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    fulfilled_at TIMESTAMP
);

CREATE INDEX idx_procurement_station ON equipment_procurement(station_id);

-- News acknowledgements
CREATE TABLE news_acknowledgement (
    news_id INTEGER NOT NULL REFERENCES news(id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY(news_id, member_id)
);

-- User tags
CREATE TABLE user_tag (
    id SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES station(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    UNIQUE(station_id, name)
);

CREATE TABLE user_tag_entry (
    tag_id INTEGER NOT NULL REFERENCES user_tag(id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES station_member(id) ON DELETE CASCADE,
    PRIMARY KEY(tag_id, member_id)
);
