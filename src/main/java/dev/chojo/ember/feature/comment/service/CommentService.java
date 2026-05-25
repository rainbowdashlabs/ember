/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service providing business logic for event comments, including CRUD operations and @mention handling.
 */
@Singleton
public class CommentService {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[(\\d+)]");

    private final EventCommentRepository commentRepository;
    private final DomainEventBus eventBus;

    @Inject
    public CommentService(EventCommentRepository commentRepository, DomainEventBus eventBus) {
        this.commentRepository = commentRepository;
        this.eventBus = eventBus;
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
            int stationId, int eventId, Integer parentId, int authorId, String authorName, String content) {
        var comment = commentRepository.create(eventId, parentId, authorId, content);

        // Parse @mentions and publish events
        var mentionedIds = parseMentions(content);
        for (int mentionedId : mentionedIds) {
            if (mentionedId != authorId) {
                eventBus.publish(
                        new MentionedInComment(stationId, mentionedId, authorId, authorName, "event", eventId));
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
    private List<Integer> parseMentions(String content) {
        var mentions = new ArrayList<Integer>();
        var matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            mentions.add(Integer.parseInt(matcher.group(1)));
        }
        return mentions;
    }
}
