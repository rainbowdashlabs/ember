/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.route;

import dev.chojo.ember.feature.messages.entity.ConversationDetail;
import dev.chojo.ember.feature.messages.entity.ConversationKind;
import dev.chojo.ember.feature.messages.entity.ConversationSummary;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessagePage;
import dev.chojo.ember.feature.messages.entity.MessageView;
import dev.chojo.ember.feature.messages.entity.Person;
import dev.chojo.ember.feature.messages.entity.ReachablePerson;
import dev.chojo.ember.feature.messages.entity.Reaction;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.service.MessageTexts;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the message routes send, and how it is made from what the services answer. Account ids
 * never leave the server; people are named by their account identity.
 */
public final class MessageWire {
    private static final int PREVIEW_LENGTH = 120;

    private MessageWire() {}

    /**
     * Somebody in messages, by the account's register name.
     *
     * @param uid       the account's identity
     * @param firstName the first name
     * @param lastName  the last name
     */
    public record MessagePersonResponse(UUID uid, String firstName, String lastName) {
        static MessagePersonResponse of(Person person) {
            return new MessagePersonResponse(person.uid(), person.firstName(), person.lastName());
        }

        static @Nullable MessagePersonResponse ofNullable(@Nullable Person person) {
            return person == null ? null : of(person);
        }
    }

    /**
     * Somebody the reader may write to.
     *
     * @param uid         the account's identity
     * @param firstName   the first name
     * @param lastName    the last name
     * @param stationName a station both are members of, as a hint of who this is
     */
    public record ReachablePersonResponse(UUID uid, String firstName, String lastName, String stationName) {
        static ReachablePersonResponse of(ReachablePerson found) {
            Person person = found.person();
            return new ReachablePersonResponse(
                    person.uid(), person.firstName(), person.lastName(), found.stationName());
        }
    }

    /**
     * The newest line of a conversation, as the list shows it.
     *
     * @param id         the message
     * @param author     who wrote it, or null for a deleted account and a line nobody wrote
     * @param text       the start of its text, mentions written as names
     * @param systemKind what a system line says, or null for a written message
     * @param subject    whom a system line is about, or null
     * @param deleted    whether it was deleted
     * @param createdAt  when it was written
     */
    public record ConversationPreview(
            int id,
            @Nullable MessagePersonResponse author,
            String text,
            @Nullable SystemKind systemKind,
            @Nullable MessagePersonResponse subject,
            boolean deleted,
            Instant createdAt) {
        static @Nullable ConversationPreview of(@Nullable Message message) {
            if (message == null) return null;
            return new ConversationPreview(
                    message.id(),
                    MessagePersonResponse.ofNullable(message.author()),
                    MessageTexts.shortened(MessageTexts.plain(message.content()), PREVIEW_LENGTH),
                    message.systemKind(),
                    MessagePersonResponse.ofNullable(message.subject()),
                    message.deleted(),
                    message.createdAt());
        }
    }

    /**
     * One row of the list of conversations.
     *
     * @param uid            the conversation
     * @param kind           direct or group
     * @param name           a group's name, or null to call it after its members
     * @param others         the other people in it, the longest member first
     * @param newest         its newest line, or null where nothing was written yet
     * @param lastActivityAt when anything was last written in it
     * @param unread         how many messages the reader has not read
     * @param muted          whether the reader muted it
     */
    public record ConversationSummaryResponse(
            UUID uid,
            ConversationKind kind,
            @Nullable String name,
            List<MessagePersonResponse> others,
            @Nullable ConversationPreview newest,
            Instant lastActivityAt,
            int unread,
            boolean muted) {
        static ConversationSummaryResponse of(ConversationSummary summary) {
            var conversation = summary.overview().conversation();
            return new ConversationSummaryResponse(
                    conversation.uid(),
                    conversation.kind(),
                    conversation.name(),
                    summary.others().stream().map(MessagePersonResponse::of).toList(),
                    ConversationPreview.of(summary.newest()),
                    conversation.lastActivityAt(),
                    summary.overview().unread(),
                    summary.overview().muted());
        }
    }

    /**
     * Somebody taking part in a conversation.
     *
     * @param uid       the account's identity
     * @param firstName the first name
     * @param lastName  the last name
     * @param joinedAt  when they started or were added
     */
    public record ConversationMemberResponse(UUID uid, String firstName, String lastName, Instant joinedAt) {
        static ConversationMemberResponse of(Membership member) {
            Person person = member.person();
            return new ConversationMemberResponse(
                    person.uid(), person.firstName(), person.lastName(), member.joinedAt());
        }
    }

