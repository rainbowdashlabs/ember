/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The comments on every kind of target: reading, writing, changing and removing them, and telling
 * whoever a comment concerns.
 *
 * <p>What all kinds share lives here: the thread, the check that an answer stays on the target of
 * the comment it answers, the soft delete, the one preview cut, the mentions and the events. What
 * differs between kinds (who may read, write and moderate, who else is told, where a notification
 * opens) is asked of the {@link CommentTarget} bound for the kind.
 *
 * <p>The refusals a route answers for a comment that is missing or not the caller's to change stay
 * the route's own, since their codes are public; the service names only what is new to all kinds,
 * an answer to a comment on another target.
 */
@Singleton
public class CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final int PREVIEW_LENGTH = 100;
    private static final int NO_STATION = -1;

    private final CommentRepository repository;
    private final Map<CommentEntityType, CommentTarget> targets;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final StationRepository stationRepository;
    private final CommentMentions mentions;

    @Inject
    public CommentService(
            CommentRepository repository,
            Map<CommentEntityType, CommentTarget> targets,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            StationRepository stationRepository,
            CommentMentions mentions) {
        this.repository = repository;
        this.targets = targets;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.stationRepository = stationRepository;
        this.mentions = mentions;
    }

    private static String preview(String content) {
        return content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH) + "…" : content;
    }

    private static int stationOf(TargetInfo target) {
        return Objects.requireNonNullElse(target.stationId(), 0);
    }

    private CommentTarget targetOf(CommentEntityType type) {
        return Objects.requireNonNull(targets.get(type), () -> "No comment target is bound for " + type);
    }

    /**
     * The thing a comment of the given kind hangs under.
     *
     * @param type     the kind of target
     * @param targetId the target
     * @return the target, empty when it does not exist
     */
    public Optional<TargetInfo> target(CommentEntityType type, int targetId) {
        return targetOf(type).find(targetId);
    }

    /**
     * The target, after asserting the member may read its comments. A missing target is refused the
     * way its kind names it.
     *
     * @param session  the reader
     * @param type     the kind of target
     * @param targetId the target
     * @return the target
     */
    public TargetInfo requireReadable(UserSession session, CommentEntityType type, int targetId) {
        var target = targetOf(type);
        var info = target.find(targetId).orElseThrow(() -> target.missing().raise());
        target.requireReadable(session, info);
        return info;
    }

    /**
     * The comments on one target, oldest first.
     *
     * @param type     the kind of target
     * @param targetId the target
     * @param filter   which of them to show
     * @return the comments, deleted placeholders included so the threads keep their shape
     */
    public List<Comment> list(CommentEntityType type, int targetId, CommentFilter filter) {
        return switch (filter) {
            case CommentFilter.All all -> repository.findByTarget(type, targetId);
            case CommentFilter.Occurrence occurrence -> repository.findByEventOccurrence(targetId, occurrence.date());
            case CommentFilter.FromStation from -> repository.findByTargetFrom(type, targetId, from.stationUid());
        };
    }

    /**
     * How many comments one target carries, deleted placeholders included.
     *
     * @param type     the kind of target
     * @param targetId the target
     * @return the number of comments
     */
    public int count(CommentEntityType type, int targetId) {
        return repository.count(type, targetId);
    }

    /**
     * Finds a comment of the given kind. A comment of another kind is never found here, whatever its
     * id.
     *
     * @param type the kind of target the comment must hang under
     * @param id   the comment
     * @return the comment, if there is one of that kind
     */
    public Optional<Comment> findById(CommentEntityType type, int id) {
        return repository.findById(type, id);
    }

    /**
     * Writes a comment as a member, after asserting they may write on the target.
     *
     * @param session  the writer
     * @param type     the kind of target
     * @param targetId the target
     * @param writer   who writes, with the name notifications show
     * @param comment  the comment
     * @return the stored comment
     */
    public Comment create(
            UserSession session, CommentEntityType type, int targetId, CommentWriter writer, NewComment comment) {
        var target = targetOf(type);
        var info = target.find(targetId).orElseThrow(() -> target.missing().raise());
        target.requireWritable(session, info);
        return store(info, writer, comment);
    }

    /**
     * Writes a comment whose writer the caller has already let in: a member of a partner station,
     * whose request was checked against what is shared where it arrived, or a comment the demo
     * seeds. Whom it tells follows from the writer's origin, as for any other comment.
     *
     * @param target  the target
     * @param writer  who writes
     * @param comment the comment
     * @return the stored comment
     */
    public Comment createOn(TargetInfo target, CommentWriter writer, NewComment comment) {
        return store(target, writer, comment);
    }

    private Comment store(TargetInfo target, CommentWriter writer, NewComment comment) {
        requireParentOn(target, comment.parentId());
        var stored = repository.create(
                target.type(),
                target.id(),
                comment.eventDate(),
                comment.parentId(),
                writer.identity(),
                comment.content());
        log.info(
                "Created {} comment {} on {} (station {})",
                target.type(),
                stored.id(),
                target.id(),
                target.stationId());
        announceCreated(target, stored, writer);
        return stored;
    }

    /**
     * Refuses an answer to a comment that is not on the same target, which would otherwise tell the
     * author of a comment elsewhere about it and open the wrong page for them.
     */
    private void requireParentOn(TargetInfo target, @Nullable Integer parentId) {
        if (parentId == null) return;
        boolean onTarget = repository
                .findById(target.type(), parentId)
                .filter(parent -> parent.targetId() == target.id())
                .isPresent();
        if (!onTarget) throw Refusal.COMMENT_PARENT_ELSEWHERE.raise();
    }

    private void announceCreated(TargetInfo target, Comment comment, CommentWriter writer) {
        var audience = targetOf(target.type()).audienceFor(target, writer.origin());
        int stationId = stationOf(target);
        Integer authorMemberId = resolveLocalMemberId(stationId, writer.identity());
        Integer parentAuthorId = audience.parentAuthor() ? parentAuthorOf(stationId, comment) : null;
        boolean answersSomebodyElse = parentAuthorId != null && !parentAuthorId.equals(authorMemberId);
        if (answersSomebodyElse || audience.others() != null) {
            eventBus.publish(new CommentCreated(
                    stationId,
                    target.type(),
                    target.title(),
                    targetOf(target.type()).link(target, comment.id()),
                    comment.id(),
                    comment.parentId(),
                    parentAuthorId,
                    authorMemberId,
                    writer.name(),
                    preview(comment.content()),
                    audience.others()));
        }
        if (audience.mentions()) {
            mentions.announce(
                    origin(target, comment.id(), authorMemberId, writer.name(), comment.content()), comment.content());
        }
    }

    private @Nullable Integer parentAuthorOf(int stationId, Comment comment) {
        Integer parentId = comment.parentId();
        if (parentId == null) return null;
        return repository
                .findById(comment.type(), parentId)
                .map(parent -> resolveLocalMemberId(stationId, parent.author()))
                .orElse(null);
    }

    private CommentMentions.Origin origin(
            TargetInfo target, int commentId, @Nullable Integer authorMemberId, String authorName, String content) {
        return new CommentMentions.Origin(
                stationOf(target),
                authorMemberId,
                authorName,
                target.type(),
                target.title(),
                targetOf(target.type()).link(target, commentId),
                commentId,
                preview(content));
    }

    /**
     * Whether a member may change or remove a comment: its author always may, anybody else only
     * where the target lets them moderate.
     *
     * @param session the member
     * @param actor   the member's identity
     * @param comment the comment
     * @param action  what they want to do with it
     * @return {@code true} when they may
     */
    public boolean mayModify(UserSession session, MemberIdentity actor, Comment comment, Moderation action) {
        var author = comment.author();
        if (author != null && author.sameMember(actor)) return true;
        var target = targetOf(comment.type());
        return target.find(comment.targetId())
                .map(info -> target.mayModerate(session, info, action))
                .orElse(false);
    }

    /**
     * Refuses unless the comment hangs under something the member's station owns.
     *
     * @param session the member
     * @param comment the comment
     */
    public void requireSameStation(UserSession session, Comment comment) {
        RouteSupport.requireSameStation(session, Objects.requireNonNullElse(comment.stationId(), NO_STATION));
    }

    /**
     * Rewrites a comment and announces the mentions the edit added. Whoever the comment already
     * mentioned is not told again, and an edit from a partner station tells nobody, as its comment
     * did not either.
     *
     * @param previous the comment as it stands
     * @param writer   who changes it
     * @param content  the new text
     * @return the changed comment, empty when it went in the meantime
     */
    public Optional<Comment> update(Comment previous, CommentWriter writer, String content) {
        if (!repository.update(previous.type(), previous.id(), content)) {
            log.warn("Update for {} comment {} affected zero rows", previous.type(), previous.id());
            return Optional.empty();
        }
        log.info("Updated {} comment {}", previous.type(), previous.id());
        target(previous.type(), previous.targetId())
                .ifPresent(target -> announceAddedMentions(target, previous, writer, content));
        return repository.findById(previous.type(), previous.id());
    }

    private void announceAddedMentions(TargetInfo target, Comment previous, CommentWriter writer, String content) {
        if (!targetOf(target.type()).audienceFor(target, writer.origin()).mentions()) return;
        Integer authorMemberId = resolveLocalMemberId(stationOf(target), writer.identity());
        mentions.announceAdded(
                origin(target, previous.id(), authorMemberId, writer.name(), content), previous.content(), content);
    }

    /**
     * Removes a comment and announces the removal, so that whatever was written about it can be
     * withdrawn. A comment others answered stays as an empty placeholder.
     *
     * @param comment the comment
     * @return {@code true} if the comment was removed
     */
    public boolean delete(Comment comment) {
        var target = target(comment.type(), comment.targetId());
        if (!repository.delete(comment.type(), comment.id())) {
            log.warn("Delete for {} comment {} affected zero rows", comment.type(), comment.id());
            return false;
        }
        target.ifPresent(info -> eventBus.publish(new CommentDeleted(
                stationOf(info), comment.type(), targetOf(comment.type()).link(info, comment.id()), comment.id())));
        log.info("Deleted {} comment {}", comment.type(), comment.id());
        return true;
    }

    /**
     * The member of the given station an identity names, {@code null} when the identity is missing
     * or belongs to another station.
     */
    private @Nullable Integer resolveLocalMemberId(int stationId, @Nullable MemberIdentity identity) {
        if (identity == null) return null;
        int identityStationId =
                stationRepository.resolveId(identity.stationUid()).orElse(0);
        if (identityStationId != stationId) return null;
        return stationMemberService.resolveId(stationId, identity.memberUid()).orElse(null);
    }
}
