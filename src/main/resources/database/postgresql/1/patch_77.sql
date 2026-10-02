DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, TEXT, INT, INT, TEXT);

CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _restriction_table TEXT,
    _fk_column TEXT,
    _entity_table TEXT,
    _entity_id_column TEXT,
    _mode_column TEXT,
    _entity_id INT,
    _member_id INT
) RETURNS BOOLEAN
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    _mode      TEXT;
    _user_type TEXT;
    _group_ids INT[];
    _tag_ids   INT[];
BEGIN
    EXECUTE format('SELECT %I FROM %I WHERE %I = $1', _mode_column, _entity_table, _entity_id_column)
        INTO _mode USING _entity_id;
    IF _mode IS NULL THEN _mode := 'AND'; END IF;

    SELECT sm.user_type INTO _user_type FROM ember_schema.station_member sm WHERE sm.id = _member_id;
    IF _user_type IS NULL THEN RETURN FALSE; END IF;

    SELECT COALESCE(ARRAY(SELECT mge.group_id
                          FROM ember_schema.member_group_entry mge
                          WHERE mge.member_id = _member_id), '{}')
    INTO _group_ids;

    SELECT COALESCE(ARRAY(SELECT ute.tag_id
                          FROM ember_schema.user_tag_entry ute
                          WHERE ute.member_id = _member_id), '{}')
    INTO _tag_ids;

    RETURN ember_schema.check_restriction(
            _restriction_table, _fk_column, _entity_id, _mode, _member_id,
            _user_type, _group_ids, _tag_ids
           );
END;
$$;

COMMENT ON FUNCTION ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, TEXT, INT, INT) IS
    'Whether the restriction rows of one entity take in a member, reading the member''s user type, groups and tags and the entity''s combination mode itself. Managers are not let through here: whether somebody manages the entity type is decided by the application and combined with this result by the caller.';

ALTER TABLE ember_schema.email_queue
    ADD COLUMN next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
    ADD COLUMN claimed_at      TIMESTAMP;

UPDATE ember_schema.email_queue
SET claimed_at = created_at
WHERE status = 'SENDING';

COMMENT ON COLUMN ember_schema.email_queue.next_attempt_at
    IS 'Earliest time the worker may take this mail again. Pushed back after every transient failure, doubling each time.';
COMMENT ON COLUMN ember_schema.email_queue.claimed_at
    IS 'When the worker last took this mail in hand for sending. A mail in sending for long past this was left behind by a worker that died.';

CREATE INDEX idx_email_queue_pending_due ON ember_schema.email_queue (next_attempt_at) WHERE status = 'PENDING';

ALTER TABLE ember_schema.station_mail_provider
    ADD COLUMN smtp_encryption TEXT NOT NULL DEFAULT 'STARTTLS'
        CHECK (smtp_encryption IN ('IMPLICIT_TLS', 'STARTTLS', 'NONE'));

UPDATE ember_schema.station_mail_provider
SET smtp_encryption = 'IMPLICIT_TLS'
WHERE smtp_ssl;

ALTER TABLE ember_schema.station_mail_provider
    DROP COLUMN smtp_ssl;

COMMENT ON COLUMN ember_schema.station_mail_provider.smtp_encryption
    IS 'How the connection to the relay is secured: IMPLICIT_TLS from the first byte, STARTTLS required before login, or NONE for an unencrypted relay chosen on purpose.';

DROP TABLE IF EXISTS ember_schema.account_external_auth;

COMMENT ON COLUMN ember_schema.station.federation_private_key
    IS 'Private key the station signs federation requests with, shared across all of its partners. Stored encrypted with the instance key from the configuration or the data directory, marked by the enc:v1: prefix; a value without it predates encryption and is encrypted once at startup. Never exported as a column: a station transfer carries it sealed with the transfer token.';

CREATE TABLE ember_schema.signed_request_nonce
(
    scope      TEXT        NOT NULL,
    nonce      TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (scope, nonce)
);

CREATE INDEX idx_signed_request_nonce_expires ON ember_schema.signed_request_nonce (expires_at);

COMMENT ON TABLE ember_schema.signed_request_nonce
    IS 'Nonces of signed requests from other instances already taken, so a captured request cannot be sent again. Shared by discovery pings and beacon deliveries; swept once expired.';
COMMENT ON COLUMN ember_schema.signed_request_nonce.scope
    IS 'Whose nonces these are: discovery for inbound pings, beacon:<instance id> for deliveries to this beacon. The same nonce may appear once per scope.';
COMMENT ON COLUMN ember_schema.signed_request_nonce.nonce
    IS 'The nonce the signed request carried.';
COMMENT ON COLUMN ember_schema.signed_request_nonce.expires_at
    IS 'When the request would no longer be accepted anyway, after which the nonce is forgotten.';

INSERT INTO ember_schema.signed_request_nonce (scope, nonce, expires_at)
SELECT 'beacon:' || instance_id, nonce, issued_at + INTERVAL '20 minutes'
FROM ember_schema.beacon_nonce
ON CONFLICT DO NOTHING;

INSERT INTO ember_schema.signed_request_nonce (scope, nonce, expires_at)
SELECT 'discovery', nonce, expires_at
FROM ember_schema.discovery_ping
WHERE direction = 'IN'
ON CONFLICT DO NOTHING;

DELETE FROM ember_schema.discovery_ping WHERE direction = 'IN';

DROP TABLE ember_schema.beacon_nonce;

COMMENT ON TABLE ember_schema.discovery_ping
    IS 'Pings this instance sent and awaits a callback for, so the callback can be matched to its ping. Inbound ping nonces live in signed_request_nonce.';
COMMENT ON COLUMN ember_schema.discovery_ping.direction
    IS 'OUT for pings this instance sent. Rows for received pings (IN) are no longer written.';

CREATE TABLE ember_schema.event_date_cancellation
(
    event_id     INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date   DATE        NOT NULL,
    cause        TEXT        NOT NULL CHECK (cause IN ('MANUAL', 'THRESHOLD')),
    reason       TEXT,
    cancelled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    restored_at  TIMESTAMPTZ,
    PRIMARY KEY (event_id, event_date)
);

