/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service providing business logic for event comments, including CRUD operations and @mention handling.
 */
@Singleton
public class CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final int PREVIEW_LENGTH = 100;

    private final CommentRepository commentRepository;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final StationRepository stationRepository;
    private final CommentMentions mentions;

    @Inject
    public CommentService(
            CommentRepository commentRepository,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            StationRepository stationRepository,
            CommentMentions mentions) {
        this.commentRepository = commentRepository;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.stationRepository = stationRepository;
        this.mentions = mentions;
    }

    private static String preview(String content) {
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content;
    }

    /**
     * Finds all comments for an event.
     *
     * @param eventId the event ID
     * @return the list of comments
     */
    public List<Comment> findByEvent(int eventId) {
        return commentRepository.findByTarget(CommentEntityType.EVENT, eventId);
    }

    /**
     * Finds comments scoped to a specific occurrence of a recurring event. {@code null}
     * returns only whole-event comments (event_date IS NULL); a non-null date returns
     * comments stamped with exactly that occurrence.
     */
    public List<Comment> findByEventAndDate(int eventId, LocalDate eventDate) {
        return commentRepository.findByEventOccurrence(eventId, eventDate);
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, if found
     */
    public Optional<Comment> findById(int id) {
        return commentRepository.findById(CommentEntityType.EVENT, id);
    }

    /**
     * The station owning the event a comment hangs under.
     *
     * @param commentId the comment ID
     * @return the owning station, empty when there is no such comment
     */
    public Optional<Integer> findCommentStation(int commentId) {
        return commentRepository.findById(CommentEntityType.EVENT, commentId).map(Comment::stationId);
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
        var comment =
                commentRepository.create(CommentEntityType.EVENT, eventId, eventDate, parentId, author, content);
        log.info("Created event comment {} on event {} (station {})", comment.id(), eventId, stationId);

        // Resolve author to local member ID (null if federated / not on this station)
        Integer authorMemberId = resolveLocalMemberId(stationId, author);

        // Notify parent comment author on reply (skip for federated comments without a local author)
        if (parentId != null && authorMemberId != null) {
            commentRepository.findById(CommentEntityType.EVENT, parentId).ifPresent(parent -> {
                Integer parentAuthorId = resolveLocalMemberId(stationId, parent.author());
                if (parentAuthorId != null && !parentAuthorId.equals(authorMemberId)) {
                    eventBus.publish(new CommentCreated(
                            stationId,
                            CommentEntityType.EVENT,
                            eventId,
                            entityTitle,
                            null,
                            comment.id(),
                            parentId,
                            parentAuthorId,
                            authorMemberId,
                            authorName,
                            preview(content)));
                }
            });
        }

        if (authorMemberId != null) {
            mentions.announce(
                    mentionOrigin(stationId, authorMemberId, authorName, eventId, entityTitle, comment.id(), content),
                    content);
        }

        return comment;
    }

    /**
     * Updates the content of a comment and announces the mentions the edit added. Whoever the
     * comment already mentioned is not told again, and a comment without a local author raises
     * nothing, the same as when it was written.
     *
     * @param stationId  the station the comment was written in
     * @param id         the comment ID
     * @param authorName the display name of the author
     * @param content    the new content
     * @return {@code true} if the comment was updated
     */
    public boolean update(int stationId, int id, String authorName, String content) {
        var previous = commentRepository.findById(CommentEntityType.EVENT, id);
        boolean updated = commentRepository.update(CommentEntityType.EVENT, id, content);
        if (!updated) {
            log.warn("Update for event comment {} affected zero rows", id);
            return false;
        }
        log.info("Updated event comment {}", id);
        previous.ifPresent(comment -> announceAddedMentions(stationId, comment, authorName, content));
        return true;
    }

    private void announceAddedMentions(int stationId, Comment previous, String authorName, String content) {
        Integer authorMemberId = resolveLocalMemberId(stationId, previous.author());
        if (authorMemberId == null) return;
        commentRepository
                .findCommentedEvent(previous.id())
                .ifPresent(event -> mentions.announceAdded(
                        mentionOrigin(
                                stationId,
                                authorMemberId,
                                authorName,
                                event.id(),
                                event.name(),
                                previous.id(),
                                content),
                        previous.content(),
                        content));
    }

    private static CommentMentions.Origin mentionOrigin(
            int stationId,
            int authorMemberId,
            String authorName,
            int eventId,
            String eventTitle,
            int commentId,
            String content) {
        return new CommentMentions.Origin(
                stationId,
                authorMemberId,
                authorName,
                CommentEntityType.EVENT,
                eventId,
                eventTitle,
                null,
                commentId,
                preview(content));
    }

    /**
     * Deletes a comment by ID and announces the removal, so that whatever was written about it can
     * be withdrawn. The owning station is read before the row goes, since afterwards there is
     * nothing left to read it from.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted
     */
    public boolean delete(int id) {
        int stationId = findCommentStation(id).orElse(0);
        boolean deleted = commentRepository.delete(CommentEntityType.EVENT, id);
        if (deleted) {
            eventBus.publish(new CommentDeleted(stationId, CommentEntityType.EVENT, id));
            log.info("Deleted event comment {}", id);
        } else {
            log.warn("Delete for event comment {} affected zero rows", id);
        }
        return deleted;
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
}
