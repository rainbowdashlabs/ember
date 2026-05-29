/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsCommentFederatedAuthor;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import dev.chojo.ember.feature.news.repository.NewsFederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final EventFederationRepository eventFederationRepository;

    @Inject
    public NewsFederationService(
            NewsFederationRepository federationRepository,
            FederationService federationService,
            FederationRepository partnerRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            NewsService newsService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            EventFederationRepository eventFederationRepository) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.partnerRepository = partnerRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.newsService = newsService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.eventFederationRepository = eventFederationRepository;
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
    public NewsFederationShare setShare(int newsId, String scope, String visibilityRole, List<Integer> partnerIds) {
        var share = federationRepository.setShare(newsId, scope, visibilityRole);
        federationRepository.setShareTargets(share.id(), partnerIds);
        return share;
    }

    /**
     * Removes federation sharing for a news article.
     *
     * @param newsId the news article ID
     */
    public void removeShare(int newsId) {
        federationRepository.removeShare(newsId);
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
    public Optional<String> findVisibilityRole(int newsId) {
        return federationRepository.findVisibilityRole(newsId);
    }

    // -- Federated comment author tracking --

    /**
     * Records the federated author for a news comment.
     *
     * @param commentId      the local comment ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     */
    public void setFederatedCommentAuthor(int commentId, int partnerId, UUID remoteMemberId) {
        federationRepository.setFederatedCommentAuthor(commentId, partnerId, remoteMemberId);
    }

    /**
     * Creates a comment from a remote federated member on a news article.
     * Stores the comment with {@code authorId=null}, records the federated author mapping,
     * and caches the display name.
     */
    public NewsComment createRemoteComment(
            int stationId,
            int newsId,
            int partnerId,
            UUID remoteMemberUid,
            String displayName,
            Integer parentId,
            String content) {
        var comment = newsService.createComment(stationId, newsId, parentId, null, displayName, content);
        federationRepository.setFederatedCommentAuthor(comment.id(), partnerId, remoteMemberUid);
        eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        return comment;
    }

    /**
     * Finds the federated author for a news comment.
     *
     * @param commentId the local comment ID
     * @return the federated author, if present
     */
    public Optional<NewsCommentFederatedAuthor> findFederatedCommentAuthor(int commentId) {
        return federationRepository.findFederatedCommentAuthor(commentId);
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

        var futures = new ArrayList<CompletableFuture<List<FederatedNewsItem>>>();
        for (var partner : partners) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return browseNewsViaHttp(station, partner);
                } else {
                    return browseNewsDirect(partner);
                }
            }));
        }
        return collectResults(futures);
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
                String visibilityRole = findVisibilityRole(newsId).orElse("MEMBER");
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
                "/remote/news",
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

    /**
     * Fetches a single federated news article by partner station UUID and news ID.
     * Transparently handles local and remote partners.
     */
    public FederatedNewsData getFederatedNews(int localStationId, UUID partnerStationUid, int newsId) {
        var partner = partnerRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        if (partner.isRemote()) {
            var result = httpClient.get(
                    partner.remoteHost(),
                    "/remote/news/" + newsId,
                    localStationId,
                    stationRepository
                            .findById(localStationId)
                            .map(Station::federationPrivateKey)
                            .orElse(null),
                    FederatedNewsData.class);
            if (result == null) throw new IllegalStateException("Failed to fetch news from remote partner");
            return result;
        }
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElseThrow();
        var newsIds = findSharedNewsIds(partner.id(), partnerStationId);
        if (!newsIds.contains(newsId)) {
            throw new IllegalArgumentException("News not shared with this partner");
        }
        return newsService
                .findById(newsId)
                .map(n -> {
                    String visibilityRole = findVisibilityRole(newsId).orElse("MEMBER");
                    return toNewsData(n, visibilityRole);
                })
                .orElseThrow();
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("?");
    }

    private FederatedNewsData toNewsData(News n, String visibilityRole) {
        String authorName = stationMemberRepository
                .findById(n.authorId())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
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

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation news fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation news results", e);
            }
        }
        return result;
    }

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
            String visibilityRole) {}

    private record RemoteNewsListEntry(
            int id,
            String title,
            String contentHtml,
            String authorName,
            String publishedAt,
            int commentCount,
            String visibilityRole) {}
}