COMMENT ON TABLE ember_schema.event_date_cancellation
    IS 'One date of an appointment that was called off, a one-time appointment included. The date is cancelled while restored_at is empty. A restored row stays, so the minimum-registration check never cancels a date a manager deliberately brought back.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.event_id
    IS 'The appointment the date belongs to.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.event_date
    IS 'The date that was called off, on the station''s calendar.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.cause
    IS 'Who called it off: MANUAL for a manager, THRESHOLD for the check that too few had registered.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.reason
    IS 'The reason the manager gave. Empty for THRESHOLD, whose reason is worded in the reader''s language from the cause.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.cancelled_at
    IS 'When the date was called off, the last time if it was called off more than once.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.cancelled_by
    IS 'The manager who called it off. Empty for THRESHOLD and once that member is gone.';
COMMENT ON COLUMN ember_schema.event_date_cancellation.restored_at
    IS 'When a manager brought the date back. Empty while the date stays cancelled.';

INSERT INTO ember_schema.event_date_cancellation (event_id, event_date, cause, reason, cancelled_at)
SELECT e.id,
       (e.start_time AT TIME ZONE zone.name)::DATE,
       CASE WHEN e.cancel_reason LIKE 'Mindestanzahl von % Anmeldungen nicht erreicht' THEN 'THRESHOLD' ELSE 'MANUAL' END,
       CASE WHEN e.cancel_reason LIKE 'Mindestanzahl von % Anmeldungen nicht erreicht' THEN NULL ELSE e.cancel_reason END,
       coalesce(e.cancelled_at, now())
FROM ember_schema.station_event e
         JOIN ember_schema.station s ON s.id = e.station_id
         CROSS JOIN LATERAL (SELECT CASE
                                        WHEN EXISTS (SELECT 1 FROM pg_timezone_names tz WHERE tz.name = s.timezone)
                                            THEN s.timezone
                                        ELSE 'UTC' END AS name) zone
WHERE e.event_type = 'ONE_TIME'
  AND e.cancelled;

UPDATE ember_schema.station_event
SET cancelled     = FALSE,
    cancelled_at  = NULL,
    cancel_reason = NULL
WHERE event_type = 'ONE_TIME'
  AND cancelled;

COMMENT ON COLUMN ember_schema.station_event.cancelled
    IS 'Whether the whole series has been cancelled. A single date, and a one-time appointment, is cancelled in event_date_cancellation instead.';
COMMENT ON COLUMN ember_schema.station_event.cancelled_at
    IS 'When the whole series was cancelled.';
COMMENT ON COLUMN ember_schema.station_event.cancel_reason
    IS 'The reason given for cancelling the whole series.';

ALTER TABLE ember_schema.station_event
    ADD COLUMN threshold_days INTEGER CHECK (threshold_days >= 0);

UPDATE ember_schema.station_event e
SET threshold_days = greatest(0, (e.start_time AT TIME ZONE zone.name)::DATE
                                     - (e.threshold_date AT TIME ZONE zone.name)::DATE)
FROM ember_schema.station s
         CROSS JOIN LATERAL (SELECT CASE
                                        WHEN EXISTS (SELECT 1 FROM pg_timezone_names tz WHERE tz.name = s.timezone)
                                            THEN s.timezone
                                        ELSE 'UTC' END AS name) zone
WHERE s.id = e.station_id
  AND e.event_type = 'ONE_TIME'
  AND e.threshold_date IS NOT NULL;

ALTER TABLE ember_schema.station_event
    DROP COLUMN threshold_date,
    DROP COLUMN threshold_notified;

COMMENT ON COLUMN ember_schema.station_event.threshold_days
    IS 'How many days before each date min_registrations must be reached, or that date is cancelled automatically. Empty where no date is ever cancelled for too few registrations.';

CREATE TABLE ember_schema.account_ai_credential
(
    account_id INT         NOT NULL PRIMARY KEY REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    provider   TEXT        NOT NULL,
    model      TEXT,
    api_key    TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.account_ai_credential
    IS 'The AI provider key a person keeps for generating quiz content, one per account. It follows them to every browser and every station they work at.';
COMMENT ON COLUMN ember_schema.account_ai_credential.account_id
    IS 'The account the key belongs to. Deleted with the account.';
COMMENT ON COLUMN ember_schema.account_ai_credential.provider
    IS 'Which AI provider the key is for (openai, gemini, claude).';
COMMENT ON COLUMN ember_schema.account_ai_credential.model
    IS 'The model to ask by default. NULL for the provider default.';
COMMENT ON COLUMN ember_schema.account_ai_credential.api_key
    IS 'The key, encrypted with the instance credential key (enc:v1: prefix). Never sent back to a browser and never exported.';
COMMENT ON COLUMN ember_schema.account_ai_credential.updated_at
    IS 'When the key or its settings were last saved.';

COMMENT ON COLUMN ember_schema.station_ai_provider.api_key
    IS 'The station key for the provider, encrypted with the instance credential key (enc:v1: prefix). A key stored in plaintext before encryption existed is encrypted at start-up. Never sent to a browser.';

ALTER TABLE ember_schema.notification
    ADD COLUMN IF NOT EXISTS dedup_key TEXT NULL;

COMMENT ON COLUMN ember_schema.notification.dedup_key
    IS 'Set on a notification meant to arrive once while unread: a hash of its type and data. Two unread notifications of one recipient never share a key, so the same news is not told twice. NULL where every sending counts.';

WITH ranked AS (SELECT id,
                       row_number() OVER (
                           PARTITION BY member_id, cluster_member_id, md5(type || data::TEXT)
                           ORDER BY created_at, id) AS position
                FROM ember_schema.notification
                WHERE acknowledged_at IS NULL)
UPDATE ember_schema.notification n
SET dedup_key = md5(n.type || n.data::TEXT)
FROM ranked
WHERE ranked.id = n.id
  AND ranked.position = 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_unread_member
    ON ember_schema.notification (member_id, dedup_key)
    WHERE acknowledged_at IS NULL AND dedup_key IS NOT NULL AND member_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_unread_cluster_member
    ON ember_schema.notification (cluster_member_id, dedup_key)
    WHERE acknowledged_at IS NULL AND dedup_key IS NOT NULL AND cluster_member_id IS NOT NULL;

ALTER TABLE ember_schema.cluster_member
    ADD COLUMN IF NOT EXISTS email_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.cluster_member.email_enabled
    IS 'Whether this person wants the association''s gathered notifications by mail. Off until they switch it on, the same as a station membership''s mail setting.';

CREATE TABLE ember_schema.attendance_template_user_type
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    user_type   TEXT    NOT NULL,
    PRIMARY KEY (template_id, user_type)
);

