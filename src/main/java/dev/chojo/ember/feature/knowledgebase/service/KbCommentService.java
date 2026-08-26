/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.comment.service.MentionLimits;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Comments on knowledge-base files and the notifications they trigger. Writing a comment announces
 * the comment itself, tells the author of the comment being replied to, and delivers one
 * notification per mention found in the body.
 *
 * <p>Three mention forms are recognised: the current {@code station/member} form, the legacy
 * numeric member form, and the bulk form addressing a whole group or event audience. Authors are
 * never notified about their own mentions, and a mention that resolves to nobody is dropped rather
 * than failing the comment.
 */
@Singleton
public class KbCommentService {
    private static final Logger log = LoggerFactory.getLogger(KbCommentService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");
    private static final Pattern BULK_MENTION_PATTERN =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");
    private static final int PREVIEW_LENGTH = 100;

    private final KnowledgeBaseRepository repository;
    private final KbCommentRepository commentRepository;
    private final MemberIdentityFactory memberIdentityFactory;
    private final StationMemberService stationMemberService;
    private final DomainEventBus eventBus;

    @Inject
    public KbCommentService(
            KnowledgeBaseRepository repository,
            KbCommentRepository commentRepository,
            MemberIdentityFactory memberIdentityFactory,
            StationMemberService stationMemberService,
            DomainEventBus eventBus) {
        this.repository = repository;
        this.commentRepository = commentRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.stationMemberService = stationMemberService;
        this.eventBus = eventBus;
    }

    /**
     * Shortens a comment body to the excerpt a notification renders inline.
     */
    private static String preview(String content) {
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "..." : content;
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
        var comment = commentRepository.create(fileId, parentId, identity, content);
        log.info(
                "KB comment {} created on file {} in station {} by member {}",
                comment.id(),
                fileId,
                stationId,
                authorId);

        String fileTitle = repository.findFileById(fileId).map(KbFile::name).orElse("");
        String preview = preview(content);

        eventBus.publish(new CommentCreated(
                stationId,
                CommentEntityType.KB,
                fileId,
                fileTitle,
                comment.id(),
                parentId,
                parentAuthorId(parentId),
                authorId,
                authorName,
                preview));

        notifyMentions(stationId, fileId, fileTitle, authorId, authorName, content, preview);
        return comment;
    }

    /**
     * Deletes a knowledge-base comment and announces the removal with a short content preview.
     *
     * @param stationId the station owning the file the comment belongs to
     * @param commentId the comment to remove
     * @return {@code true} when a comment was removed
     */
    public boolean deleteComment(int stationId, int commentId) {
        var comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null || !commentRepository.delete(commentId)) {
            log.warn("Delete for knowledge comment {} skipped: not found", commentId);
            return false;
        }
        eventBus.publish(new CommentDeleted(stationId, commentId, preview(comment.content())));
        log.info("Deleted knowledge comment {} on station {}", commentId, stationId);
        return true;
    }

    private Integer parentAuthorId(Integer parentId) {
        if (parentId == null) return null;
        var parentComment = commentRepository.findById(parentId).orElse(null);
        if (parentComment == null || parentComment.author() == null) return null;
        return stationMemberService.resolveMemberId(parentComment.author()).orElse(null);
    }

    private void notifyMentions(
            int stationId,
            int fileId,
            String fileTitle,
            int authorId,
            String authorName,
            String content,
            String preview) {
        var matcher = MENTION_PATTERN.matcher(content);
        int mentioned = 0;
        while (matcher.find() && mentioned++ < MentionLimits.MAX_MEMBER_MENTIONS) {
            UUID memberUid;
            try {
                memberUid = UUID.fromString(matcher.group(2));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            stationMemberService
                    .resolveId(stationId, memberUid)
                    .ifPresent(mentionedId ->
                            notifyMention(stationId, fileId, fileTitle, authorId, authorName, mentionedId, preview));
        }

        var bulkMatcher = BULK_MENTION_PATTERN.matcher(content);
        int addressed = 0;
        while (bulkMatcher.find() && addressed++ < MentionLimits.MAX_BULK_MENTIONS) {
            var type = MentionType.valueOf(bulkMatcher.group(1));
            int targetId = Integer.parseInt(bulkMatcher.group(3));
            eventBus.publish(new BulkMentionedInComment(
                    stationId, authorId, authorName, CommentEntityType.KB, fileId, fileTitle, type, targetId, preview));
        }
    }

    private void notifyMention(
            int stationId,
            int fileId,
            String fileTitle,
            int authorId,
            String authorName,
            int mentionedId,
            String preview) {
        if (mentionedId == authorId) return;
        eventBus.publish(new MentionedInComment(
                stationId, mentionedId, authorId, authorName, CommentEntityType.KB, fileId, fileTitle, preview));
    }
}
