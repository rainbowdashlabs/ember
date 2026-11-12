/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.ConversationOverview;
import dev.chojo.ember.feature.messages.entity.Membership;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Conversations and the accounts taking part in them.
 *
 * <p>A membership is kept when somebody leaves, with the time they left, so a direct conversation
 * can come back to both sides with its next message and a group can take somebody back. Only an
 * active membership opens a conversation.
 */
@Singleton
public class ConversationRepository {

    /**
     * The condition a message aliased {@code msg} meets when it counts as unread for the member
     * aliased {@code m}: written by somebody else whom the member has not blocked, still standing,
     * and after the member's read marker.
     */
    private static final String UNREAD = """
            msg.conversation_id = m.conversation_id
            AND msg.id > coalesce(m.last_read_message_id, 0)
            AND msg.system_kind IS NULL AND msg.deleted_at IS NULL
            AND msg.author_account_id IS DISTINCT FROM m.account_id
            AND NOT EXISTS (
                SELECT 1 FROM account_block b
                WHERE b.blocker_account_id = m.account_id AND b.blocked_account_id = msg.author_account_id)""";

    /**
     * The key that keeps one direct conversation per pair of accounts.
     *
     * @param first  one account
     * @param second the other
     * @return both ids, the lower first, joined by a colon
     */
    public static String directPair(int first, int second) {
        return "%d:%d".formatted(Math.min(first, second), Math.max(first, second));
    }

    /**
     * The direct conversation of a pair, started if there is none yet. Two people starting it at
     * the same moment end up in the same one.
     *
     * @param first  one account
     * @param second the other
     * @return the conversation
     */
    public Conversation directOf(int first, int second) {
        query("""
                INSERT INTO conversation(kind, direct_pair) VALUES ('DIRECT', :pair)
                ON CONFLICT (direct_pair) DO NOTHING;""").single(call().bind("pair", directPair(first, second))).insert();
        return findDirect(first, second).orElseThrow();
    }

    /**
     * The direct conversation of a pair, where they have one.
     *
     * @param first  one account
     * @param second the other
     * @return the conversation, or empty where the two never wrote to each other
     */
    public Optional<Conversation> findDirect(int first, int second) {
        return query("""
                SELECT %s FROM conversation c WHERE c.direct_pair = :pair;""", Conversation.COLUMNS)
                .single(call().bind("pair", directPair(first, second)))
                .map(Conversation.map())
                .first();
    }

    /**
     * Starts a group.
     *
     * @param ownerId the account starting it, which owns it
     * @param name    its name, or null to call it after its members
     * @return the group
     */
    public Conversation createGroup(int ownerId, @Nullable String name) {
        return query("""
                INSERT INTO conversation(kind, name, owner_account_id) VALUES ('GROUP', :name, :owner_id)
                RETURNING id, uid, kind, name, owner_account_id, created_at, last_activity_at;""")
                .single(call().bind("name", name).bind("owner_id", ownerId))
                .map(Conversation.map())
                .first()
                .orElseThrow();
    }

    /**
     * A conversation by its identity.
     *
     * @param uid the identity
     * @return the conversation, or empty where there is none
     */
    public Optional<Conversation> findByUid(UUID uid) {
        return query("""
                SELECT %s FROM conversation c WHERE c.uid = :uid::uuid;""", Conversation.COLUMNS)
                .single(call().bind("uid", uid, UUID_STRING))
                .map(Conversation.map())
                .first();
    }

    /**
     * Lets an account take part, or take part again. Its read marker starts at the newest message,
     * so the history it can now read does not count as unread.
     *
     * @param conversationId the conversation
     * @param accountId      the account
     */
    public void join(int conversationId, int accountId) {
        query("""
                INSERT INTO conversation_member(conversation_id, account_id, last_read_message_id)
                VALUES (:conversation_id, :account_id,
                        (SELECT max(id) FROM message WHERE conversation_id = :conversation_id))
                ON CONFLICT (conversation_id, account_id) DO UPDATE SET
                    joined_at            = now(),
                    left_at              = NULL,
                    muted                = FALSE,
                    mailed_at            = NULL,
                    last_read_message_id = excluded.last_read_message_id
                WHERE conversation_member.left_at IS NOT NULL;""")
                .single(call().bind("conversation_id", conversationId).bind("account_id", accountId))
                .insert();
    }