COMMENT ON TABLE ember_schema.attendance_template_user_type
    IS 'User types whose members are expected on sheets made from this template, in addition to the members of its groups. No row enters nobody by type.';
COMMENT ON COLUMN ember_schema.attendance_template_user_type.template_id
    IS 'References the attendance template. Deleted with the template.';
COMMENT ON COLUMN ember_schema.attendance_template_user_type.user_type
    IS 'StationUserType name whose members the template expects.';

CREATE TABLE ember_schema.attendance_session_audience
(
    session_id INTEGER NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    user_type  TEXT    NULL,
    group_id   INTEGER NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    position   INTEGER NOT NULL DEFAULT 0,
    CHECK (num_nonnulls(user_type, group_id) = 1),
    UNIQUE (session_id, user_type),
    UNIQUE (session_id, group_id)
);

COMMENT ON TABLE ember_schema.attendance_session_audience
    IS 'Whom one sheet was told to expect when it was started, instead of the user types and groups of its template. A sheet without rows follows its template.';
COMMENT ON COLUMN ember_schema.attendance_session_audience.session_id
    IS 'References the attendance session. Deleted with the session.';
COMMENT ON COLUMN ember_schema.attendance_session_audience.user_type
    IS 'StationUserType name whose members the sheet expects. Exactly one of user_type and group_id is set.';
COMMENT ON COLUMN ember_schema.attendance_session_audience.group_id
    IS 'Group whose members the sheet expects. Exactly one of user_type and group_id is set.';
COMMENT ON COLUMN ember_schema.attendance_session_audience.position
    IS 'Order the groups were chosen in, which is the order the sheet is written in.';

ALTER TABLE ember_schema.federation_lending_request
    ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_federation_lending_request_uid
    ON ember_schema.federation_lending_request (uid);

COMMENT ON COLUMN ember_schema.federation_lending_request.uid
    IS 'The identity the request carries between the two stations. A request to a station on another instance is kept on both instances under the same uid; between stations of one instance it is the one row both of them share.';

ALTER TABLE ember_schema.federation_lending_request_item
    ADD COLUMN label TEXT NOT NULL DEFAULT '';

COMMENT ON COLUMN ember_schema.federation_lending_request_item.label
    IS 'What the line asks for, in words, as the lending station named it. Kept on the borrowing station''s copy of a request to a station on another instance, whose inventories are not here to name it. Empty where the line names gear of this instance.';

ALTER TABLE ember_schema.station
    ADD COLUMN discovery_reviewed_at TIMESTAMPTZ;

COMMENT ON COLUMN ember_schema.station.discovery_reviewed_at
    IS 'When a manager last saved how the station is listed in discovery, NULL while nobody has. Once set, the station has decided knowingly and the discovery step of its setup counts as done.';

CREATE TABLE ember_schema.member_group_set
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

COMMENT ON TABLE ember_schema.member_group_set
    IS 'A set of groups a member can be in only one of, such as the levels of a training. Deleting a set keeps its groups and their members.';
COMMENT ON COLUMN ember_schema.member_group_set.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_group_set.station_id
    IS 'The station the set belongs to.';
COMMENT ON COLUMN ember_schema.member_group_set.name
    IS 'The name of the set, unique within the station.';

ALTER TABLE ember_schema.member_group
    ADD COLUMN group_set_id INTEGER REFERENCES ember_schema.member_group_set (id) ON DELETE SET NULL;

CREATE INDEX idx_member_group_group_set ON ember_schema.member_group (group_set_id);

COMMENT ON COLUMN ember_schema.member_group.group_set_id
    IS 'The set this group belongs to, which allows a member in only one of its groups. Empty for a group in no set.';

CREATE TABLE ember_schema.member_group_user_type
(
    group_id  INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    user_type TEXT    NOT NULL CHECK (user_type IN ('TRIAL', 'MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER')),
    PRIMARY KEY (group_id, user_type)
);

COMMENT ON TABLE ember_schema.member_group_user_type
    IS 'The user types a group is bound to: only members of these types can be in it. A group with no rows here takes every type.';
COMMENT ON COLUMN ember_schema.member_group_user_type.group_id
    IS 'The bound group. Deleted with the group.';
COMMENT ON COLUMN ember_schema.member_group_user_type.user_type
    IS 'A user type allowed in the group (TRIAL, MEMBER, GUARDIAN, TEAM, MANAGER).';

ALTER TABLE ember_schema.member_group_entry
    ADD COLUMN group_set_id INTEGER;

UPDATE ember_schema.member_group_entry mge
SET group_set_id = mg.group_set_id
FROM ember_schema.member_group mg
WHERE mg.id = mge.group_id;

COMMENT ON COLUMN ember_schema.member_group_entry.group_set_id
    IS 'A copy of the set of the group, kept right by triggers on this table and on member_group. It exists so one unique index can allow a member in only one group of a set.';

CREATE UNIQUE INDEX idx_member_group_entry_one_per_set
    ON ember_schema.member_group_entry (member_id, group_set_id)
    WHERE group_set_id IS NOT NULL;

CREATE OR REPLACE FUNCTION ember_schema.member_group_entry_copy_set() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    SELECT mg.group_set_id INTO NEW.group_set_id FROM ember_schema.member_group mg WHERE mg.id = NEW.group_id;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION ember_schema.member_group_entry_copy_set()
    IS 'Fills the set of a new or moved group membership from its group, whoever writes it.';

CREATE TRIGGER member_group_entry_copy_set
    BEFORE INSERT OR UPDATE OF group_id
    ON ember_schema.member_group_entry
    FOR EACH ROW
EXECUTE FUNCTION ember_schema.member_group_entry_copy_set();

