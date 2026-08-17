/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.news.route.RemoteNewsRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service providing business logic for federated news sharing.
 */
@Singleton
public class NewsFederationService {
    private static final Logger log = LoggerFactory.getLogger(NewsFederationService.class);

    private final NewsFederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationRepository partnerRepository;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;
    private final NewsService newsService;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;

    @Inject
    public NewsFederationService(
            NewsFederationRepository federationRepository,
            FederationService federationService,
            FederationRepository partnerRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            NewsService newsService,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.partnerRepository = partnerRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.newsService = newsService;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
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
     * @param partnerId the federation partner ID
     * @param stationId the station ID
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

    // -- Federated comment author tracking --

    /**
     * Creates a comment from a remote federated member on a news article.
     * Stores the comment with the federated author identity and caches the display name.
     */
    public NewsComment createRemoteComment(
            int stationId,
            int newsId,
            int partnerId,
            UUID remoteMemberUid,
            String displayName,
            Integer parentId,
            String content) {
        var partnerStationUid = partnerRepository
                .findPartnerById(partnerId)
                .map(FederationPartner::partnerStationId)
                .orElse(null);
        var authorIdentity = partnerStationUid != null ? new MemberIdentity(partnerStationUid, remoteMemberUid) : null;
        var comment = newsService.createComment(stationId, newsId, parentId, authorIdentity, displayName, content);
        eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        log.info("Stored federated comment {} on news {} from partner {}", comment.id(), newsId, partnerId);
        return comment;
    }

    // -- Federated browsing (parallel fetch from all partners) --

    /**
     * Browses federated news from all active partners with parallel fetching.
     * Local partners are queried via direct DB, remote partners via HTTP.
     */
    public List<FederatedNewsItem> browseFederatedNews(int stationId) {
        var station = stationRepository.findById(stationId).orElseThrow();
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();
        return fanout.fanOut(partners, this::browseNewsDirect, partner -> browseNewsViaHttp(station, partner));
    }

    /**
     * Fetches a single federated news article by partner station UUID and news ID.
     * Transparently handles local and remote partners.
     */
    public FederatedNewsData getFederatedNews(int localStationId, UUID partnerStationUid, int newsId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                RemoteNewsRoutes.GET_NEWS.at(newsId),
                FederatedNewsData.class,
                "news",
                partner -> {
                    int partnerStationId = stationRepository
                            .findByUid(partner.partnerStationId())
                            .map(Station::id)
                            .orElseThrow();
                    var newsIds = findSharedNewsIds(partner.id(), partnerStationId);
                    if (!newsIds.contains(newsId)) {
                        throw new BadRequestResponse("News not shared with this partner");
                    }
                    return newsService
                            .findById(newsId)
                            .map(n -> {
                                NewsVisibilityRole visibilityRole =
                                        findVisibilityRole(newsId).orElse(NewsVisibilityRole.MEMBER);
                                return toNewsData(n, visibilityRole);
                            })
                            .orElseThrow();
                });
    }