    /**
     * Lets an account take part in a conversation it never took part in. One that left stays out:
     * a direct conversation is brought back to it by the next message, not by being opened.
     *
     * @param conversationId the conversation
     * @param accountId      the account
     */
    public void include(int conversationId, int accountId) {
        query("""
                INSERT INTO conversation_member(conversation_id, account_id) VALUES (:conversation_id, :account_id)
                ON CONFLICT (conversation_id, account_id) DO NOTHING;""")
                .single(call().bind("conversation_id", conversationId).bind("account_id", accountId))
                .insert();
    }

    /**
     * Everybody who ever took part in a conversation, whether they still do or left.
     *
     * @param conversationId the conversation
     * @return the memberships, the longest first
     */
    public List<Membership> everyMember(int conversationId) {
        return query("""
                SELECT %s
                FROM conversation_member m
                JOIN account a ON a.id = m.account_id
                WHERE m.conversation_id = :conversation_id
                ORDER BY m.joined_at, m.account_id;""", Membership.COLUMNS)
                .single(call().bind("conversation_id", conversationId))
                .map(Membership.map())
                .all();
    }

    /**
     * Brings everybody who left a direct conversation back into it, keeping where they had read to.
     *
     * @param conversationId the conversation
     * @return the accounts that came back
     */
    public List<Integer> returnAll(int conversationId) {
        return query("""
                UPDATE conversation_member SET left_at = NULL, joined_at = now()
                WHERE conversation_id = :conversation_id AND left_at IS NOT NULL
                RETURNING account_id;""")
                .single(call().bind("conversation_id", conversationId))
                .map(row -> row.getInt("account_id"))
                .all();
    }

    /**
     * Records that an account left a conversation or was removed from it.
     *
     * @param conversationId the conversation
     * @param accountId      the account
     * @return whether it was taking part until now
     */
    public boolean leave(int conversationId, int accountId) {
        return query("""
                UPDATE conversation_member SET left_at = now()
                WHERE conversation_id = :conversation_id AND account_id = :account_id AND left_at IS NULL;""")
                .single(call().bind("conversation_id", conversationId).bind("account_id", accountId))
                .update()
                .changed();
    }

    /**
     * An account's membership of a conversation, whether active or left.
     *
     * @param conversationId the conversation
     * @param accountId      the account
     * @return the membership, or empty where it never took part
     */
    public Optional<Membership> membership(int conversationId, int accountId) {
        return query("""
                SELECT %s
                FROM conversation_member m
                JOIN account a ON a.id = m.account_id
                WHERE m.conversation_id = :conversation_id AND m.account_id = :account_id;""", Membership.COLUMNS)
                .single(call().bind("conversation_id", conversationId).bind("account_id", accountId))
                .map(Membership.map())
                .first();
    }

    /**
     * The accounts taking part in conversations right now, the longest member first.
     *
     * @param conversationIds the conversations
     * @return their active memberships
     */
    public List<Membership> activeMembers(List<Integer> conversationIds) {
        return query("""
                SELECT %s
                FROM conversation_member m
                JOIN account a ON a.id = m.account_id
                WHERE m.conversation_id = ANY(:ids) AND m.left_at IS NULL
                ORDER BY m.conversation_id, m.joined_at, m.account_id;""", Membership.COLUMNS)
                .single(call().bind("ids", conversationIds, PostgreSqlTypes.INTEGER))
                .map(Membership.map())
                .all();
    }

    /**
     * The accounts taking part in one conversation right now, the longest member first.
     *
     * @param conversationId the conversation
     * @return its active memberships
     */
    public List<Membership> activeMembers(int conversationId) {
        return activeMembers(List.of(conversationId));
    }

    /**
     * Renames a group.
     *
     * @param conversationId the group
     * @param name           the new name, or null to call it after its members
     */
    public void rename(int conversationId, @Nullable String name) {
        query("""
                UPDATE conversation SET name = :name WHERE id = :id;""").single(call().bind("id", conversationId).bind("name", name)).update();
    }