CREATE OR REPLACE FUNCTION ember_schema.member_group_spread_set() RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE ember_schema.member_group_entry SET group_set_id = NEW.group_set_id WHERE group_id = NEW.id;
    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION ember_schema.member_group_spread_set()
    IS 'Carries the set of a group over to its memberships when the group changes set, so moving a group into a set whose members overlap fails on the unique index.';

CREATE TRIGGER member_group_spread_set
    AFTER UPDATE OF group_set_id
    ON ember_schema.member_group
    FOR EACH ROW
    WHEN (OLD.group_set_id IS DISTINCT FROM NEW.group_set_id)
EXECUTE FUNCTION ember_schema.member_group_spread_set();

CREATE TABLE ember_schema.comment
(
    id                 SERIAL PRIMARY KEY,
    station_id         INTEGER REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    event_id           INTEGER REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    event_date         DATE,
    news_id            INTEGER REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    kb_file_id         INTEGER REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    board_ticket_id    INTEGER REFERENCES ember_schema.board_ticket (id) ON DELETE CASCADE,
    parent_id          INTEGER REFERENCES ember_schema.comment (id) ON DELETE SET NULL,
    author_station_uid UUID,
    author_member_uid  UUID,
    content            TEXT        NOT NULL,
    deleted            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ,
    CONSTRAINT comment_one_target CHECK (num_nonnulls(event_id, news_id, kb_file_id, board_ticket_id) = 1),
    CONSTRAINT comment_date_on_event CHECK (event_date IS NULL OR event_id IS NOT NULL),
    CONSTRAINT comment_station_known CHECK (station_id IS NOT NULL OR news_id IS NOT NULL)
);

CREATE INDEX idx_comment_station ON ember_schema.comment (station_id);
CREATE INDEX idx_comment_event ON ember_schema.comment (event_id, event_date) WHERE event_id IS NOT NULL;
CREATE INDEX idx_comment_news ON ember_schema.comment (news_id) WHERE news_id IS NOT NULL;
CREATE INDEX idx_comment_kb_file ON ember_schema.comment (kb_file_id) WHERE kb_file_id IS NOT NULL;
CREATE INDEX idx_comment_board_ticket ON ember_schema.comment (board_ticket_id) WHERE board_ticket_id IS NOT NULL;
CREATE INDEX idx_comment_parent ON ember_schema.comment (parent_id) WHERE parent_id IS NOT NULL;

COMMENT ON TABLE ember_schema.comment
    IS 'Threaded comments on appointments, news entries, knowledge base files and board tickets. Exactly one target column is set, and that column says what the comment was written on.';
COMMENT ON COLUMN ember_schema.comment.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.comment.station_id
    IS 'The station owning what the comment was written on, copied from it. NULL only under a news entry the instance published to every station.';
COMMENT ON COLUMN ember_schema.comment.event_id
    IS 'The appointment the comment was written on, NULL for every other target.';
COMMENT ON COLUMN ember_schema.comment.event_date
    IS 'For a comment on one occurrence of a recurring appointment, the date of that occurrence. NULL for one-time appointments, for comments on the whole appointment and for every other target.';
COMMENT ON COLUMN ember_schema.comment.news_id
    IS 'The news entry the comment was written on, NULL for every other target.';
COMMENT ON COLUMN ember_schema.comment.kb_file_id
    IS 'The knowledge base file the comment was written on, NULL for every other target.';
COMMENT ON COLUMN ember_schema.comment.board_ticket_id
    IS 'The board ticket the comment was written on, NULL for every other target.';
COMMENT ON COLUMN ember_schema.comment.parent_id
    IS 'The comment this one answers, always on the same target. NULL for top-level comments and for answers whose parent is gone.';
COMMENT ON COLUMN ember_schema.comment.author_station_uid
    IS 'Station UUID of the comment author, which can be a partner station.';
COMMENT ON COLUMN ember_schema.comment.author_member_uid
    IS 'Member UUID of the comment author within that station.';
COMMENT ON COLUMN ember_schema.comment.content
    IS 'Comment text. Empty once a comment with answers is deleted.';
COMMENT ON COLUMN ember_schema.comment.deleted
    IS 'Soft-delete flag for a comment that still has answers. Content is hidden but threading is preserved.';
COMMENT ON COLUMN ember_schema.comment.created_at
    IS 'When the comment was created.';
COMMENT ON COLUMN ember_schema.comment.updated_at
    IS 'When the comment was last edited. NULL when never edited.';

INSERT INTO ember_schema.comment
    (id, station_id, event_id, event_date, author_station_uid, author_member_uid, content, deleted, created_at,
     updated_at)
SELECT c.id,
       e.station_id,
       c.event_id,
       c.event_date,
       c.author_station_uid,
       c.author_member_uid,
       c.content,
       c.deleted,
       c.created_at,
       c.updated_at
FROM ember_schema.event_comment c
         JOIN ember_schema.station_event e ON e.id = c.event_id;

UPDATE ember_schema.comment c
SET parent_id = old.parent_id
FROM ember_schema.event_comment old
WHERE old.id = c.id
  AND old.parent_id IS NOT NULL;

CREATE TEMP TABLE comment_id_move
(
    kind   TEXT    NOT NULL,
    old_id INTEGER NOT NULL,
    new_id INTEGER NOT NULL,
    PRIMARY KEY (kind, old_id)
) ON COMMIT DROP;

INSERT INTO comment_id_move (kind, old_id, new_id)
SELECT moved.kind,
       moved.old_id,
       (SELECT coalesce(max(id), 0) FROM ember_schema.comment) + row_number() OVER (ORDER BY moved.kind_order, moved.old_id)
FROM (SELECT 1 AS kind_order, 'NEWS' AS kind, id AS old_id
      FROM ember_schema.news_comment
      UNION ALL
      SELECT 2, 'KB', id
      FROM ember_schema.kb_comment
      UNION ALL
      SELECT 3, 'BOARD_TICKET', id
      FROM ember_schema.board_ticket_comment) moved;

INSERT INTO ember_schema.comment
    (id, station_id, news_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at)
SELECT m.new_id,
       n.station_id,
       c.news_id,
       c.author_station_uid,
       c.author_member_uid,
       c.content,
       c.deleted,
       c.created_at::TIMESTAMPTZ,
       c.updated_at
