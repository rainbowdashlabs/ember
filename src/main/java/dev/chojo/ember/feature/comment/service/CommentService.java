/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service providing business logic for event comments, including CRUD operations and @mention handling.
 */
@Singleton
public class CommentService {
    private static final Pattern MENTION_PATTERN_LEGACY = Pattern.compile("@\\[(\\d+):([^\\]]+)]");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");
    private static final Pattern BULK_MENTION_PATTERN =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");

    private final EventCommentRepository commentRepository;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final StationRepository stationRepository;

    @Inject
    public CommentService(
            EventCommentRepository commentRepository,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            StationRepository stationRepository) {
        this.commentRepository = commentRepository;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.stationRepository = stationRepository;
    }

    /**
     * Finds all comments for an event.
     *
     * @param eventId the event ID
     * @return the list of comments
     */
    public List<Comment> findByEvent(int eventId) {
        return commentRepository.findByEvent(eventId);
    }

    /**
     * Finds comments scoped to a specific occurrence of a recurring event. {@code null}
     * returns only whole-event comments (event_date IS NULL); a non-null date returns
     * comments stamped with exactly that occurrence.
     */
    public List<Comment> findByEventAndDate(int eventId, LocalDate eventDate) {
        return commentRepository.findByEventAndDate(eventId, eventDate);
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, if found
     */
    public Optional<Comment> findById(int id) {
        return commentRepository.findById(id);
    }

    /**
     * Creates a new comment and publishes mention events for any @mentioned members.
     *
     * @param stationId   the station ID
     * @param eventId     the event ID
     * @param parentId    parent comment ID for replies, or {@code null}
     * @param author      the identity of the author, or {@code null} for anonymous/federated without identity
     * @param authorName  the display name of the author
     * @param content     the comment text
     * @param entityTitle the title/name of the event (used in notifications)
     * @return the created comment
     */
    public Comment create(
            int stationId,
            int eventId,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            String entityTitle,
            LocalDate eventDate) {
        var comment = commentRepository.create(eventId, parentId, author, content, eventDate);

        // Resolve author to local member ID (null if federated / not on this station)
        Integer authorMemberId = resolveLocalMemberId(stationId, author);

        // Notify parent comment author on reply (skip for federated comments without a local author)
        if (parentId != null && authorMemberId != null) {
            commentRepository.findById(parentId).ifPresent(parent -> {
                Integer parentAuthorId = resolveLocalMemberId(stationId, parent.author());
                if (parentAuthorId != null && !parentAuthorId.equals(authorMemberId)) {
                    String preview = content.length() > 100 ? content.substring(0, 100) + "…" : content;
                    eventBus.publish(new CommentCreated(
                            stationId,
                            CommentEntityType.EVENT,
                            eventId,
                            entityTitle,
                            comment.id(),
                            parentId,
                            parentAuthorId,
                            authorMemberId,
                            authorName,
                            preview));
                }
            });
        }

        // Parse @mentions and publish events (skip for federated comments without a local author)
        if (authorMemberId != null) {
            String mentionPreview = content.length() > 100 ? content.substring(0, 100) + "\u2026" : content;
            var mentionedIds = parseMentions(stationId, content);
            for (int mentionedId : mentionedIds) {
                if (mentionedId != authorMemberId) {
                    eventBus.publish(new MentionedInComment(
                            stationId,
                            mentionedId,
                            authorMemberId,
                            authorName,
                            CommentEntityType.EVENT,
                            eventId,
                            entityTitle,
                            mentionPreview));
                }
            }
            parseBulkMentions(
                    stationId,
                    authorMemberId,
                    authorName,
                    CommentEntityType.EVENT,
                    eventId,
                    entityTitle,
                    content,
                    mentionPreview);
        }

        return comment;
    }

    /**
     * Resolves a MemberIdentity to a local member ID on the given station.
     * Returns {@code null} if the identity is null or the member is not local.
     */
    private Integer resolveLocalMemberId(int stationId, MemberIdentity identity) {
        if (identity == null) return null;
        // Check if the identity belongs to this station
        int identityStationId =
                stationRepository.resolveId(identity.stationUid()).orElse(0);
        if (identityStationId != stationId) return null;
        return stationMemberService.resolveId(stationId, identity.memberUid()).orElse(null);
    }

    /**
     * Updates the content of a comment.
     *
     * @param id      the comment ID
     * @param content the new content
     * @return {@code true} if the comment was updated
     */
    public boolean update(int id, String content) {
        return commentRepository.update(id, content);
    }

    /**
     * Deletes a comment by ID.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted
     */
    public boolean delete(int id) {
        return commentRepository.delete(id);
    }

    /**
     * Extracts member IDs from @mention patterns in the content.
     * Pattern: {@code @[123]} where 123 is the member ID.
     *
     * @param content the comment text
     */
    private void parseBulkMentions(
            int stationId,
            Integer authorMemberId,
            String authorName,
            CommentEntityType entityType,
            int entityId,
            String entityTitle,
            String content,
            String preview) {
        var matcher = BULK_MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            var type = MentionType.valueOf(matcher.group(1));
            int targetId = Integer.parseInt(matcher.group(3));
            eventBus.publish(new BulkMentionedInComment(
                    stationId, authorMemberId, authorName, entityType, entityId, entityTitle, type, targetId, preview));
        }
    }

    private List<Integer> parseMentions(int stationId, String content) {
        var mentions = new ArrayList<Integer>();
        // New format: @[stationUid/memberUid:Name]
        var matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                var memberUid = UUID.fromString(matcher.group(2));
                stationMemberService.resolveId(stationId, memberUid).ifPresent(mentions::add);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Legacy format: @[123:Name]
        var legacyMatcher = MENTION_PATTERN_LEGACY.matcher(content);
        while (legacyMatcher.find()) {
            mentions.add(Integer.parseInt(legacyMatcher.group(1)));
        }
        return mentions;
    }
}
