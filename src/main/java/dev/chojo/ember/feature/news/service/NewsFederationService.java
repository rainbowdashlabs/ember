/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes.RemoteNewsCommentDeleteRequest;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes.RemoteNewsCommentRequest;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes.RemoteNewsCommentUpdateRequest;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes.RemoteNewsDetail;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes.RemoteNewsSummary;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Federated news sharing: which articles a station shares, what it serves a partner, and how it
 * reads and comments on what partners share with it.
 *
 * <p>What a partner may see and write is decided in one place, the serving functions registered in
 * {@link #serveOn}. A partner on another instance reaches them through the {@code /remote} routes,
 * a partner on this instance through the local transport, and both are held to the share targets of
 * the serving station.
 */
@Singleton
public class NewsFederationService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(NewsFederationService.class);

    private final NewsFederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationRepository partnerRepository;
    private final StationRepository stationRepository;
    private final NewsService newsService;
    private final CommentService commentService;
    private final NewsAttachmentService attachmentService;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final FederationTransport transport;

    @Inject
    public NewsFederationService(
            NewsFederationRepository federationRepository,
            FederationService federationService,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            NewsService newsService,
            CommentService commentService,
            NewsAttachmentService attachmentService,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            FederationTransport transport) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.newsService = newsService;
        this.commentService = commentService;
        this.attachmentService = attachmentService;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.transport = transport;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteNewsRoutes.LIST_NEWS, (partner, params, body) -> serveNewsList(partner));
        endpoints.serve(
                RemoteNewsRoutes.GET_NEWS, (partner, params, body) -> serveNews(partner, params.integer("newsId")));
        endpoints.serve(
                RemoteNewsRoutes.LIST_COMMENTS,
                (partner, params, body) -> serveComments(partner, params.integer("newsId")));
        endpoints.<RemoteNewsCommentRequest, CommentResponse>serve(
                RemoteNewsRoutes.CREATE_COMMENT,
                (partner, params, body) -> serveNewComment(partner, params.integer("newsId"), body));
        endpoints.<RemoteNewsCommentUpdateRequest, CommentResponse>serve(
                RemoteNewsRoutes.UPDATE_COMMENT,
                (partner, params, body) -> serveCommentEdit(partner, params.integer("commentId"), body));
        endpoints.<RemoteNewsCommentDeleteRequest, Void>serve(
                RemoteNewsRoutes.DELETE_COMMENT, (partner, params, body) -> {
                    serveCommentDeletion(partner, params.integer("commentId"), body);
                    return null;
                });
    }

    // -- Share management --

    /**
     * Configures federation sharing for a news article.
     *
     * @param newsId         the news article ID
     * @param scope          the sharing scope
     * @param visibilityRole the minimum visibility role
     * @param partnerIds     the partner IDs to target (used when scope is SPECIFIC)
     * @return the created or updated share
     */
    public NewsFederationShare setShare(
            int newsId, ShareScope scope, NewsVisibilityRole visibilityRole, List<Integer> partnerIds) {
        var share = federationRepository.setShare(newsId, scope, visibilityRole);
        federationRepository.setShareTargets(share.id(), partnerIds);
        log.info(
                "Configured news federation share {} for news {} (scope {}, {} target partner(s))",
                share.id(),
                newsId,
                scope,
                partnerIds.size());
        return share;
    }

    /**
     * Removes federation sharing for a news article.
     *
     * @param newsId the news article ID
     */
    public void removeShare(int newsId) {
        federationRepository.removeShare(newsId);
        log.info("Removed news federation share for news {}", newsId);
    }

    /**
     * Finds the federation share configuration for a news article.
     *
     * @param newsId the news article ID
     * @return the share, if configured
     */
    public Optional<NewsFederationShare> findShareByNews(int newsId) {
        return federationRepository.findShareByNews(newsId);
    }

    /**
     * Retrieves the partner IDs targeted by a share.
     *
     * @param shareId the share ID
     * @return the list of partner IDs
     */
    public List<Integer> findShareTargets(int shareId) {
        return federationRepository.findShareTargets(shareId);
    }

    /**
     * Finds news IDs shared with a partner for a given station.
     *
     * @param partnerId the serving station's own partner row, which is what share targets name
     * @param stationId the serving station
     * @return the list of shared news IDs
     */
    public List<Integer> findSharedNewsIds(int partnerId, int stationId) {
        return federationRepository.findSharedNewsIds(partnerId, stationId);
    }

    /**
     * Finds the visibility role for a shared news article.
     *
     * @param newsId the news article ID
     * @return the visibility role, if the news is shared
     */
    public Optional<NewsVisibilityRole> findVisibilityRole(int newsId) {
        return federationRepository.findVisibilityRole(newsId);
    }

    // -- Serving partners --

    /**
     * The articles this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return a summary per shared article
     */
    public List<RemoteNewsSummary> serveNewsList(ServingPartner partner) {
        return sharedWith(partner).stream()
                .map(newsService::findById)
                .flatMap(Optional::stream)
                .map(news -> new RemoteNewsSummary(
                        news.id(),
                        news.title(),
                        attachmentService.withAttachmentLinksHtml(
                                Objects.requireNonNullElse(news.contentHtml(), ""), news.id(), news.stationId()),
                        authorName(news),
                        publishedAt(news),
                        commentService.count(CommentEntityType.NEWS, news.id()),
                        visibilityOf(news.id())))
                .toList();
    }

    /**
     * One article this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @param newsId  the article asked for
     * @return the article
     */
    public RemoteNewsDetail serveNews(ServingPartner partner, int newsId) {
        requireShared(partner, newsId);
        var news = newsService.findById(newsId).orElseThrow(Refusal.REMOTE_NEWS_NOT_HERE::raise);
        return new RemoteNewsDetail(
                news.id(),
                news.title(),
                attachmentService.withAttachmentLinks(
                        Objects.requireNonNullElse(news.contentMarkdown(), ""), news.id(), news.stationId()),
                attachmentService.withAttachmentLinksHtml(
                        Objects.requireNonNullElse(news.contentHtml(), ""), news.id(), news.stationId()),
                authorName(news),
                publishedAt(news),
                commentService.count(CommentEntityType.NEWS, newsId),
                visibilityOf(newsId));
    }

    /**
     * The comments on an article this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @param newsId  the article
     * @return its comments
     */
    public List<CommentResponse> serveComments(ServingPartner partner, int newsId) {
        requireShared(partner, newsId);
        return commentService.list(CommentEntityType.NEWS, newsId, CommentFilter.ALL).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    /**
     * A partner's member commenting on an article this station shares with the partner.
     *
     * @param partner the partnership the request arrived on
     * @param newsId  the article
     * @param request who writes and what
     * @return the stored comment
     */
    public CommentResponse serveNewComment(ServingPartner partner, int newsId, RemoteNewsCommentRequest request) {
        requireShared(partner, newsId);
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.REMOTE_NEWS_COMMENT_NEEDS_TEXT.raise();
        }
        var author = new MemberIdentity(partner.askingStationUid(), request.remoteMemberUid());
        var comment = storePartnerComment(
                newsId,
                CommentWriter.partner(author, request.displayName()),
                new NewComment(request.parentId(), null, request.content()));
        eventFederationRepository.cacheName(partner.partnerId(), request.remoteMemberUid(), request.displayName());
        return toCommentResponse(comment);
    }

    /**
     * Stores a comment a partner's member wrote, telling whoever a partner's comment on an entry
     * tells, which the entry's comment target decides.
     */
    private Comment storePartnerComment(int newsId, CommentWriter writer, NewComment comment) {
        var target =
                commentService.target(CommentEntityType.NEWS, newsId).orElseThrow(Refusal.REMOTE_NEWS_NOT_HERE::raise);
        return commentService.createOn(target, writer, comment);
    }

    /**
     * A partner's member editing a comment of their own.
     *
     * @param partner   the partnership the request arrived on
     * @param commentId the comment
     * @param request   who edits and the new text
     * @return the updated comment
     */
    public CommentResponse serveCommentEdit(
            ServingPartner partner, int commentId, RemoteNewsCommentUpdateRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.REMOTE_NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE.raise();
        }
        var comment = commentService
                .findById(CommentEntityType.NEWS, commentId)
                .orElseThrow(Refusal.REMOTE_NEWS_COMMENT_NOT_HERE_ON_UPDATE::raise);
        var editor = new MemberIdentity(partner.askingStationUid(), request.remoteMemberUid());
        if (!editor.sameMember(comment.author())) {
            throw Refusal.REMOTE_NEWS_COMMENT_NOT_YOURS_TO_EDIT.raise();
        }
        return toCommentResponse(commentService
                .update(comment, CommentWriter.partner(editor, ""), request.content())
                .orElseThrow(Refusal.REMOTE_NEWS_COMMENT_NOT_HERE_AFTER_UPDATE::raise));
    }

    /**
     * A partner's member deleting a comment of their own.
     *
     * @param partner   the partnership the request arrived on
     * @param commentId the comment
     * @param request   who deletes
     */
    public void serveCommentDeletion(ServingPartner partner, int commentId, RemoteNewsCommentDeleteRequest request) {
        var comment = commentService
                .findById(CommentEntityType.NEWS, commentId)
                .orElseThrow(Refusal.REMOTE_NEWS_COMMENT_NOT_HERE_ON_DELETE::raise);
        if (!new MemberIdentity(partner.askingStationUid(), request.remoteMemberUid()).sameMember(comment.author())) {
            throw Refusal.REMOTE_NEWS_COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.delete(comment)) {
            throw Refusal.REMOTE_NEWS_COMMENT_NOT_DELETED.raise();
        }
    }

    /**
     * Confirms the partner is allowed to see the given article, i.e. it is in the set this station
     * shares with that partner. Guards every read and write so a partner cannot address
     * never-federated news by enumerating ids.
     */
    private void requireShared(ServingPartner partner, int newsId) {
        if (!sharedWith(partner).contains(newsId)) {
            throw Refusal.NEWS_NOT_SHARED_WITH_PARTNER.raise();
        }
    }

    private List<Integer> sharedWith(ServingPartner partner) {
        return findSharedNewsIds(partner.partnerId(), partner.servingStationId());
    }

    // -- Federated comment author tracking --

    /**
     * Creates a comment from a remote federated member on a news article.
     * Stores the comment with the federated author identity and caches the display name.
     */
    public Comment createRemoteComment(
            int newsId,
            int partnerId,
            UUID remoteMemberUid,
            String displayName,
            @Nullable Integer parentId,
            String content) {
        var partnerStationUid = partnerRepository
                .findPartnerById(partnerId)
                .map(FederationPartner::partnerStationId)
                .orElse(null);
        var authorIdentity = partnerStationUid != null ? new MemberIdentity(partnerStationUid, remoteMemberUid) : null;
        var comment = storePartnerComment(
                newsId,
                new CommentWriter(authorIdentity, displayName, CommentOrigin.PARTNER),
                new NewComment(parentId, null, content));
        eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        log.info("Stored federated comment {} on news {} from partner {}", comment.id(), newsId, partnerId);
        return comment;
    }

    /**
     * Browses the news every active partner shares with this station, asking them in parallel.
     */
    public List<FederatedNewsItem> browseFederatedNews(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();
        return fanout.fanOut(partners, this::browsePartner).items();
    }

    /**
     * Fetches a single article a partner shares with this station.
     */
    public FederatedNewsData getFederatedNews(int localStationId, UUID partnerStationUid, int newsId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        var detail = transport.get(partner, RemoteNewsRoutes.GET_NEWS.at(newsId), RemoteNewsDetail.class);
        return new FederatedNewsData(
                detail.id(),
                detail.title(),
                Objects.requireNonNullElse(detail.contentMarkdown(), ""),
                Objects.requireNonNullElse(detail.contentHtml(), ""),
                Objects.requireNonNullElse(detail.authorName(), ""),
                detail.publishedAt(),
                detail.commentCount(),
                detail.visibilityRole());
    }

    /**
     * Lists the comments of a news article owned by a federation partner.
     *
     * @param stationId         the requesting station ID
     * @param partnerStationUid the owning partner station UUID
     * @param newsId            the news article ID
     * @return the comments of the article
     */
    public List<CommentResponse> listFederatedComments(int stationId, UUID partnerStationUid, int newsId) {
        var partner = requirePartner(stationId, partnerStationUid);
        return transport.getList(partner, RemoteNewsRoutes.LIST_COMMENTS.at(newsId), CommentResponse.class);
    }

    /**
     * Adds a comment to a news article owned by a federation partner.
     *
     * @param stationId         the requesting station ID
     * @param partnerStationUid the owning partner station UUID
     * @param newsId            the news article ID
     * @param author            the commenting member
     * @param parentId          the parent comment for threaded replies, or {@code null}
     * @param content           the comment text
     * @return the created comment
     */
    public CommentResponse createFederatedComment(
            int stationId,
            UUID partnerStationUid,
            int newsId,
            FederatedCommentAuthor author,
            Integer parentId,
            String content) {
        var partner = requirePartner(stationId, partnerStationUid);
        var body = new RemoteNewsCommentRequest(author.memberUid(), author.displayName(), parentId, content);
        var created = transport.send(partner, RemoteNewsRoutes.CREATE_COMMENT.at(newsId), body, CommentResponse.class);
        log.info("Station {} commented on news article {} at partner {}", stationId, newsId, partner.id());
        return created;
    }

    /**
     * Edits a comment the requesting member wrote on a news article owned by a federation partner.
     *
     * @param stationId         the requesting station ID
     * @param partnerStationUid the owning partner station UUID
     * @param commentId         the comment ID
     * @param author            the commenting member
     * @param content           the new comment text
     * @return the updated comment
     */
    public CommentResponse updateFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, FederatedCommentAuthor author, String content) {
        var partner = requirePartner(stationId, partnerStationUid);
        var body = new RemoteNewsCommentUpdateRequest(author.memberUid(), content);
        var updated =
                transport.send(partner, RemoteNewsRoutes.UPDATE_COMMENT.at(commentId), body, CommentResponse.class);
        log.info("Station {} edited its comment {} at partner {}", stationId, commentId, partner.id());
        return updated;
    }

    /**
     * Deletes a comment the requesting member wrote on a news article owned by a federation partner.
     *
     * @param stationId         the requesting station ID
     * @param partnerStationUid the owning partner station UUID
     * @param commentId         the comment ID
     * @param author            the commenting member
     */
    public void deleteFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, FederatedCommentAuthor author) {
        var partner = requirePartner(stationId, partnerStationUid);
        transport.send(
                partner,
                RemoteNewsRoutes.DELETE_COMMENT.at(commentId),
                new RemoteNewsCommentDeleteRequest(author.memberUid()),
                Void.class);
        log.info("Station {} deleted its comment {} at partner {}", stationId, commentId, partner.id());
    }

    private FederationPartner requirePartner(int stationId, UUID partnerStationUid) {
        return partnerRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerStationUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private CommentResponse toCommentResponse(Comment comment) {
        return CommentResponseMapper.fromNews(memberNameResolver, comment);
    }

    private List<FederatedNewsItem> browsePartner(FederationPartner partner) {
        String stationName = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        return transport.getList(partner, RemoteNewsRoutes.LIST_NEWS.at(), RemoteNewsSummary.class).stream()
                .map(entry -> new FederatedNewsItem(
                        partner.id(),
                        stationName,
                        partner.partnerStationId().toString(),
                        new FederatedNewsData(
                                entry.id(),
                                entry.title(),
                                "",
                                Objects.requireNonNullElse(entry.contentHtml(), ""),
                                Objects.requireNonNullElse(entry.authorName(), ""),
                                entry.publishedAt(),
                                entry.commentCount(),
                                entry.visibilityRole())))
                .toList();
    }

    private String authorName(News news) {
        var resolved = news.author() != null ? memberNameResolver.resolveDisplay(news.author()) : null;
        return resolved != null && resolved.name() != null ? resolved.name() : "";
    }

    private static String publishedAt(News news) {
        return news.publishedAt() != null ? news.publishedAt().toString() : "";
    }

    private NewsVisibilityRole visibilityOf(int newsId) {
        return findVisibilityRole(newsId).orElse(NewsVisibilityRole.MEMBER);
    }

    /**
     * The member writing on a partner station, in both the shape the local database stores and
     * the shape a partner instance expects.
     *
     * @param identity    the member's own identity
     * @param memberUid   the member UUID sent to the partner
     * @param displayName the name shown next to the comment
     */
    public record FederatedCommentAuthor(MemberIdentity identity, UUID memberUid, String displayName) {}

    /**
     * A federated news item with partner info.
     */
    public record FederatedNewsItem(
            int partnerId, String partnerStationName, String partnerStationUid, FederatedNewsData news) {}

    public record FederatedNewsData(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            NewsVisibilityRole visibilityRole) {}
}