FROM ember_schema.news_comment c
         JOIN ember_schema.news n ON n.id = c.news_id
         JOIN comment_id_move m ON m.kind = 'NEWS' AND m.old_id = c.id;

INSERT INTO ember_schema.comment
    (id, station_id, kb_file_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at)
SELECT m.new_id,
       f.station_id,
       c.file_id,
       c.author_station_uid,
       c.author_member_uid,
       c.content,
       c.deleted,
       c.created_at,
       c.updated_at
FROM ember_schema.kb_comment c
         JOIN ember_schema.kb_file f ON f.id = c.file_id
         JOIN comment_id_move m ON m.kind = 'KB' AND m.old_id = c.id;

INSERT INTO ember_schema.comment
    (id, station_id, board_ticket_id, author_station_uid, author_member_uid, content, deleted, created_at,
     updated_at)
SELECT m.new_id,
       b.station_id,
       c.ticket_id,
       c.author_station_uid,
       c.author_member_uid,
       c.content,
       c.deleted,
       c.created_at,
       c.updated_at
FROM ember_schema.board_ticket_comment c
         JOIN ember_schema.board_ticket t ON t.id = c.ticket_id
         JOIN ember_schema.board b ON b.id = t.board_id
         JOIN comment_id_move m ON m.kind = 'BOARD_TICKET' AND m.old_id = c.id;

UPDATE ember_schema.comment c
SET parent_id = parent.new_id
FROM (SELECT 'NEWS' AS kind, id, parent_id
      FROM ember_schema.news_comment
      UNION ALL
      SELECT 'KB', id, parent_id
      FROM ember_schema.kb_comment
      UNION ALL
      SELECT 'BOARD_TICKET', id, parent_id
      FROM ember_schema.board_ticket_comment) old
         JOIN comment_id_move child ON child.kind = old.kind AND child.old_id = old.id
         JOIN comment_id_move parent ON parent.kind = old.kind AND parent.old_id = old.parent_id
WHERE c.id = child.new_id;

SELECT setval(pg_get_serial_sequence('ember_schema.comment', 'id'),
              (SELECT coalesce(max(id), 0) + 1 FROM ember_schema.comment), FALSE);

UPDATE ember_schema.notification n
SET data      = jsonb_set(n.data, '{link,query,comment}', to_jsonb(m.new_id)),
    dedup_key = CASE
                    WHEN n.dedup_key IS NOT NULL
                        THEN md5(n.type || jsonb_set(n.data, '{link,query,comment}', to_jsonb(m.new_id))::TEXT)
        END
FROM comment_id_move m
WHERE n.data -> 'link' ->> 'route' = CASE m.kind
                                         WHEN 'NEWS' THEN 'news-detail'
                                         WHEN 'KB' THEN 'kb-file'
                                         ELSE 'ticket-detail'
    END
  AND n.data -> 'link' -> 'query' -> 'comment' = to_jsonb(m.old_id);

DROP TABLE ember_schema.event_comment;
DROP TABLE ember_schema.news_comment;
DROP TABLE ember_schema.kb_comment;
DROP TABLE ember_schema.board_ticket_comment;

DELETE
FROM ember_schema.profile_field_value v
    USING ember_schema.profile_field f
WHERE v.field_id = f.id
  AND f.field_type = 'AGE';

DELETE
FROM ember_schema.cluster_profile_field_value v
    USING ember_schema.cluster_profile_field f
WHERE v.field_id = f.id
  AND f.field_type = 'AGE';

CREATE TABLE IF NOT EXISTS ember_schema.mail_webhook_receipt
(
    provider    TEXT      NOT NULL,
    webhook_id  TEXT      NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, webhook_id)
);

COMMENT ON TABLE ember_schema.mail_webhook_receipt
    IS 'Which delivery reports a mail provider has already handed over, so a report it sends again is answered without being taken twice.';
COMMENT ON COLUMN ember_schema.mail_webhook_receipt.provider IS 'The provider that sent the report.';
COMMENT ON COLUMN ember_schema.mail_webhook_receipt.webhook_id IS 'The id the provider gives the report, the same on every repeat of it.';
COMMENT ON COLUMN ember_schema.mail_webhook_receipt.received_at IS 'When the report first arrived. Receipts older than the provider keeps retrying are removed.';

UPDATE ember_schema.profile_field
SET field_type = CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END
WHERE field_type <> CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END;

UPDATE ember_schema.cluster_profile_field
SET field_type = CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END
WHERE field_type <> CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END;

UPDATE ember_schema.inventory_field_definition
SET field_type = CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END
WHERE field_type <> CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END;

UPDATE ember_schema.inventory_field_definition
SET config = jsonb_set(coalesce(config, '{}'), '{kind}', to_jsonb(field_type))
WHERE config ->> 'kind' IS DISTINCT FROM field_type;

UPDATE ember_schema.waiting_list_field
SET field_type = CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END
WHERE field_type <> CASE upper(field_type) WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END;

UPDATE ember_schema.board_field
SET field_type = CASE upper(field_type) WHEN 'STRING' THEN 'TEXT' WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END
WHERE field_type <> CASE upper(field_type) WHEN 'STRING' THEN 'TEXT' WHEN 'ENUM' THEN 'CHOICE' ELSE upper(field_type) END;

UPDATE ember_schema.attendance_template_field
SET field_type = CASE upper(field_type)
                     WHEN 'STRING' THEN 'TEXT'
                     WHEN 'TEXTAREA' THEN 'LONG_TEXT'
                     WHEN 'ENUM' THEN 'CHOICE'
                     ELSE upper(field_type) END
WHERE upper(field_type) IN ('STRING', 'TEXTAREA', 'ENUM')
   OR field_type <> upper(field_type);

UPDATE ember_schema.event_field
SET field_type = CASE upper(field_type)
                     WHEN 'STRING' THEN 'TEXT'
                     WHEN 'TEXTAREA' THEN 'LONG_TEXT'
                     WHEN 'ENUM' THEN 'CHOICE'
                     ELSE upper(field_type) END
WHERE upper(field_type) IN ('STRING', 'TEXTAREA', 'ENUM')
   OR field_type <> upper(field_type);

