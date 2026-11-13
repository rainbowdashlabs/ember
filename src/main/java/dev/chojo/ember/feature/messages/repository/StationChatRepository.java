/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.StationChatSummary;
import dev.chojo.ember.feature.messages.entity.StationRef;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Station chats: opening them, finding a station's chats, and who belongs in one.
 *
 * <p>Who belongs in a chat is read from the station on every call, never remembered: the current
 * members of the station, group or tag whose account can use messages, trial members only where a
 * group or tag names them, and, where the chat takes guardians in, the current guardians of those
 * people whatever their own messages allow.
 */
@Singleton
public class StationChatRepository {

    /**
     * Two common table expressions for the chat bound as {@code :id}: {@code chat}, its row, and
     * {@code people}, the current station members of its audience with their account, if any.
     */
    /** The first key of the advisory lock {@link #lockOpening} takes, which names what it guards. */
    private static final int OPENING_LOCK = 7_301;

    private static final String PEOPLE = """
            chat AS (
                SELECT * FROM conversation WHERE id = :id AND kind = 'STATION'
            ),
            people AS (
                SELECT sm.id AS member_id, sm.account_id
                FROM chat c
                JOIN station_member sm ON sm.station_id = c.station_id AND sm.former = FALSE
                WHERE CASE c.audience
                    WHEN 'STATION' THEN sm.user_type <> 'TRIAL'
                    WHEN 'GROUP' THEN EXISTS (
                        SELECT 1 FROM member_group_entry e
                        WHERE e.group_id = c.audience_group_id AND e.member_id = sm.id)
                    WHEN 'TAG' THEN EXISTS (
                        SELECT 1 FROM user_tag_entry e
                        WHERE e.tag_id = c.audience_tag_id AND e.member_id = sm.id)
                    ELSE FALSE
                END
            )""";

