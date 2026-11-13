/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

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
     * Opens a station chat.
     *
     * @param stationId        the station
     * @param name             its name
     * @param audience         whom it is for
     * @param groupId          the group of a group chat, or null
     * @param tagId            the tag of a tag chat, or null
     * @param includeGuardians whether the guardians of its people are members too
     * @return the chat
     */
    public Conversation create(
            int stationId,
            String name,
            ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            boolean includeGuardians) {
        return query("""
                INSERT INTO conversation AS c (kind, name, station_id, audience, audience_group_id, audience_tag_id, include_guardians)
                VALUES ('STATION', :name, :station_id, :audience, :group_id, :tag_id, :include_guardians)
                RETURNING %s;""", Conversation.COLUMNS)
                .single(call().bind("name", name)
                        .bind("station_id", stationId)
                        .bind("audience", audience)
                        .bind("group_id", groupId)
                        .bind("tag_id", tagId)
                        .bind("include_guardians", includeGuardians))
                .map(Conversation.map())
                .first()
                .orElseThrow();
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
                WITH chat AS (
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
                ),
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
                WHERE %s;""", MessagePeopleRepository.USABLE)
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
}
