/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Comments on knowledge-base files and the notifications they trigger. Writing a comment announces
 * the comment itself, tells the author of the comment being replied to, and delivers one
 * notification per mention found in the body; rewriting one delivers them for the mentions the
 * edit added.
 */
@Singleton
public class KbCommentService {
    private static final Logger log = LoggerFactory.getLogger(KbCommentService.class);
    private static final int PREVIEW_LENGTH = 100;

    private final KnowledgeBaseRepository repository;
    private final CommentRepository commentRepository;
    private final MemberIdentityFactory memberIdentityFactory;
    private final StationMemberService stationMemberService;
    private final DomainEventBus eventBus;
    private final CommentMentions mentions;

    @Inject
    public KbCommentService(
            KnowledgeBaseRepository repository,
            CommentRepository commentRepository,
            MemberIdentityFactory memberIdentityFactory,
            StationMemberService stationMemberService,
            DomainEventBus eventBus,
            CommentMentions mentions) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.stationMemberService = stationMemberService;
        this.eventBus = eventBus;
        this.mentions = mentions;
    }

    /**
     * Shortens a comment body to the excerpt a notification renders inline.
     */
    private static String preview(String content) {
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "..." : content;
    }

    /**
     * One comment on a knowledge-base file, whichever station holds the file.
     *
     * @param commentId the comment
     * @return the comment, or empty when there is none by that id
     */
    public Optional<KbComment> findComment(int commentId) {
        return commentRepository.findById(CommentEntityType.KB, commentId).map(KbComment::of);
    }

    /**
     * Writes a comment on a knowledge-base file and fans out the notifications it triggers.
     *
     * @param stationId  the station owning the file
     * @param fileId     the file being commented on
     * @param parentId   the comment being replied to, or {@code null} for a top-level comment
     * @param authorId   the writing member
     * @param authorName the writing member's display name
     * @param content    the comment body
     * @return the stored comment
     */
    public KbComment createComment(
            int stationId, int fileId, Integer parentId, int authorId, String authorName, String content) {
        var identity = memberIdentityFactory.fromMemberId(authorId);
        var comment =
                KbComment.of(commentRepository.create(CommentEntityType.KB, fileId, null, parentId, identity, content));
        log.info(
                "KB comment {} created on file {} in station {} by member {}",
                comment.id(),
                fileId,
                stationId,
                authorId);

        String fileTitle = repository.findFileById(fileId).map(KbFile::name).orElse("");

        eventBus.publish(new CommentCreated(
                stationId,
                CommentEntityType.KB,
                fileTitle,
                commentLink(fileId, comment.id()),
                comment.id(),
                parentId,
                parentAuthorId(parentId),
                authorId,
                authorName,
                preview(content),
                null));

        mentions.announce(
                mentionOrigin(stationId, fileId, fileTitle, comment.id(), authorId, authorName, content), content);
        return comment;
    }

    /**
     * Rewrites a comment on a knowledge-base file and announces the mentions the edit added.
     * Whoever the comment already mentioned is not told again.
     *
     * @param stationId  the station owning the file
     * @param commentId  the comment being rewritten
     * @param authorId   the writing member
     * @param authorName the writing member's display name
     * @param content    the new comment body
     */
    public void updateComment(int stationId, int commentId, int authorId, String authorName, String content) {
        var previous = findComment(commentId);
        commentRepository.update(CommentEntityType.KB, commentId, content);
        previous.ifPresent(comment -> {
            String fileTitle =
                    repository.findFileById(comment.fileId()).map(KbFile::name).orElse("");
            mentions.announceAdded(
                    mentionOrigin(stationId, comment.fileId(), fileTitle, commentId, authorId, authorName, content),
                    comment.content(),
                    content);
        });
    }

    private static CommentMentions.Origin mentionOrigin(
            int stationId,
            int fileId,
            String fileTitle,
            int commentId,
            int authorId,
            String authorName,
            String content) {
        return new CommentMentions.Origin(
                stationId,
                authorId,
                authorName,
                CommentEntityType.KB,
                fileTitle,
                commentLink(fileId, commentId),
                commentId,
                preview(content));
    }

    private static NotificationLink commentLink(int fileId, int commentId) {
        return NotificationLinks.comment(NotificationLinks.kbFile(fileId), commentId);
    }

    /**
     * Deletes a knowledge-base comment and announces the removal, so that whatever was written
     * about it can be withdrawn.
     *
     * @param stationId the station owning the file the comment belongs to
     * @param commentId the comment to remove
     * @return {@code true} when a comment was removed
     */
    public boolean deleteComment(int stationId, int commentId) {
        var comment = findComment(commentId).orElse(null);
        if (comment == null || !commentRepository.delete(CommentEntityType.KB, commentId)) {
            log.warn("Delete for knowledge comment {} skipped: not found", commentId);
            return false;
        }
        eventBus.publish(new CommentDeleted(
                stationId, CommentEntityType.KB, commentLink(comment.fileId(), commentId), commentId));
        log.info("Deleted knowledge comment {} on station {}", commentId, stationId);
        return true;
    }

    private Integer parentAuthorId(Integer parentId) {
        if (parentId == null) return null;
        var parentComment = findComment(parentId).orElse(null);
        if (parentComment == null || parentComment.author() == null) return null;
        return stationMemberService.resolveMemberId(parentComment.author()).orElse(null);
    }
}
