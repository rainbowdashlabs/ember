/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.MembershipChange;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.StationChatSummary;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
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
 * A station's chats as its managers handle them: opening one for any audience of the station,
 * changing its name, audience and guardians, closing and reopening it.
 *
 * <p>A station may have several open chats, each with its own name and audience. Opening, renaming,
 * closing and reopening leave a quiet line in the chat; who comes and goes with the audience does
 * not. Who holds the permission is checked by the route; everything here is held to the station
 * named.
 */
@Singleton
public class StationChatService {
    private static final Logger log = LoggerFactory.getLogger(StationChatService.class);

    private final StationChatRepository chats;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final StationChatMembership membership;
    private final MessageSignals signals;
    private final RestrictionService restrictions;

    @Inject
    public StationChatService(
            StationChatRepository chats,
            ConversationRepository conversations,
            MessageRepository messages,
            StationChatMembership membership,
            MessageSignals signals,
            RestrictionService restrictions) {
        this.signals = signals;
        this.chats = chats;
        this.conversations = conversations;
        this.messages = messages;
        this.membership = membership;
        this.restrictions = restrictions;
    }

    /**
     * The station's chats, the open ones first.
     *
     * @param stationId the station
     * @return each with its audience and how many take part
     */
    public List<StationChatSummary> list(int stationId) {
        return chats.counted(stationId).stream().map(this::summarise).toList();
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
        return chats.counted(stationId, chat.id())
                .map(this::summarise)
                .orElseThrow(MessageRefusal.STATION_CHAT_NOT_HERE::raise);
    }

    /**
     * Opens a chat and takes its audience in.
     *
     * @param stationId        the station
     * @param accountId        the manager opening it
     * @param name             its name
     * @param audience         whom it is for; null or one naming nobody is the whole station
     * @param includeGuardians whether the guardians of its people are members too
     * @return the chat
     */
    public Conversation open(
            int stationId,
            int accountId,
            @Nullable String name,
            @Nullable RestrictionAudience audience,
            boolean includeGuardians) {
        String kept = requireName(name);
        RestrictionAudience target = requireAudience(stationId, audience);
        Conversation chat = Transactions.call(() -> {
            var created = chats.create(stationId, kept, includeGuardians, !target.namesNobody(), kindsOf(target));
            restrictions.setAudience(RestrictionType.STATION_CHAT, created.id(), target);
            messages.writeSystem(created.id(), accountId, SystemKind.OPENED, null, kept);
            return created;
        });
        reconcileQuietly(chat);
        log.info("Station chat {} opened at station {} by account {}", chat.id(), stationId, accountId);
        return chat;
    }

    /**
     * Changes a chat's name, audience and whether it takes the guardians in, then takes the audience
     * in. A new name leaves a quiet line in the chat. A closed chat keeps its changes for when it is
     * reopened, which is how one that lost its audience gets a new one.
     *
     * @param stationId        the station
     * @param accountId        the manager changing it
     * @param conversationUid  the chat
     * @param name             its name
     * @param audience         whom it is for; one naming nobody is the whole station. Refused when
     *                         absent, so a change never widens a chat by leaving it out
     * @param includeGuardians whether the guardians of its people are members too, refused when absent
     */
    public void edit(
            int stationId,
            int accountId,
            UUID conversationUid,
            @Nullable String name,
            @Nullable RestrictionAudience audience,
            @Nullable Boolean includeGuardians) {
        if (audience == null || includeGuardians == null) throw MessageRefusal.STATION_CHAT_CHANGE_UNSAID.raise();
        Conversation chat = requireChat(stationId, conversationUid);
        String kept = requireName(name);
        RestrictionAudience target = requireAudience(stationId, audience);
        boolean guardians = includeGuardians;
        boolean renamed = !kept.equals(chat.name());
        Transactions.run(() -> {
            chats.update(chat.id(), kept, guardians, !target.namesNobody(), kindsOf(target));
            restrictions.setAudience(RestrictionType.STATION_CHAT, chat.id(), target);
            if (renamed) {
                messages.writeSystem(chat.id(), accountId, SystemKind.RENAMED, null, kept);
                conversations.touch(chat.id());
            }
        });
        log.info("Station chat {} at station {} changed by account {}", chat.id(), stationId, accountId);
        if (!reconcileQuietly(chat)) tell(chat);
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
        boolean closed = Transactions.call(() -> {
            if (!chats.close(chat.id(), accountId)) return false;
            messages.writeSystem(chat.id(), accountId, SystemKind.CLOSED, null, "");
            conversations.touch(chat.id());
            return true;
        });
        if (closed) tell(chat);
    }

    /**
     * Opens a closed chat again and takes in whoever joined its audience meanwhile. A chat whose
     * audience lost part of what it named stays closed until it is given a new audience.
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
        boolean reopened = Transactions.call(() -> {
            if (!chats.reopen(chat.id())) return false;
            messages.writeSystem(chat.id(), accountId, SystemKind.REOPENED, null, "");
            conversations.touch(chat.id());
            return true;
        });
        if (reopened && !reconcileQuietly(chat)) tell(chat);
    }
    /**
     * Tells the tabs of everybody in a chat that its name or state changed.
     */
    private void tell(Conversation chat) {
        Transactions.afterCommit(() -> signals.publishTo(chat.id(), MessageEvent.conversation(chat.uid()), List.of()));
    }

    /**
     * Takes a chat's audience in after a change that has been committed. A failure here leaves the
     * change standing and is logged; the next change to the station or the daily sweep makes up for
     * it. Answers whether anything changed, in which case the chat's tabs were told already.
     */
    private boolean reconcileQuietly(Conversation chat) {
        try {
            return membership
                    .reconcileChat(chat.id())
                    .filter(MembershipChange::any)
                    .isPresent();
        } catch (RuntimeException e) {
            log.warn("Taking in the audience of station chat {} failed", chat.id(), e);
            return false;
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

    private RestrictionAudience requireAudience(int stationId, @Nullable RestrictionAudience audience) {
        RestrictionAudience target = audience == null ? RestrictionAudience.empty() : audience.distinct();
        if (!restrictions.ofStation(stationId, target)) throw MessageRefusal.STATION_CHAT_AUDIENCE_NOT_HERE.raise();
        return target;
    }

    private static int kindsOf(RestrictionAudience audience) {
        var selection = audience.toSelection();
        return selection.userTypes().size()
                + selection.groupIds().size()
                + selection.tagIds().size();
    }

    private StationChatSummary summarise(StationChatRepository.Counted counted) {
        Conversation chat = counted.conversation();
        return new StationChatSummary(
                chat, restrictions.audienceOf(RestrictionType.STATION_CHAT, chat.id()), counted.members());
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