UPDATE ember_schema.event_template_field
SET field_type = CASE upper(field_type)
                     WHEN 'STRING' THEN 'TEXT'
                     WHEN 'TEXTAREA' THEN 'LONG_TEXT'
                     WHEN 'ENUM' THEN 'CHOICE'
                     ELSE upper(field_type) END
WHERE upper(field_type) IN ('STRING', 'TEXTAREA', 'ENUM')
   OR field_type <> upper(field_type);

UPDATE ember_schema.event_registration_field
SET field_type = CASE upper(field_type)
                     WHEN 'STRING' THEN 'TEXT'
                     WHEN 'TEXTAREA' THEN 'LONG_TEXT'
                     WHEN 'ENUM' THEN 'CHOICE'
                     ELSE upper(field_type) END
WHERE upper(field_type) IN ('STRING', 'TEXTAREA', 'ENUM')
   OR field_type <> upper(field_type);

UPDATE ember_schema.event_template_registration_field
SET field_type = CASE upper(field_type)
                     WHEN 'STRING' THEN 'TEXT'
                     WHEN 'TEXTAREA' THEN 'LONG_TEXT'
                     WHEN 'ENUM' THEN 'CHOICE'
                     ELSE upper(field_type) END
WHERE upper(field_type) IN ('STRING', 'TEXTAREA', 'ENUM')
   OR field_type <> upper(field_type);

ALTER TABLE ember_schema.board_field
    ALTER COLUMN field_type SET DEFAULT 'TEXT';
ALTER TABLE ember_schema.event_field
    ALTER COLUMN field_type SET DEFAULT 'TEXT';
ALTER TABLE ember_schema.event_template_field
    ALTER COLUMN field_type SET DEFAULT 'TEXT';

DELETE
FROM ember_schema.profile_field_value v
    USING ember_schema.profile_field f
WHERE v.field_id = f.id
  AND (f.field_type IN ('SECTION', 'SPACER')
    OR (f.field_type = 'BOOLEAN' AND v.value IN ('""', 'null', '{}')));

DELETE
FROM ember_schema.cluster_profile_field_value v
    USING ember_schema.cluster_profile_field f
WHERE v.field_id = f.id
  AND (f.field_type IN ('SECTION', 'SPACER')
    OR (f.field_type = 'BOOLEAN' AND (v.value IS NULL OR v.value IN ('""', 'null', '{}'))));

DELETE
FROM ember_schema.waiting_list_entry_value v
    USING ember_schema.waiting_list_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND v.value IN ('""', 'null', '{}');

DELETE
FROM ember_schema.attendance_session_field v
    USING ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND (f.field_type = 'BOOLEAN' OR f.field_type LIKE 'MEMBER%')
  AND v.value IN ('""', 'null', '{}', '[]');

