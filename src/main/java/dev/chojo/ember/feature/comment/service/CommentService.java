/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    private final EventCommentRepository commentRepository;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;

    @Inject
    public CommentService(
            EventCommentRepository commentRepository,
            DomainEventBus eventBus,
            StationMemberService stationMemberService) {
        this.commentRepository = commentRepository;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
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
     * @param stationId  the station ID
     * @param eventId    the event ID
     * @param parentId   parent comment ID for replies, or {@code null}
     * @param authorId   the member ID of the author
     * @param authorName the display name of the author
     * @param content    the comment text
     * @return the created comment
     */
    public Comment create(
            int stationId, int eventId, Integer parentId, Integer authorId, String authorName, String content) {
        var comment = commentRepository.create(eventId, parentId, authorId, content);

        // Notify parent comment author on reply (skip for federated comments without a local author)
        if (parentId != null && authorId != null) {
            commentRepository.findById(parentId).ifPresent(parent -> {
                if (!java.util.Objects.equals(parent.authorId(), authorId)) {
                    String preview = content.length() > 100 ? content.substring(0, 100) + "…" : content;
                    eventBus.publish(new CommentCreated(
                            stationId,
                            CommentEntityType.EVENT,
                            eventId,
                            "",
                            comment.id(),
                            parentId,
                            parent.authorId(),
                            authorId,
                            authorName,
                            preview));
                }
            });
        }

        // Parse @mentions and publish events (skip for federated comments without a local author)
        if (authorId != null) {
            var mentionedIds = parseMentions(stationId, content);
            for (int mentionedId : mentionedIds) {
                if (mentionedId != authorId) {
                    eventBus.publish(new MentionedInComment(
                            stationId, mentionedId, authorId, authorName, CommentEntityType.EVENT, eventId));
                }
            }
        }

        return comment;
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
     * @return list of mentioned member IDs
     */
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
