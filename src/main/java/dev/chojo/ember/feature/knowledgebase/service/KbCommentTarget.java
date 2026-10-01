/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.service.CommentTarget;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

/**
 * Comments on a knowledge base file.
 *
 * <p>They are read and written by the station owning the file; a file of another station answers
 * the same as one that does not exist, as {@link KbGuards#requireOwnedFile} does for the file itself.
 * Only their author changes them; their author or a knowledge manager removes them. A reply tells
 * the author of the answered comment and a comment tells whoever it mentions; a comment from a
 * partner station tells nobody.
 */
@Singleton
public class KbCommentTarget implements CommentTarget {
    private final KnowledgeBaseRepository files;

    @Inject
    public KbCommentTarget(KnowledgeBaseRepository files) {
        this.files = files;
    }

    @Override
    public CommentEntityType type() {
        return CommentEntityType.KB;
    }

    @Override
    public Optional<TargetInfo> find(int targetId) {
        return files.findFileById(targetId)
                .map(file -> TargetInfo.of(CommentEntityType.KB, file.id(), file.stationId(), file.name()));
    }

    @Override
    public Refusal missing() {
        return Refusal.NOT_HERE_OR_NOT_YOURS;
    }

    @Override
    public void requireReadable(UserSession session, TargetInfo target) {
        requireOwned(session, target);
    }

    @Override
    public void requireWritable(UserSession session, TargetInfo target) {
        requireOwned(session, target);
    }

    private static void requireOwned(UserSession session, TargetInfo target) {
        if (!Objects.equals(target.stationId(), session.stationId())) {
            throw Refusal.NOT_HERE_OR_NOT_YOURS.raise();
        }
    }

    @Override
    public boolean mayModerate(UserSession session, TargetInfo target, Moderation action) {
        return action == Moderation.DELETE && session.hasPermission(StationPermission.KNOWLEDGE_MANAGER);
    }

    @Override
    public CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin) {
        return origin == CommentOrigin.LOCAL ? CreatedAudience.THREAD : CreatedAudience.NOBODY;
    }

    @Override
    public NotificationLink link(TargetInfo target, int commentId) {
        return NotificationLinks.comment(NotificationLinks.kbFile(target.id()), commentId);
    }
}
