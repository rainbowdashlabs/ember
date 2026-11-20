/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.StationRef;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.PermissionHolderSql;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Station chats: opening them, finding a station's chats, and who belongs in one.
 *
 * <p>Who belongs in a chat is read from the station on every call, never remembered: the current
 * members its audience takes in whose account can use messages (an audience naming nobody is the
 * station without its trial members), and, where the chat takes guardians in, the current guardians
 * of those people whatever their own messages allow.
 */
@Singleton
public class StationChatRepository {

    /**
     * Two common table expressions for the chat bound as {@code :id}: {@code chat}, its row, and
     * {@code people}, the current station members of its audience with their account, if any. An
     * audience naming nobody is the station without its trial members; one that lost part of what it
     * named takes in nobody.
     */
    private static final String MEMBER_COUNT = """
            (SELECT count(*) FROM conversation_member m
             WHERE m.conversation_id = c.id AND m.left_at IS NULL) AS member_count""";

    private static final String PEOPLE = """
            chat AS (
                SELECT * FROM conversation WHERE id = :id AND kind = 'STATION'
            ),
            people AS (
                SELECT sm.id AS member_id, sm.account_id
                FROM chat c
                JOIN station_member sm ON sm.station_id = c.station_id AND sm.former = FALSE
                WHERE CASE
                    WHEN NOT c.audience_named THEN sm.user_type <> 'TRIAL'
                    WHEN %s THEN FALSE
                    ELSE %s
                END
            )""".formatted(
            StationScope.audienceGone("c"), RestrictionSql.admits(RestrictionType.STATION_CHAT, "c.id", "sm.id"));

    /**
     * Opens a station chat. Whom it is for is set apart, as its restrictions.
     *
     * @param stationId        the station
     * @param name             its name
     * @param includeGuardians whether the guardians of its people are members too
     * @param audienceNamed    whether its audience names anybody, rather than the whole station
     * @param audienceKinds    how many user types, groups and tags its audience names
     * @return the chat
     */
    public Conversation create(
            int stationId, String name, boolean includeGuardians, boolean audienceNamed, int audienceKinds) {
        return query("""
                INSERT INTO conversation AS c (kind, name, station_id, include_guardians, audience_named, audience_kinds)
                VALUES ('STATION', :name, :station_id, :include_guardians, :audience_named, :audience_kinds)
                RETURNING %s;""", Conversation.COLUMNS)
                .single(call().bind("name", name)
                        .bind("station_id", stationId)
                        .bind("include_guardians", includeGuardians)
                        .bind("audience_named", audienceNamed)
                        .bind("audience_kinds", audienceKinds))
                .map(Conversation.map())
                .first()
                .orElseThrow();
    }

    /**
     * Changes a station chat's name, whether it takes guardians in, and whether its audience names
     * anybody. Whom it is for is set apart, as its restrictions.
     *
     * @param conversationId   the chat
     * @param name             its name
     * @param includeGuardians whether the guardians of its people are members too
     * @param audienceNamed    whether its audience names anybody, rather than the whole station
     * @param audienceKinds    how many user types, groups and tags its audience names
     */
    public void update(
            int conversationId, String name, boolean includeGuardians, boolean audienceNamed, int audienceKinds) {
        query("""
                UPDATE conversation
                SET name = :name, include_guardians = :include_guardians, audience_named = :audience_named,
                    audience_kinds = :audience_kinds
                WHERE id = :id AND kind = 'STATION';""")
                .single(call().bind("id", conversationId)
                        .bind("name", name)
                        .bind("include_guardians", includeGuardians)
                        .bind("audience_named", audienceNamed)
                        .bind("audience_kinds", audienceKinds))
                .update();
    }

    /**
     * Every chat of a station with how many take part, the open ones first, then the newest.
     *
     * @param stationId the station
     * @return the chats
     */
    public List<Counted> counted(int stationId) {
        return query("""
                SELECT %s, %s FROM conversation c
                WHERE c.kind = 'STATION' AND c.station_id = :station_id
                ORDER BY c.closed_at IS NOT NULL, c.created_at DESC, c.id DESC;""", Conversation.COLUMNS, MEMBER_COUNT)
                .single(call().bind("station_id", stationId))
                .map(Counted.map())
                .all();
    }

    /**
     * One of a station's chats with how many take part.
     *
     * @param stationId      the station
     * @param conversationId the chat
     * @return the chat, or empty where it is gone
     */
    public Optional<Counted> counted(int stationId, int conversationId) {
        return query("""
                SELECT %s, %s FROM conversation c
                WHERE c.kind = 'STATION' AND c.station_id = :station_id AND c.id = :id;""", Conversation.COLUMNS, MEMBER_COUNT)
                .single(call().bind("station_id", stationId).bind("id", conversationId))
                .map(Counted.map())
                .first();
    }

