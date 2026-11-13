/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.service.CommentTarget;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Comments on an event group, which work as those on an appointment do.
 *
 * <p>They are read and written by whoever may open the event group in its own station. Only their
 * author changes them; their author or an appointment manager removes them. A reply tells the
 * author of the answered comment and a comment tells whoever it mentions. A new top-level comment,
 * from here or from a partner station, tells the appointment managers; a partner's comment tells
 * nobody else.
 */
@Singleton
public class EventGroupCommentTarget implements CommentTarget {
    private final EventGroupService eventGroups;
    private final EventGroupGuards guards;

    @Inject
    public EventGroupCommentTarget(EventGroupService eventGroups, EventGroupGuards guards) {
        this.eventGroups = eventGroups;
        this.guards = guards;
    }

    @Override
    public CommentEntityType type() {
        return CommentEntityType.EVENT_GROUP;
    }

    @Override
    public Optional<TargetInfo> find(int targetId) {
        return eventGroups
                .findById(targetId)
                .map(group ->
                        TargetInfo.of(CommentEntityType.EVENT_GROUP, group.id(), group.stationId(), group.name()));
    }

    @Override
    public Refusal missing() {
        return EventRefusal.EVENT_GROUP_NOT_HERE;
    }

    @Override
    public void requireReadable(StationSession session, TargetInfo target) {
        guards.requireOpen(session, target.id());
    }

    @Override
    public void requireWritable(StationSession session, TargetInfo target) {
        guards.requireOpen(session, target.id());
    }

    @Override
    public boolean mayModerate(StationSession session, TargetInfo target, Moderation action) {
        return action == Moderation.DELETE && session.hasPermission(StationPermission.EVENT_MANAGER);
    }

    @Override
    public CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin) {
        var told = origin == CommentOrigin.LOCAL ? CreatedAudience.THREAD : CreatedAudience.NOBODY;
        Integer stationId = target.stationId();
        return stationId == null
                ? told
                : told.withNewThreadsTo(StationAudience.holders(stationId, StationPermission.EVENT_MANAGER));
    }

    @Override
    public NotificationLink link(TargetInfo target, int commentId) {
        return NotificationLinks.comment(NotificationLinks.eventGroup(target.id()), commentId);
    }
}
