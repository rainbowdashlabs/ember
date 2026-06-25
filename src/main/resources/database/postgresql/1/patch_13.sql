-- Discovery chain (cross-instance gossip + station listing)

-- Peers we have heard of via gossip, bootstrap, or manual admin add.
CREATE TABLE ember_schema.discovery_peer (
    public_key      TEXT PRIMARY KEY,
    base_url        TEXT        NOT NULL,
    instance_id     TEXT        NOT NULL,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_pinged_at  TIMESTAMPTZ,
    last_reached_at TIMESTAMPTZ,
    reachable       BOOLEAN     NOT NULL DEFAULT true,
    source          TEXT        NOT NULL,
    introduced_by   TEXT,
    reputation      INTEGER     NOT NULL DEFAULT 0,
    blocked         BOOLEAN     NOT NULL DEFAULT false
);
CREATE INDEX discovery_peer_last_seen_idx ON ember_schema.discovery_peer (last_seen_at DESC);
CREATE INDEX discovery_peer_reachable_idx ON ember_schema.discovery_peer (reachable, blocked);

COMMENT ON TABLE ember_schema.discovery_peer
    IS 'Known Ember instances learned via gossip, federation bootstrap, or manual admin add.';
COMMENT ON COLUMN ember_schema.discovery_peer.source
    IS 'Origin of this peer record: BOOTSTRAP | GOSSIP | MANUAL.';
COMMENT ON COLUMN ember_schema.discovery_peer.introduced_by
    IS 'Public key of the peer that told us about this one; NULL for BOOTSTRAP and MANUAL.';
COMMENT ON COLUMN ember_schema.discovery_peer.reputation
    IS 'Soft score driving back-off and decay; negative values throttle outbound pings.';

-- In-flight ping nonces for replay protection and callback correlation.
CREATE TABLE ember_schema.discovery_ping (
    nonce      TEXT PRIMARY KEY,
    direction  TEXT        NOT NULL,
    peer_key   TEXT,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX discovery_ping_expires_idx ON ember_schema.discovery_ping (expires_at);

COMMENT ON COLUMN ember_schema.discovery_ping.direction
    IS 'OUT for pings we sent, IN for pings we received (used for nonce reuse detection).';

-- Cached public station listings retrieved from peers.
CREATE TABLE ember_schema.discovery_station_cache (
    instance_public_key TEXT        NOT NULL,
    station_uid         TEXT        NOT NULL,
    payload             JSONB       NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instance_public_key, station_uid),
    FOREIGN KEY (instance_public_key)
        REFERENCES ember_schema.discovery_peer (public_key)
        ON DELETE CASCADE
);
CREATE INDEX discovery_station_cache_fetched_idx ON ember_schema.discovery_station_cache (fetched_at DESC);

COMMENT ON COLUMN ember_schema.discovery_station_cache.payload
    IS 'The station card JSON exactly as returned by the peer''s /public/discovery/stations endpoint.';

-- Admin-managed blocklist of base URLs / public keys we refuse to interact with.
CREATE TABLE ember_schema.discovery_blocklist (
    value      TEXT PRIMARY KEY,
    kind       TEXT        NOT NULL,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN ember_schema.discovery_blocklist.kind
    IS 'BASE_URL or PUBLIC_KEY — controls which side of the peer identity is matched.';