    /**
     * A station chat with how many take part in it right now.
     *
     * @param conversation the chat
     * @param members      how many take part
     */
    public record Counted(Conversation conversation, int members) {
        static RowMapping<Counted> map() {
            return row -> new Counted(Conversation.map().map(row), row.getInt("member_count"));
        }
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
                WHERE c.kind = 'STATION' AND c.station_id = :station_id AND %s
                ORDER BY c.created_at, c.id;""", Conversation.COLUMNS, ConversationRepository.STILL_HERE)
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
                SELECT DISTINCT c.station_id FROM conversation c
                WHERE c.kind = 'STATION' AND %s ORDER BY c.station_id;""", ConversationRepository.STILL_HERE)
                .single()
                .map(row -> row.getInt("station_id"))
                .all();
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
     * their type. A chat whose audience lost part of what it named keeps its members, but only while
     * they are still at the station.
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
     * <p>The people of its audience are the current members its restrictions take in, or the
     * station's current members without the trial members where it names nobody. Of those, the ones
     * with an account that can use messages are in. Where the chat takes guardians in, the current
     * guardians of every one of those people are in too, also of people without an account or without
     * messages, as long as the guardian's own account can use messages. A chat whose audience lost
     * part of what it named has nobody.
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
     * Whether a station chat takes nothing new: it is closed, or its station is being moved to
     * another installation or has moved there. Read with a lock that waits for a close running
     * right now and holds a close back until the running transaction ends.
     *
     * @param conversationId the chat
     * @return whether it is closed
     */
    public boolean closedNow(int conversationId) {
        return query("""
                SELECT (c.closed_at IS NOT NULL OR s.read_only_for_transfer OR s.moved_away_at IS NOT NULL) AS closed
                FROM conversation c JOIN station s ON s.id = c.station_id
                WHERE c.id = :id FOR SHARE OF c;""")
                .single(call().bind("id", conversationId))
                .map(row -> row.getBoolean("closed"))
                .first()
                .orElse(false);
    }

    /**
     * The same as {@link #closedNow}, read with the lock a write to the chat's row takes anyway.
     * The sending of a message touches that row afterwards, and a share lock would have to be raised
     * to that, which two senders at once would wait on from each other.
     *
     * @param conversationId the chat
     * @return whether it is closed
     */
    public boolean closedNowForSending(int conversationId) {
        return query("""
                SELECT (c.closed_at IS NOT NULL OR s.read_only_for_transfer OR s.moved_away_at IS NOT NULL) AS closed
                FROM conversation c JOIN station s ON s.id = c.station_id
                WHERE c.id = :id FOR NO KEY UPDATE OF c;""")
                .single(call().bind("id", conversationId))
                .map(row -> row.getBoolean("closed"))
                .first()
                .orElse(false);
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

    /**
     * Whether an account handles a station's chats there: it is a current member of the station
     * holding the permission to, directly, through its type, a group, or a wider right.
     *
     * @param accountId the account
     * @param stationId the station
     * @return whether it may remove messages and handle reports there
     */
    public boolean moderates(int accountId, int stationId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1 FROM station_member sm
                    WHERE sm.station_id = :station_id AND sm.account_id = :account_id AND sm.former = FALSE
                    AND (%s)
                ) AS moderates;""", PermissionHolderSql.HOLDS_PERMISSION)
                .single(PermissionHolderSql.bind(
                        call().bind("station_id", stationId).bind("account_id", accountId),
                        Set.of(StationPermission.MESSAGE_CHAT_MODERATE)))
                .map(row -> row.getBoolean("moderates"))
                .first()
                .orElse(false);
    }

    /**
     * The members the given accounts are at a station right now.
     *
     * @param stationId  the station
     * @param accountIds the accounts
     * @return each account that is a current member there, with its member's identity
     */
    public Map<Integer, UUID> memberUids(int stationId, Collection<Integer> accountIds) {
        if (accountIds.isEmpty()) return Map.of();
        return query("""
                SELECT account_id, uid FROM station_member
                WHERE station_id = :station_id AND former = FALSE AND account_id = ANY(:ids);""")
                .single(call().bind("station_id", stationId)
                        .bind("ids", List.copyOf(accountIds), PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("account_id"), row.get("uid", UUID_STRING)))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * The member an account is at a station right now.
     *
     * @param accountId the account
     * @param stationId the station
     * @return the member, or empty where the account is no current member there
     */
    public Optional<Integer> memberAt(int accountId, int stationId) {
        return query("""
                SELECT id FROM station_member
                WHERE account_id = :account_id AND station_id = :station_id AND former = FALSE;""")
                .single(call().bind("account_id", accountId).bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .first();
    }

    /**
     * The station a conversation belongs to.
     *
     * @param conversationId the conversation
     * @return the station of a station chat, empty for any other conversation
     */
    public Optional<Integer> stationOf(int conversationId) {
        return query("""
                SELECT station_id FROM conversation WHERE id = :id AND kind = 'STATION';""")
                .single(call().bind("id", conversationId))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * Whether a station has moved to another installation, which takes its chats along and leaves
     * the copy here to nobody.
     *
     * @param stationId the station
     * @return whether it moved away
     */
    public boolean movedAway(int stationId) {
        return query("""
                SELECT moved_away_at IS NOT NULL AS moved FROM station WHERE id = :id;""")
                .single(call().bind("id", stationId))
                .map(row -> row.getBoolean("moved"))
                .first()
                .orElse(true);
    }

    /**
     * Whether a station is being moved to another installation or has moved there, which leaves
     * nothing in its chats to change here.
     *
     * @param stationId the station
     * @return whether it is moving or gone
     */
    public boolean moving(int stationId) {
        return query("""
                SELECT read_only_for_transfer OR moved_away_at IS NOT NULL AS moving FROM station WHERE id = :id;""")
                .single(call().bind("id", stationId))
                .map(row -> row.getBoolean("moving"))
                .first()
                .orElse(true);
    }
}