    /**
     * Opens a station chat.
     *
     * @param stationId        the station
     * @param name             its name
     * @param audience         whom it is for
     * @param groupId          the group of a group chat, or null
     * @param tagId            the tag of a tag chat, or null
     * @param includeGuardians whether the guardians of its people are members too
     * @return the chat, or empty where its audience has an open chat already
     */
    public Optional<Conversation> create(
            int stationId,
            String name,
            ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            boolean includeGuardians) {
        return query("""
                INSERT INTO conversation AS c (kind, name, station_id, audience, audience_group_id, audience_tag_id, include_guardians)
                VALUES ('STATION', :name, :station_id, :audience, :group_id, :tag_id, :include_guardians)
                ON CONFLICT DO NOTHING
                RETURNING %s;""", Conversation.COLUMNS)
                .single(call().bind("name", name)
                        .bind("station_id", stationId)
                        .bind("audience", audience)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId)
                        .bind("include_guardians", includeGuardians))
                .map(Conversation.map())
                .first();
    }

    /**
     * Every chat of a station, open or closed, the oldest first.
     *
     * @param stationId the station
     * @return its chats
     */
    public List<Conversation> ofStation(int stationId) {
        return query("""
                SELECT %s FROM conversation c
                WHERE c.kind = 'STATION' AND c.station_id = :station_id
                ORDER BY c.created_at, c.id;""", Conversation.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(Conversation.map())
                .all();
    }

    /**
     * A station chat, locked for the rest of the running transaction so that two reconciliations
     * of it run one after the other.
     *
     * @param conversationId the chat
     * @return the chat as it is now, or empty where it is gone
     */
    public Optional<Conversation> lock(int conversationId) {
        return query("""
                SELECT %s FROM conversation c
                WHERE c.id = :id AND c.kind = 'STATION'
                FOR NO KEY UPDATE;""", Conversation.COLUMNS)
                .single(call().bind("id", conversationId))
                .map(Conversation.map())
                .first();
    }

    /**
     * The stations that have at least one chat.
     *
     * @return their ids
     */
    public List<Integer> stationsWithChats() {
        return query("""
                SELECT DISTINCT station_id FROM conversation WHERE kind = 'STATION' ORDER BY station_id;""").single().map(row -> row.getInt("station_id")).all();
    }

    /**
     * The stations an account is a current member of, which is where a change to the account
     * itself can move it into or out of chats.
     *
     * @param accountId the account
     * @return the stations
     */
    public List<Integer> stationsOf(int accountId) {
        return query("""
                SELECT DISTINCT station_id FROM station_member
                WHERE account_id = :account_id AND former = FALSE
                ORDER BY station_id;""")
                .single(call().bind("account_id", accountId))
                .map(row -> row.getInt("station_id"))
                .all();
    }

    /**
     * The accounts of everybody at a station right now whose account can use messages, whatever
     * their type. A chat that lost its group or tag keeps its members, but only while they are still
     * at the station.
     *
     * @param stationId the station
     * @return the account ids
     */
    public Set<Integer> stationPeople(int stationId) {
        return query("""
                SELECT a.id FROM station_member sm
                JOIN account a ON a.id = sm.account_id
                WHERE sm.station_id = :station_id AND sm.former = FALSE AND %s;""", MessagePeopleRepository.USABLE)
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all()
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * The accounts that belong in a station chat right now.
     *
     * <p>The people of its audience are the current members of the station (trial members left out),
     * of its group or of its tag. Of those, the ones with an account that can use messages are in.
     * Where the chat takes guardians in, the current guardians of every one of those people are in
     * too, also of people without an account or without messages, as long as the guardian's own
     * account can use messages. A chat whose group or tag is gone has nobody.
     *
     * @param conversationId the chat
     * @return the account ids
     */
    public Set<Integer> audience(int conversationId) {
        return query("""
                WITH %s,
                accounts AS (
                    SELECT account_id FROM people WHERE account_id IS NOT NULL
                    UNION
                    SELECT guardian.account_id
                    FROM people p
                    JOIN member_manager mm ON mm.managed_id = p.member_id
                    JOIN station_member guardian
                        ON guardian.id = mm.manager_id AND guardian.former = FALSE AND guardian.account_id IS NOT NULL
                    WHERE (SELECT include_guardians FROM chat)
                )
                SELECT a.id FROM accounts x JOIN account a ON a.id = x.account_id
                WHERE %s;""", PEOPLE, MessagePeopleRepository.USABLE)
                .single(call().bind("id", conversationId))
                .map(row -> row.getInt("id"))
                .all()
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * Closes a chat that is still open.
     *
     * @param conversationId the chat
     * @param byAccountId    who closes it, or null where nobody does
     * @return whether it was open until now
     */
    public boolean close(int conversationId, @Nullable Integer byAccountId) {
        return query("""
                UPDATE conversation SET closed_at = now(), closed_by_account_id = :by
                WHERE id = :id AND kind = 'STATION' AND closed_at IS NULL;""")
                .single(call().bind("id", conversationId).bind("by", byAccountId))
                .update()
                .changed();
    }

    /**
     * Every chat of a station with its audience's name and how many take part, the open ones first,
     * then the newest.
     *
     * @param stationId the station
     * @return the chats
     */
    public List<StationChatSummary> summaries(int stationId) {
        return query("""
                SELECT %s,
                    coalesce(g.name, t.name) AS audience_name,
                    (SELECT count(*) FROM conversation_member m
                     WHERE m.conversation_id = c.id AND m.left_at IS NULL) AS member_count
                FROM conversation c
                LEFT JOIN member_group g ON g.id = c.audience_group_id
                LEFT JOIN user_tag t ON t.id = c.audience_tag_id
                WHERE c.kind = 'STATION' AND c.station_id = :station_id
                ORDER BY c.closed_at IS NOT NULL, c.created_at DESC, c.id DESC;""", Conversation.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationChatSummary.map())
                .all();
    }

    /**
     * The open chat a station has for an audience.
     *
     * @param stationId the station
     * @param audience  whom the chat is for
     * @param groupId   the group of a group chat, or null
     * @param tagId     the tag of a tag chat, or null
     * @return the open chat, or empty where there is none
     */
    public Optional<Conversation> findOpen(
            int stationId, ChatAudience audience, @Nullable Integer groupId, @Nullable Integer tagId) {
        return query("""
                SELECT %s FROM conversation c
                WHERE c.kind = 'STATION' AND c.station_id = :station_id AND c.audience = :audience
                AND c.closed_at IS NULL
                AND c.audience_group_id IS NOT DISTINCT FROM :group_id::int
                AND c.audience_tag_id IS NOT DISTINCT FROM :tag_id::int;""", Conversation.COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("audience", audience)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId))
                .map(Conversation.map())
                .first();
    }

    /**
     * Whether a group belongs to a station.
     *
     * @param groupId   the group
     * @param stationId the station
     * @return true for one of the station's groups
     */
    public boolean groupOf(int groupId, int stationId) {
        return query("""
                SELECT id FROM member_group WHERE id = :id AND station_id = :station_id FOR KEY SHARE;""")
                .single(call().bind("id", groupId).bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    /**
     * Whether a tag belongs to a station.
     *
     * @param tagId     the tag
     * @param stationId the station
     * @return true for one of the station's tags
     */
    public boolean tagOf(int tagId, int stationId) {
        return query("""
                SELECT id FROM user_tag WHERE id = :id AND station_id = :station_id FOR KEY SHARE;""")
                .single(call().bind("id", tagId).bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    /**
     * Holds the station's chats against a second opening or reopening for the rest of the running
     * transaction, so two managers acting at once cannot both find an audience without an open chat.
     *
     * @param stationId the station
     */
    public void lockOpening(int stationId) {
        query("""
                SELECT 1 AS locked FROM (SELECT pg_advisory_xact_lock(%d, :station_id)) l;""", OPENING_LOCK)
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("locked"))
                .first();
    }

    /**
     * Whether a station chat is closed, read with a lock that waits for a close running right now
     * and holds a close back until the running transaction ends.
     *
     * @param conversationId the chat
     * @return whether it is closed
     */
    public boolean closedNow(int conversationId) {
        return query("""
                SELECT closed_at IS NOT NULL AS closed FROM conversation WHERE id = :id FOR SHARE;""")
                .single(call().bind("id", conversationId))
                .map(row -> row.getBoolean("closed"))
                .first()
                .orElse(false);
    }

    /**
     * Whether a station chat is closed, read with the lock a write to the chat's row takes anyway.
     * The sending of a message touches that row afterwards, and a share lock would have to be raised
     * to that, which two senders at once would wait on from each other.
     *
     * @param conversationId the chat
     * @return whether it is closed
     */
    public boolean closedNowForSending(int conversationId) {
        return query("""
                SELECT closed_at IS NOT NULL AS closed FROM conversation WHERE id = :id FOR NO KEY UPDATE;""")
                .single(call().bind("id", conversationId))
                .map(row -> row.getBoolean("closed"))
                .first()
                .orElse(false);
    }

    /**
     * One of a station's chats with its audience's name and how many take part.
     *
     * @param conversationId the chat
     * @return the chat, or empty where it is gone
     */
    public Optional<StationChatSummary> summary(int conversationId) {
        return query("""
                SELECT %s,
                    coalesce(g.name, t.name) AS audience_name,
                    (SELECT count(*) FROM conversation_member m
                     WHERE m.conversation_id = c.id AND m.left_at IS NULL) AS member_count
                FROM conversation c
                LEFT JOIN member_group g ON g.id = c.audience_group_id
                LEFT JOIN user_tag t ON t.id = c.audience_tag_id
                WHERE c.kind = 'STATION' AND c.id = :id;""", Conversation.COLUMNS)
                .single(call().bind("id", conversationId))
                .map(StationChatSummary.map())
                .first();
    }

    /**
     * Renames a station chat.
     *
     * @param conversationId the chat
     * @param name           its new name
     */
    public void rename(int conversationId, String name) {
        query("""
                UPDATE conversation SET name = :name WHERE id = :id AND kind = 'STATION';""").single(call().bind("id", conversationId).bind("name", name)).update();
    }

    /**
     * Takes the guardians of a group or tag chat's people in, or leaves them out.
     *
     * @param conversationId the chat
     * @param include        whether they are members
     */
    public void setIncludeGuardians(int conversationId, boolean include) {
        query("""
                UPDATE conversation SET include_guardians = :include WHERE id = :id AND kind = 'STATION';""")
                .single(call().bind("id", conversationId).bind("include", include))
                .update();
    }

    /**
     * Opens a closed chat again.
     *
     * @param conversationId the chat
     * @return whether it was closed until now
     */
    public boolean reopen(int conversationId) {
        return query("""
                UPDATE conversation SET closed_at = NULL, closed_by_account_id = NULL
                WHERE id = :id AND kind = 'STATION' AND closed_at IS NOT NULL;""").single(call().bind("id", conversationId)).update().changed();
    }

    /**
     * The stations the given ones are, by id.
     *
     * @param stationIds the stations
     * @return each station that exists
     */
    public Map<Integer, StationRef> stations(Collection<Integer> stationIds) {
        if (stationIds.isEmpty()) return Map.of();
        return query("""
                SELECT id, uid, name FROM station WHERE id = ANY(:ids);""")
                .single(call().bind("ids", List.copyOf(stationIds), PostgreSqlTypes.INTEGER))
                .map(row -> new StationRef(row.getInt("id"), row.get("uid", UUID_STRING), row.getString("name")))
                .all()
                .stream()
                .collect(Collectors.toMap(StationRef::id, Function.identity()));
    }

    /**
     * For a chat that takes guardians in, whom each guardian in it is there for: the names of the
     * people of its audience they look after.
     *
     * @param conversationId the chat
     * @return the names per guardian account, empty for a chat without guardians
     */
    public Map<Integer, List<String>> guardiansFor(int conversationId) {
        return query("""
                WITH %s
                SELECT guardian.account_id AS guardian_account_id,
                    coalesce(nullif(btrim(a.first_name || ' ' || a.last_name), ''), sm.display_name) AS name
                FROM people p
                JOIN station_member sm ON sm.id = p.member_id
                LEFT JOIN account a ON a.id = sm.account_id
                JOIN member_manager mm ON mm.managed_id = p.member_id
                JOIN station_member guardian
                    ON guardian.id = mm.manager_id AND guardian.former = FALSE AND guardian.account_id IS NOT NULL
                WHERE (SELECT include_guardians FROM chat)
                ORDER BY guardian.account_id, name;""", PEOPLE)
                .single(call().bind("id", conversationId))
                .map(row -> Map.entry(row.getInt("guardian_account_id"), row.getString("name")))
                .all()
                .stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }
}
