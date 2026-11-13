/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.StationChatSummary;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A station's chats as its managers handle them: opening one for the whole station, a group or a
 * tag, renaming it, taking the guardians in or out, closing and reopening it.
 *
 * <p>A station has at most one open chat for the whole station, and each group and tag at most one,
 * so the same chat is never opened twice by accident. Opening, renaming, closing and reopening leave
 * a quiet line in the chat; who comes and goes with the audience does not. Who holds the permission
 * is checked by the route; everything here is held to the station named.
 */
@Singleton
public class StationChatService {
    private static final Logger log = LoggerFactory.getLogger(StationChatService.class);

    private final StationChatRepository chats;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final StationChatMembership membership;

    @Inject
    public StationChatService(
            StationChatRepository chats,
            ConversationRepository conversations,
            MessageRepository messages,
            StationChatMembership membership) {
        this.chats = chats;
        this.conversations = conversations;
        this.messages = messages;
        this.membership = membership;
    }

    /**
     * The station's chats, the open ones first.
     *
     * @param stationId the station
     * @return each with its audience's name and how many take part
     */
    public List<StationChatSummary> list(int stationId) {
        return chats.summaries(stationId);
    }

    /**
     * One of the station's chats as its list shows it.
     *
     * @param stationId       the station
     * @param conversationUid the chat
     * @return the chat
     */
    public StationChatSummary one(int stationId, UUID conversationUid) {
        Conversation chat = requireChat(stationId, conversationUid);
        return chats.summary(chat.id()).orElseThrow(MessageRefusal.STATION_CHAT_NOT_HERE::raise);
    }

    /**
     * Opens a chat and takes its audience in.
     *
     * @param stationId        the station
     * @param accountId        the manager opening it
     * @param name             its name
     * @param audience         whom it is for
     * @param groupId          the group of a group chat, or null
     * @param tagId            the tag of a tag chat, or null
     * @param includeGuardians whether the guardians of a group or tag's people are members too
     * @return the chat
     */
    public Conversation open(
            int stationId,
            int accountId,
            @Nullable String name,
            @Nullable ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            boolean includeGuardians) {
        String kept = requireName(name);
        Conversation chat = Transactions.call(() -> {
            chats.lockOpening(stationId);
            requireAudience(stationId, audience, groupId, tagId, includeGuardians);
            var created = chats.create(stationId, kept, audience, groupId, tagId, includeGuardians)
                    .orElseThrow(MessageRefusal.STATION_CHAT_TAKEN::raise);
            messages.writeSystem(created.id(), accountId, SystemKind.OPENED, null, kept);
            return created;
        });
        reconcileQuietly(chat);
        log.info(
                "Station chat {} opened at station {} for {} by account {}", chat.id(), stationId, audience, accountId);
        return chat;
    }

    /**
     * Renames a chat.
     *
     * @param stationId       the station
     * @param accountId       the manager renaming it
     * @param conversationUid the chat
     * @param name            its new name
     */
    public void rename(int stationId, int accountId, UUID conversationUid, @Nullable String name) {
        Conversation chat = requireChat(stationId, conversationUid);
        String kept = requireName(name);
        if (kept.equals(chat.name())) return;
        Transactions.run(() -> {
            chats.rename(chat.id(), kept);
            messages.writeSystem(chat.id(), accountId, SystemKind.RENAMED, null, kept);
            conversations.touch(chat.id());
        });
    }

    /**
     * Takes the guardians of a group or tag chat's people in, or leaves them out again.
     *
     * @param stationId       the station
     * @param conversationUid the chat
     * @param include         whether the guardians are members, refused when not said
     */
    public void includeGuardians(int stationId, UUID conversationUid, @Nullable Boolean include) {
        if (include == null) throw MessageRefusal.STATION_CHAT_GUARDIANS_UNSAID.raise();
        Conversation chat = requireChat(stationId, conversationUid);
        StationScope scope = scopeOf(chat);
        if (scope.audience() == ChatAudience.STATION) throw MessageRefusal.STATION_CHAT_GUARDIANS_NOT_TAKEN.raise();
        if (scope.includeGuardians() == include) return;
        chats.setIncludeGuardians(chat.id(), include);
        reconcileQuietly(chat);
    }

