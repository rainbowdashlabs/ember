/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The mentions a comment makes and the notifications they raise, the same for every kind of comment.
 *
 * <p>A mention is announced once: when the comment is written, or when an edit adds it. Whoever the
 * text already mentioned before the edit is not told a second time, and the author is never told
 * about their own mention.
 *
 * <p>Members are read only in the form carrying a station and a member uid, resolved through the
 * station the comment was written in. The numeric form older editors wrote names a member by an id
 * on the whole instance, so reading it would notify a stranger in another station; such comments
 * still render and simply raise nothing. A mention that resolves to nobody is dropped rather than
 * failing the comment.
 */
@Singleton
public class CommentMentions {
    private static final Pattern MEMBER_MENTION = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");
    private static final Pattern BULK_MENTION =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");

    private final MemberLookupService memberLookupService;
    private final DomainEventBus eventBus;

    @Inject
    public CommentMentions(MemberLookupService memberLookupService, DomainEventBus eventBus) {
        this.memberLookupService = memberLookupService;
        this.eventBus = eventBus;
    }

    /**
     * Where a comment was written and by whom, which every notification about its mentions carries.
     *
     * @param stationId      the station the comment was written in, which resolves the mentions
     * @param authorMemberId the author as a member of that station, {@code null} when they are not one
     * @param authorName     the name the notification shows as the author
     * @param entityType     what the comment hangs under
     * @param entityTitle    that thing's title
     * @param link           where a notification about the comment opens
     * @param commentId      the comment
     * @param preview        the excerpt of the comment the notification shows
     */
    public record Origin(
            int stationId,
            @Nullable Integer authorMemberId,
            String authorName,
            CommentEntityType entityType,
            String entityTitle,
            NotificationLink link,
            int commentId,
            String preview) {}

    /**
     * Announces every mention in a comment that has just been written.
     *
     * @param origin  where the comment was written
     * @param content the comment's text
     */
    public void announce(Origin origin, String content) {
        announceAdded(origin, "", content);
    }

    /**
     * Announces the mentions an edit added, leaving alone everybody the earlier text already
     * mentioned.
     *
     * @param origin   where the comment was written
     * @param previous the text before the edit
     * @param content  the text after it
     */
    public void announceAdded(Origin origin, String previous, String content) {
        var members = members(origin.stationId(), content);
        members.removeAll(members(origin.stationId(), previous));
        members.stream()
                .filter(memberId -> !memberId.equals(origin.authorMemberId()))
                .forEach(memberId -> publishMember(origin, memberId));

        var audiences = audiences(content);
        audiences.removeAll(audiences(previous));
        audiences.forEach(audience -> publishAudience(origin, audience));
    }

    private Set<Integer> members(int stationId, String content) {
        var members = new LinkedHashSet<Integer>();
        var matcher = MEMBER_MENTION.matcher(content);
        int read = 0;
        while (matcher.find() && read++ < MentionLimits.MAX_MEMBER_MENTIONS) {
            memberUid(matcher.group(2))
                    .flatMap(uid -> memberLookupService.resolveId(stationId, uid))
                    .ifPresent(members::add);
        }
        return members;
    }

    private static Optional<UUID> memberUid(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException notAUid) {
            return Optional.empty();
        }
    }

    /**
     * A group, an event's audience or one of its registration lists, addressed as a whole.
     *
     * @param type     which kind of audience
     * @param targetId the group or event it is drawn from
     */
    private record Audience(MentionType type, int targetId) {}

    private Set<Audience> audiences(String content) {
        var audiences = new LinkedHashSet<Audience>();
        var matcher = BULK_MENTION.matcher(content);
        int read = 0;
        while (matcher.find() && read++ < MentionLimits.MAX_BULK_MENTIONS) {
            audiences.add(new Audience(MentionType.valueOf(matcher.group(1)), Integer.parseInt(matcher.group(3))));
        }
        return audiences;
    }

    private void publishMember(Origin origin, int memberId) {
        eventBus.publish(new MentionedInComment(
                origin.stationId(),
                memberId,
                origin.authorMemberId(),
                origin.authorName(),
                origin.entityType(),
                origin.entityTitle(),
                origin.link(),
                origin.commentId(),
                origin.preview()));
    }

    private void publishAudience(Origin origin, Audience audience) {
        eventBus.publish(new BulkMentionedInComment(
                origin.stationId(),
                origin.authorMemberId(),
                origin.authorName(),
                origin.entityType(),
                origin.entityTitle(),
                audience.type(),
                audience.targetId(),
                origin.link(),
                origin.commentId(),
                origin.preview()));
    }
}