UPDATE ember_schema.profile_field_value v
SET value = to_jsonb(lower(trim(v.value #>> '{}')) IN ('true', '1'))
FROM ember_schema.profile_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND jsonb_typeof(v.value) IN ('string', 'number')
  AND lower(trim(v.value #>> '{}')) IN ('true', 'false', '1', '0');

UPDATE ember_schema.cluster_profile_field_value v
SET value = to_jsonb(lower(trim(v.value #>> '{}')) IN ('true', '1'))
FROM ember_schema.cluster_profile_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND jsonb_typeof(v.value) IN ('string', 'number')
  AND lower(trim(v.value #>> '{}')) IN ('true', 'false', '1', '0');

UPDATE ember_schema.waiting_list_entry_value v
SET value = to_jsonb(lower(trim(v.value #>> '{}')) IN ('true', '1'))
FROM ember_schema.waiting_list_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND jsonb_typeof(v.value) IN ('string', 'number')
  AND lower(trim(v.value #>> '{}')) IN ('true', 'false', '1', '0');

UPDATE ember_schema.attendance_session_field v
SET value = to_jsonb(lower(trim(v.value #>> '{}')) IN ('true', '1'))
FROM ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND jsonb_typeof(v.value) IN ('string', 'number')
  AND lower(trim(v.value #>> '{}')) IN ('true', 'false', '1', '0');

UPDATE ember_schema.attendance_session_field v
SET value = to_jsonb(trim(v.value #>> '{}')::INTEGER)
FROM ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND f.field_type IN ('MEMBER', 'MEMBER_OF_GROUP')
  AND jsonb_typeof(v.value) = 'string'
  AND trim(v.value #>> '{}') ~ '^[0-9]+$';

UPDATE ember_schema.attendance_session_field v
SET value = CASE
                WHEN trim(v.value #>> '{}') ~ '^[0-9]+$' THEN jsonb_build_array(trim(v.value #>> '{}')::INTEGER)
                ELSE (v.value #>> '{}')::JSONB END
FROM ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND f.field_type IN ('MEMBER_LIST', 'MEMBER_LIST_OF_GROUP')
  AND jsonb_typeof(v.value) = 'string'
  AND trim(v.value #>> '{}') ~ '^([0-9]+|\[\s*"?[0-9]+"?(\s*,\s*"?[0-9]+"?)*\s*\])$';

UPDATE ember_schema.attendance_session_field v
SET value = (SELECT jsonb_agg((e.element #>> '{}')::INTEGER ORDER BY e.position)
             FROM jsonb_array_elements(v.value) WITH ORDINALITY AS e(element, position))
FROM ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND f.field_type IN ('MEMBER_LIST', 'MEMBER_LIST_OF_GROUP')
  AND jsonb_typeof(v.value) = 'array'
  AND jsonb_array_length(v.value) > 0
  AND EXISTS (SELECT 1 FROM jsonb_array_elements(v.value) AS e(element) WHERE jsonb_typeof(e.element) = 'string')
  AND NOT EXISTS (SELECT 1
                  FROM jsonb_array_elements(v.value) AS e(element)
                  WHERE trim(e.element #>> '{}') !~ '^[0-9]+$');

DELETE
FROM ember_schema.attendance_session_field v
    USING ember_schema.attendance_template_field f
WHERE v.field_id = f.id
  AND f.field_type LIKE 'MEMBER%'
  AND v.value = '[]';

UPDATE ember_schema.event_field
SET value = CASE WHEN lower(trim(value)) IN ('true', '1') THEN 'true' ELSE 'false' END
WHERE field_type = 'BOOLEAN'
  AND lower(trim(value)) IN ('true', 'false', '1', '0')
  AND value NOT IN ('true', 'false');

UPDATE ember_schema.event_field_date_value v
SET value = CASE WHEN lower(trim(v.value)) IN ('true', '1') THEN 'true' ELSE 'false' END
FROM ember_schema.event_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND lower(trim(v.value)) IN ('true', 'false', '1', '0')
  AND v.value NOT IN ('true', 'false');

UPDATE ember_schema.event_registration_field_value v
SET value = CASE WHEN lower(trim(v.value)) IN ('true', '1') THEN 'true' ELSE 'false' END
FROM ember_schema.event_registration_field f
WHERE v.field_id = f.id
  AND f.field_type = 'BOOLEAN'
  AND lower(trim(v.value)) IN ('true', 'false', '1', '0')
  AND v.value NOT IN ('true', 'false');

UPDATE ember_schema.event_field
SET value = CASE
                WHEN value ~ '^\s*\[\s*\]\s*$' THEN ''
                WHEN value ~ '^\s*"?[0-9]+"?\s*$' THEN btrim(value, ' "')
                ELSE '[' || (SELECT string_agg(e.element #>> '{}', ',' ORDER BY e.position)
                             FROM jsonb_array_elements(value::JSONB) WITH ORDINALITY AS e(element, position)) || ']' END
WHERE field_type LIKE 'MEMBER%'
  AND value ~ '^\s*("?[0-9]+"?|\[\s*\]|\[\s*"?[0-9]+"?(\s*,\s*"?[0-9]+"?)*\s*\])\s*$'
  AND value !~ '^([0-9]+|\[[0-9]+(,[0-9]+)*\])$';

UPDATE ember_schema.event_field_date_value v
SET value = CASE
                WHEN v.value ~ '^\s*\[\s*\]\s*$' THEN ''
                WHEN v.value ~ '^\s*"?[0-9]+"?\s*$' THEN btrim(v.value, ' "')
                ELSE '[' || (SELECT string_agg(e.element #>> '{}', ',' ORDER BY e.position)
                             FROM jsonb_array_elements(v.value::JSONB) WITH ORDINALITY AS e(element, position)) || ']' END
FROM ember_schema.event_field f
WHERE v.field_id = f.id
  AND f.field_type LIKE 'MEMBER%'
  AND v.value ~ '^\s*("?[0-9]+"?|\[\s*\]|\[\s*"?[0-9]+"?(\s*,\s*"?[0-9]+"?)*\s*\])\s*$'
  AND v.value !~ '^([0-9]+|\[[0-9]+(,[0-9]+)*\])$';

UPDATE ember_schema.event_registration_field_value v
SET value = CASE
                WHEN v.value ~ '^\s*\[\s*\]\s*$' THEN ''
                WHEN v.value ~ '^\s*"?[0-9]+"?\s*$' THEN btrim(v.value, ' "')
                ELSE '[' || (SELECT string_agg(e.element #>> '{}', ',' ORDER BY e.position)
                             FROM jsonb_array_elements(v.value::JSONB) WITH ORDINALITY AS e(element, position)) || ']' END
FROM ember_schema.event_registration_field f
WHERE v.field_id = f.id
  AND f.field_type LIKE 'MEMBER%'
  AND v.value ~ '^\s*("?[0-9]+"?|\[\s*\]|\[\s*"?[0-9]+"?(\s*,\s*"?[0-9]+"?)*\s*\])\s*$'
  AND v.value !~ '^([0-9]+|\[[0-9]+(,[0-9]+)*\])$';

ALTER TABLE ember_schema.profile_field
    ADD CONSTRAINT profile_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'AGE', 'BIRTH_DATE', 'EXPIRY_DATE', 'SECTION', 'SPACER'));

UPDATE ember_schema.cluster_profile_field
SET field_type = 'DATE'
WHERE field_type = 'BIRTH_DATE';

ALTER TABLE ember_schema.cluster_profile_field
    ADD CONSTRAINT cluster_profile_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'AGE', 'EXPIRY_DATE', 'SECTION', 'SPACER'));

ALTER TABLE ember_schema.inventory_field_definition
    ADD CONSTRAINT inventory_field_definition_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE'));

ALTER TABLE ember_schema.board_field
    ADD CONSTRAINT board_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'LANE_ASSIGNEE'));

ALTER TABLE ember_schema.waiting_list_field
    ADD CONSTRAINT waiting_list_field_type_known CHECK (field_type IN (
        'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'CHOICE', 'BIRTH_DATE'));

ALTER TABLE ember_schema.attendance_template_field
    ADD CONSTRAINT attendance_template_field_type_known CHECK (field_type IN (
        'TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'TIME', 'BOOLEAN', 'CHOICE', 'URL',
        'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP'));

ALTER TABLE ember_schema.event_field
    ADD CONSTRAINT event_field_type_known CHECK (field_type IN (
        'TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'TIME', 'BOOLEAN', 'CHOICE', 'URL', 'LOCATION',
        'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP', 'MEMBER_OF_TYPE',
        'MEMBER_LIST_OF_TYPE', 'MEMBER_OF_TAG', 'MEMBER_LIST_OF_TAG'));

ALTER TABLE ember_schema.event_template_field
    ADD CONSTRAINT event_template_field_type_known CHECK (field_type IN (
        'TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'TIME', 'BOOLEAN', 'CHOICE', 'URL', 'LOCATION',
        'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP', 'MEMBER_OF_TYPE',
        'MEMBER_LIST_OF_TYPE', 'MEMBER_OF_TAG', 'MEMBER_LIST_OF_TAG'));

ALTER TABLE ember_schema.event_registration_field
    ADD CONSTRAINT event_registration_field_type_known CHECK (field_type IN (
        'TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'TIME', 'BOOLEAN', 'CHOICE', 'URL', 'LOCATION',
        'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP', 'MEMBER_OF_TYPE',
        'MEMBER_LIST_OF_TYPE', 'MEMBER_OF_TAG', 'MEMBER_LIST_OF_TAG'));

ALTER TABLE ember_schema.event_template_registration_field
    ADD CONSTRAINT event_template_registration_field_type_known CHECK (field_type IN (
        'TEXT', 'LONG_TEXT', 'NUMBER', 'DATE', 'TIME', 'BOOLEAN', 'CHOICE', 'URL', 'LOCATION',
        'MEMBER', 'MEMBER_LIST', 'MEMBER_OF_GROUP', 'MEMBER_LIST_OF_GROUP', 'MEMBER_OF_TYPE',
        'MEMBER_LIST_OF_TYPE', 'MEMBER_OF_TAG', 'MEMBER_LIST_OF_TAG'));

COMMENT ON COLUMN ember_schema.profile_field.field_type
    IS 'What kind of answer it takes, by its shared type name: TEXT, NUMBER, DATE, BOOLEAN, CHOICE, AGE, BIRTH_DATE, EXPIRY_DATE, SECTION or SPACER.';
COMMENT ON COLUMN ember_schema.attendance_template_field.field_type
    IS 'What kind of answer it takes, by its shared type name, such as TEXT, CHOICE, TIME or MEMBER_OF_GROUP.';
COMMENT ON COLUMN ember_schema.event_field.field_type
    IS 'What kind of answer it takes, by its shared type name, such as TEXT, NUMBER, LOCATION or MEMBER_LIST_OF_GROUP.';
COMMENT ON COLUMN ember_schema.event_registration_field.field_type
    IS 'What kind of answer it takes, by its shared type name, such as TEXT, NUMBER, CHOICE or MEMBER.';

ALTER TABLE ember_schema.movement_flow
    ADD COLUMN skip_member_receipt BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.movement_flow.skip_member_receipt
    IS 'Whether a step in which the member confirms receiving a piece is confirmed for them as soon as a movement reaches it, instead of waiting for them.';

COMMENT ON COLUMN ember_schema.item_movement_log.ack_kind
    IS 'CONFIRMED when the party that owns the step said so itself, ASSERTED when the station said so for an owner that does not use Ember, FORCED when the flow owner overrode a party that could have answered and did not, CORRECTED when somebody put the movement where it should have been, AUTO_CONFIRMED when the chain confirmed a member''s receipt for them as soon as it was reached.';

WITH renamed AS (SELECT id,
                        jsonb_set(
                                jsonb_set(data, '{link,route}', to_jsonb(CASE data -> 'link' ->> 'route'
                                    WHEN 'inventory-lending-detail' THEN 'inventory-lending-request'
                                    WHEN 'lending-request' THEN 'inventory-lending-request'
                                    WHEN 'station-members-documents' THEN 'member-documents'
                                    WHEN 'station-settings' THEN 'station-storage'
                                    WHEN 'form-detail' THEN 'forms-fill'
                                    WHEN 'forms' THEN 'forms-list'
                                    END)),
                                '{link,routeParams}',
                                CASE
                                    WHEN data -> 'link' ->> 'route' = 'station-settings' THEN '{}'::JSONB
                                    ELSE coalesce(data -> 'link' -> 'routeParams', '{}'::JSONB)
                                    END) AS data
                 FROM ember_schema.notification
                 WHERE data -> 'link' ->> 'route' IN
                       ('inventory-lending-detail', 'lending-request', 'station-members-documents',
                        'station-settings', 'form-detail', 'forms'))
UPDATE ember_schema.notification n
SET data      = renamed.data,
    dedup_key = CASE WHEN n.dedup_key IS NOT NULL THEN md5(n.type || renamed.data::TEXT) END
FROM renamed
WHERE renamed.id = n.id;

ALTER TABLE ember_schema.profile_field_change
    ALTER COLUMN changed_by DROP NOT NULL;

ALTER TABLE ember_schema.profile_field_change
    ADD COLUMN changed_by_account_id INTEGER REFERENCES ember_schema.account (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.profile_field_change.changed_by
    IS 'The member who made the change, or null when they have no membership at the station of the member whose answer changed, as an association manager often has not.';
COMMENT ON COLUMN ember_schema.profile_field_change.changed_by_account_id
    IS 'The account that made the change when it has no membership at that station, so the history can still name them; null whenever the member column names the author.';

ALTER TABLE ember_schema.storage_backend_audit
    ADD COLUMN cluster_id INTEGER NULL REFERENCES ember_schema.cluster(id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.storage_backend_audit.cluster_id
    IS 'The association whose history the row belongs to: a change to its own storage or its decision about it, or a move it made for one of its stations, which then names the station as well. Set to null when the association is deleted, so the row stays as proof of what was done.';

CREATE INDEX idx_storage_backend_audit_cluster_ts
    ON ember_schema.storage_backend_audit (cluster_id, ts DESC);

ALTER TABLE ember_schema.member_document
    ADD COLUMN uploader_account_id INTEGER REFERENCES ember_schema.account (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.member_document.uploader_account_id
    IS 'The account that filed the document from the association, which holds no membership at the document''s station. Empty for a document filed at the station, which names its uploader as a member instead.';

ALTER TABLE ember_schema.member_document_member
    DROP CONSTRAINT member_document_member_pkey;

ALTER TABLE ember_schema.member_document_member
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE ember_schema.member_document_member
    ADD COLUMN departed_name TEXT;

ALTER TABLE ember_schema.member_document_member
    ADD CONSTRAINT member_document_member_once UNIQUE (document_id, member_id);

ALTER TABLE ember_schema.member_document_member
    ADD CONSTRAINT member_document_member_names_somebody CHECK (member_id IS NOT NULL OR departed_name IS NOT NULL);

COMMENT ON TABLE ember_schema.member_document_member
    IS 'Which members a document is bound to, or the name of one who was deleted while the document was kept for the record. A document with nobody left on it is deleted with the last one.';
COMMENT ON COLUMN ember_schema.member_document_member.member_id
    IS 'The member the document is about. Empty once that member was deleted and only their name is left.';
COMMENT ON COLUMN ember_schema.member_document_member.departed_name
    IS 'The name of a member who was deleted while the document was kept for the record, written when the link to them went. It keeps the document a member''s paperwork rather than the station''s own.';