    /**
     * Hands a group to another owner.
     *
     * @param conversationId the group
     * @param ownerId        the new owner, or null for none
     */
    public void setOwner(int conversationId, @Nullable Integer ownerId) {
        query("""
                UPDATE conversation SET owner_account_id = :owner_id WHERE id = :id;""")
                .single(call().bind("id", conversationId).bind("owner_id", ownerId))
                .update();
    }

    /**
     * Notes that something was written in a conversation, which moves it up the list.
     *
     * @param conversationId the conversation
     */
    public void touch(int conversationId) {
        query("""
                UPDATE conversation SET last_activity_at = now() WHERE id = :id;""").single(call().bind("id", conversationId)).update();
    }

    /**
     * Mutes or unmutes a conversation for one of its members.
     *
     * @param conversationId the conversation
     * @param accountId      the member
     * @param muted          whether it is muted
     */
    public void setMuted(int conversationId, int accountId, boolean muted) {
        query("""
                UPDATE conversation_member SET muted = :muted
                WHERE conversation_id = :conversation_id AND account_id = :account_id;""")
                .single(call().bind("conversation_id", conversationId)
                        .bind("account_id", accountId)
                        .bind("muted", muted))
                .update();
    }

    /**
     * Moves a member's read marker forward to a message. A marker never moves back, and reading
     * again makes the next unread stretch worth a mail again.
     *
     * @param conversationId the conversation
     * @param accountId      the member
     * @param messageId      the message read up to
     * @return whether the marker moved
     */
    public boolean markRead(int conversationId, int accountId, int messageId) {
        return query("""
                UPDATE conversation_member SET last_read_message_id = :message_id, mailed_at = NULL
                WHERE conversation_id = :conversation_id AND account_id = :account_id
                AND coalesce(last_read_message_id, 0) < :message_id;""")
                .single(call().bind("conversation_id", conversationId)
                        .bind("account_id", accountId)
                        .bind("message_id", messageId))
                .update()
                .changed();
    }

    /**
     * The conversations an account takes part in, the most recently active first.
     *
     * @param accountId the account
     * @return each with the account's own state and unread count
     */
    public List<ConversationOverview> overviews(int accountId) {
        return query("""
                SELECT %s, m.muted, m.last_read_message_id,
                    (SELECT count(*) FROM message msg WHERE %s) AS unread
                FROM conversation_member m
                JOIN conversation c ON c.id = m.conversation_id
                WHERE m.account_id = :account_id AND m.left_at IS NULL
                ORDER BY c.last_activity_at DESC, c.id DESC;""", Conversation.COLUMNS, UNREAD)
                .single(call().bind("account_id", accountId))
                .map(ConversationOverview.map())
                .all();
    }

    /**
     * How many messages an account has not read, muted conversations left out.
     *
     * @param accountId the account
     * @return the count for its badge
     */
    public int unreadTotal(int accountId) {
        return query("""
                SELECT count(*) AS unread
                FROM conversation_member m
                JOIN message msg ON %s
                WHERE m.account_id = :account_id AND m.left_at IS NULL AND m.muted = FALSE;""", UNREAD)
                .single(call().bind("account_id", accountId))
                .map(row -> row.getInt("unread"))
                .first()
                .orElse(0);
    }

    /**
     * Takes an account out of every conversation before it is deleted: its memberships go, the
     * direct conversations it was in no longer name it, and a conversation nobody is left in goes
     * with its messages.
     *
     * @param accountId the account being deleted
     */
    public void forgetAccount(int accountId) {
        query("""
                UPDATE conversation c SET direct_pair = NULL
                FROM conversation_member m
                WHERE m.conversation_id = c.id AND m.account_id = :account_id AND c.kind = 'DIRECT';""").single(call().bind("account_id", accountId)).update();
        query("""
                DELETE FROM conversation_member WHERE account_id = :account_id;""").single(call().bind("account_id", accountId)).delete();
        query("""
                DELETE FROM conversation c
                WHERE NOT EXISTS (SELECT 1 FROM conversation_member m WHERE m.conversation_id = c.id);""").single().delete();
    }
}