    /**
     * Lists the comments of a news article owned by a federation partner. Partners on this
     * instance are read from the database, partners on another instance over the signed
     * federation endpoint.
     *
     * @param stationId         the requesting station ID
     * @param partnerStationUid the owning partner station UUID
     * @param newsId            the news article ID
     * @return the comments of the article
     */
    public List<CommentResponse> listFederatedComments(int stationId, UUID partnerStationUid, int newsId) {
        var partner = requirePartner(stationId, partnerStationUid);
        if (!partner.isRemote()) {
            return newsService.findComments(newsId).stream()
                    .map(this::toCommentResponse)
                    .toList();
        }
        var station = localStation(stationId);
        return httpClient.getList(
                partner.remoteHost(),
                RemoteNewsRoutes.LIST_COMMENTS.at(newsId),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
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
        if (!partner.isRemote()) {
            var comment = newsService.createComment(
                    owningStationId(partnerStationUid),
                    newsId,
                    parentId,
                    author.identity(),
                    author.displayName(),
                    content);
            eventFederationRepository.cacheName(partner.id(), author.memberUid(), author.displayName());
            return toCommentResponse(comment);
        }
        var station = localStation(stationId);
        var body = new RemoteNewsRoutes.RemoteNewsCommentRequest(
                author.memberUid(), author.displayName(), parentId, content);
        var result = httpClient.post(
                partner.remoteHost(),
                RemoteNewsRoutes.CREATE_COMMENT.at(newsId),
                body,
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
        if (result == null) {
            throw new InternalServerErrorResponse("Failed to create comment on partner");
        }
        return result;
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
        if (!partner.isRemote()) {
            requireOwnComment(commentId, author, "You can only edit your own comments");
            newsService.updateComment(commentId, content);
            var updated = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
            return toCommentResponse(updated);
        }
        var station = localStation(stationId);
        var body = new RemoteNewsRoutes.RemoteNewsCommentUpdateRequest(author.memberUid(), content);
        var result = httpClient.put(
                partner.remoteHost(),
                RemoteNewsRoutes.UPDATE_COMMENT.at(commentId),
                body,
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
        if (result == null) {
            throw new InternalServerErrorResponse("Failed to update comment on partner");
        }
        return result;
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
        if (!partner.isRemote()) {
            requireOwnComment(commentId, author, "You can only delete your own comments");
            if (!newsService.deleteComment(owningStationId(partnerStationUid), commentId)) {
                throw new NotFoundResponse();
            }
            return;
        }
        var station = localStation(stationId);
        boolean deleted = httpClient.delete(
                partner.remoteHost(),
                RemoteNewsRoutes.DELETE_COMMENT.at(commentId),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey());
        if (!deleted) {
            throw new InternalServerErrorResponse("Failed to delete comment on partner");
        }
    }

    private FederationPartner requirePartner(int stationId, UUID partnerStationUid) {
        return partnerRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerStationUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    /**
     * The station that owns the article, for partners hosted on this instance.
     *
     * <p>A partner row belongs to the station that requested the partnership, so its
     * {@code stationId} is the <em>commenting</em> station, not the one holding the article.
     * Comment notifications and member lookups have to run against the owner, which is what the
     * {@code /remote/} handlers do - there the row belongs to the serving station and its
     * {@code stationId} is already the owner. This keeps the same-instance path consistent with that.
     */
    private int owningStationId(UUID partnerStationUid) {
        return stationRepository
                .resolveId(partnerStationUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner station"));
    }

    private Station localStation(int stationId) {
        return stationRepository.findById(stationId).orElseThrow();
    }

    private void requireOwnComment(int commentId, FederatedCommentAuthor author, String message) {
        var comment = newsService.findCommentById(commentId).orElseThrow(NotFoundResponse::new);
        if (!author.identity().sameMember(comment.author())) {
            throw new ForbiddenResponse(message);
        }
    }

    private CommentResponse toCommentResponse(NewsComment comment) {
        return CommentResponseMapper.fromNews(memberNameResolver, comment);
    }

    private List<FederatedNewsItem> browseNewsDirect(FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
        var newsIds = findSharedNewsIds(partner.id(), partnerStationId);
        var items = new ArrayList<FederatedNewsItem>();
        for (int newsId : newsIds) {
            newsService.findById(newsId).ifPresent(n -> {
                NewsVisibilityRole visibilityRole = findVisibilityRole(newsId).orElse(NewsVisibilityRole.MEMBER);
                items.add(new FederatedNewsItem(
                        partner.id(),
                        partnerStationName(partner),
                        partner.partnerStationId().toString(),
                        toNewsData(n, visibilityRole)));
            });
        }
        return items;
    }

    private List<FederatedNewsItem> browseNewsViaHttp(Station localStation, FederationPartner partner) {
        var remoteNews = httpClient.getList(
                partner.remoteHost(),
                RemoteNewsRoutes.LIST_NEWS.at(),
                partner.partnerStationId(),
                localStation.id(),
                localStation.federationPrivateKey(),
                RemoteNewsListEntry.class);
        return remoteNews.stream()
                .map(entry -> {
                    var data = new FederatedNewsData(
                            entry.id(),
                            entry.title(),
                            "",
                            entry.contentHtml() != null ? entry.contentHtml() : "",
                            entry.authorName() != null ? entry.authorName() : "",
                            entry.publishedAt(),
                            entry.commentCount(),
                            entry.visibilityRole());
                    return new FederatedNewsItem(
                            partner.id(),
                            partnerStationName(partner),
                            partner.partnerStationId().toString(),
                            data);
                })
                .toList();
    }

    private String partnerStationName(FederationPartner partner) {
        return FederationDisplayNames.partnerName(stationRepository, partner, "?");
    }

    private FederatedNewsData toNewsData(News n, NewsVisibilityRole visibilityRole) {
        var authorResolved = n.author() != null ? memberNameResolver.resolveDisplay(n.author()) : null;
        String authorName = authorResolved != null && authorResolved.name() != null ? authorResolved.name() : "";
        return new FederatedNewsData(
                n.id(),
                n.title(),
                n.contentMarkdown() != null ? n.contentMarkdown() : "",
                n.contentHtml() != null ? n.contentHtml() : "",
                authorName,
                n.publishedAt() != null ? n.publishedAt().toString() : "",
                newsService.countComments(n.id()),
                visibilityRole);
    }

    /**
     * The member writing on a partner station, in both the shape the local database stores and
     * the shape a partner instance expects.
     *
     * @param identity    the local author identity used when the partner lives on this instance
     * @param memberUid   the member UUID sent to a partner on another instance
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

    private record RemoteNewsListEntry(
            int id,
            String title,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            NewsVisibilityRole visibilityRole) {}
}
