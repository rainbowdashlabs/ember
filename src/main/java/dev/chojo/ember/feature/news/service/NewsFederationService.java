/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.news.entity.News;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Inject
    public NewsFederationService(
            NewsFederationRepository federationRepository,
            FederationService federationService,
            FederationRepository partnerRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            NewsService newsService) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.partnerRepository = partnerRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.newsService = newsService;
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
                items.add(
                        new FederatedNewsItem(partner.id(), partnerStationName(partner), toNewsMap(n, visibilityRole)));
            });
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private List<FederatedNewsItem> browseNewsViaHttp(Station localStation, FederationPartner partner) {
        var remoteNews = httpClient.signedGetList(
                partner.remoteHost(), "/remote/news", localStation.id(), localStation.federationPrivateKey());
        return remoteNews.stream()
                .map(news -> new FederatedNewsItem(partner.id(), partnerStationName(partner), news))
                .toList();
    }

    /**
     * Fetches a single federated news article by partner station UUID and news ID.
     * Transparently handles local and remote partners.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFederatedNews(int localStationId, UUID partnerStationUid, int newsId) {
        var partner = partnerRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        if (partner.isRemote()) {
            String json = httpClient.signedGetJson(
                    partner.remoteHost(),
                    "/remote/news/" + newsId,
                    localStationId,
                    stationRepository
                            .findById(localStationId)
                            .map(Station::federationPrivateKey)
                            .orElse(null));
            if (json == null) throw new IllegalStateException("Failed to fetch news from remote partner");
            try {
                return httpClient.getMapper().readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote news response", e);
            }
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
                    return toNewsMap(n, visibilityRole);
                })
                .orElseThrow();
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("?");
    }

    private Map<String, Object> toNewsMap(News n, String visibilityRole) {
        var map = new HashMap<String, Object>();
        map.put("id", n.id());
        map.put("title", n.title());
        map.put("contentMarkdown", n.contentMarkdown() != null ? n.contentMarkdown() : "");
        map.put("contentHtml", n.contentHtml() != null ? n.contentHtml() : "");
        map.put("publishedAt", n.publishedAt() != null ? n.publishedAt().toString() : "");
        map.put("commentCount", newsService.countComments(n.id()));
        map.put("visibilityRole", visibilityRole);
        return map;
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
    public record FederatedNewsItem(int partnerId, String partnerStationName, Object news) {}
}