    /**
     * Closes a chat: it stays readable for its members and takes nothing new. A closed chat stays as
     * it is.
     *
     * @param stationId       the station
     * @param accountId       the manager closing it
     * @param conversationUid the chat
     */
    public void close(int stationId, int accountId, UUID conversationUid) {
        Conversation chat = requireChat(stationId, conversationUid);
        Transactions.run(() -> {
            if (!chats.close(chat.id(), accountId)) return;
            messages.writeSystem(chat.id(), accountId, SystemKind.CLOSED, null, "");
            conversations.touch(chat.id());
        });
    }

    /**
     * Opens a closed chat again and takes in whoever joined its audience meanwhile. A chat whose
     * group or tag is gone, and one whose audience has another open chat by now, stay closed.
     *
     * @param stationId       the station
     * @param accountId       the manager reopening it
     * @param conversationUid the chat
     */
    public void reopen(int stationId, int accountId, UUID conversationUid) {
        Conversation chat = requireChat(stationId, conversationUid);
        StationScope scope = scopeOf(chat);
        if (!scope.closed()) return;
        if (scope.orphaned()) throw MessageRefusal.STATION_CHAT_AUDIENCE_GONE.raise();
        Transactions.run(() -> {
            chats.lockOpening(stationId);
            if (chats.findOpen(stationId, scope.audience(), scope.groupId(), scope.tagId())
                    .isPresent()) {
                throw MessageRefusal.STATION_CHAT_TAKEN.raise();
            }
            if (!chats.reopen(chat.id())) return;
            messages.writeSystem(chat.id(), accountId, SystemKind.REOPENED, null, "");
            conversations.touch(chat.id());
        });
        reconcileQuietly(chat);
    }

    /**
     * Takes a chat's audience in after a change that has been committed. A failure here leaves the
     * change standing and is logged; the next change to the station or the daily sweep makes up for
     * it.
     */
    private void reconcileQuietly(Conversation chat) {
        try {
            membership.reconcileChat(chat.id());
        } catch (RuntimeException e) {
            log.warn("Taking in the audience of station chat {} failed", chat.id(), e);
        }
    }

    private Conversation requireChat(int stationId, UUID conversationUid) {
        return conversations
                .findByUid(conversationUid)
                .filter(found -> {
                    StationScope scope = found.station();
                    return scope != null && scope.stationId() == stationId;
                })
                .orElseThrow(MessageRefusal.STATION_CHAT_NOT_HERE::raise);
    }

    private void requireAudience(
            int stationId,
            @Nullable ChatAudience audience,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            boolean includeGuardians) {
        if (audience == null) throw MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR.raise();
        switch (audience) {
            case STATION -> {
                if (groupId != null || tagId != null) throw MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR.raise();
                if (includeGuardians) throw MessageRefusal.STATION_CHAT_GUARDIANS_NOT_TAKEN.raise();
            }
            case GROUP -> {
                if (groupId == null || tagId != null) throw MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR.raise();
                if (!chats.groupOf(groupId, stationId)) throw MessageRefusal.STATION_CHAT_GROUP_NOT_HERE.raise();
            }
            case TAG -> {
                if (tagId == null || groupId != null) throw MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR.raise();
                if (!chats.tagOf(tagId, stationId)) throw MessageRefusal.STATION_CHAT_TAG_NOT_HERE.raise();
            }
        }
    }

    private static String requireName(@Nullable String name) {
        String kept = name == null ? "" : name.strip();
        if (kept.isEmpty()) throw MessageRefusal.STATION_CHAT_NAME_MISSING.raise();
        if (kept.length() > MessageTexts.NAME_LENGTH) throw MessageRefusal.STATION_CHAT_NAME_TOO_LONG.raise();
        return kept;
    }

    private static StationScope scopeOf(Conversation chat) {
        return Objects.requireNonNull(chat.station(), "a station chat has its scope");
    }
}