    /**
     * A conversation as its header and member list show it.
     *
     * @param uid               the conversation
     * @param kind              direct or group
     * @param name              a group's name, or null
     * @param ownerUid          the owner of a group, or null
     * @param members           everybody in it, the longest member first, the reader included
     * @param muted             whether the reader muted it
     * @param lastReadMessageId the last message the reader has read, where the unread part starts after
     */
    public record ConversationResponse(
            UUID uid,
            ConversationKind kind,
            @Nullable String name,
            @Nullable UUID ownerUid,
            List<ConversationMemberResponse> members,
            boolean muted,
            @Nullable Integer lastReadMessageId) {
        static ConversationResponse of(ConversationDetail detail) {
            var conversation = detail.conversation();
            Integer ownerId = conversation.ownerAccountId();
            @Nullable
            UUID ownerUid = detail.members().stream()
                    .filter(member -> Objects.equals(member.accountId(), ownerId))
                    .map(member -> member.person().uid())
                    .findFirst()
                    .orElse(null);
            return new ConversationResponse(
                    conversation.uid(),
                    conversation.kind(),
                    conversation.name(),
                    ownerUid,
                    detail.members().stream()
                            .map(ConversationMemberResponse::of)
                            .toList(),
                    detail.mine().muted(),
                    detail.mine().lastReadMessageId());
        }
    }

    /**
     * The message another one answers, quoted small above it.
     *
     * @param id      the message
     * @param author  who wrote it, or null for a deleted account
     * @param text    the start of its text, mentions written as names
     * @param deleted whether it was deleted
     */
    public record ReplyPreviewResponse(int id, @Nullable MessagePersonResponse author, String text, boolean deleted) {
        static @Nullable ReplyPreviewResponse of(@Nullable Message message) {
            if (message == null) return null;
            return new ReplyPreviewResponse(
                    message.id(),
                    MessagePersonResponse.ofNullable(message.author()),
                    MessageTexts.shortened(MessageTexts.plain(message.content()), PREVIEW_LENGTH),
                    message.deleted());
        }
    }

    /**
     * Everybody who reacted to a message with one emoji.
     *
     * @param emoji  the emoji
     * @param count  how many reacted with it
     * @param mine   whether the reader is one of them
     * @param people who reacted, in the order they did
     */
    public record ReactionGroupResponse(String emoji, int count, boolean mine, List<MessagePersonResponse> people) {}

    /**
     * One message as the reader sees it.
     *
     * @param id         the message
     * @param author     who wrote it, or null for a deleted account and a line nobody wrote
     * @param content    the text, with line breaks and mentions; empty once deleted
     * @param replyTo    the message it answers, or null
     * @param systemKind what a system line says, or null for a written message
     * @param subject    whom a system line is about, or null
     * @param createdAt  when it was written
     * @param editedAt   when it was last changed, or null
     * @param deleted    whether it was deleted
     * @param reactions  the reactions, grouped by emoji in the order they were first given
     */
    public record ConversationMessageResponse(
            int id,
            @Nullable MessagePersonResponse author,
            String content,
            @Nullable ReplyPreviewResponse replyTo,
            @Nullable SystemKind systemKind,
            @Nullable MessagePersonResponse subject,
            Instant createdAt,
            @Nullable Instant editedAt,
            boolean deleted,
            List<ReactionGroupResponse> reactions) {
        static ConversationMessageResponse of(MessageView view, UUID readerUid) {
            Message message = view.message();
            return new ConversationMessageResponse(
                    message.id(),
                    MessagePersonResponse.ofNullable(message.author()),
                    message.content(),
                    ReplyPreviewResponse.of(view.replyTo()),
                    message.systemKind(),
                    MessagePersonResponse.ofNullable(message.subject()),
                    message.createdAt(),
                    message.editedAt(),
                    message.deleted(),
                    grouped(view.reactions(), readerUid));
        }

        private static List<ReactionGroupResponse> grouped(List<Reaction> reactions, UUID readerUid) {
            var byEmoji = new LinkedHashMap<String, List<Person>>();
            reactions.forEach(reaction -> byEmoji.computeIfAbsent(reaction.emoji(), emoji -> new ArrayList<>())
                    .add(reaction.person()));
            return byEmoji.entrySet().stream()
                    .map(entry -> new ReactionGroupResponse(
                            entry.getKey(),
                            entry.getValue().size(),
                            entry.getValue().stream()
                                    .anyMatch(person -> person.uid().equals(readerUid)),
                            entry.getValue().stream()
                                    .map(MessagePersonResponse::of)
                                    .toList()))
                    .toList();
        }
    }

    /**
     * A stretch of a conversation, oldest first.
     *
     * @param messages the messages
     * @param hasMore  whether there are more in the direction asked for
     */
    public record ConversationMessagePage(List<ConversationMessageResponse> messages, boolean hasMore) {
        static ConversationMessagePage of(MessagePage page, UUID readerUid) {
            return new ConversationMessagePage(
                    page.messages().stream()
                            .map(view -> ConversationMessageResponse.of(view, readerUid))
                            .toList(),
                    page.hasMore());
        }
    }
}
