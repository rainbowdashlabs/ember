/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PublishedRemoteStation;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The stations other instances publish, ready for the public discovery page.
 *
 * <p>Read from the local cache only: the page is public, and a request that waited on every peer would
 * be as slow as the slowest of them. The cache is kept fresh in the background by
 * {@link DiscoveryStationRefreshScheduler}.
 *
 * <p>A card is left out when its identifier is no station identifier or its instance is known by an
 * address that is not a web address, since neither can be linked safely.
 */
@Singleton
public class RemoteStationListingService {
    private static final String PUBLIC_STATION_PATH = "/public/station/";

    private final DiscoveryStationCacheRepository cacheRepository;
    private final DiscoveryPingService pingService;

    @Inject
    public RemoteStationListingService(
            DiscoveryStationCacheRepository cacheRepository, DiscoveryPingService pingService) {
        this.cacheRepository = cacheRepository;
        this.pingService = pingService;
    }

    /**
     * Every station another trusted instance publishes, by station name.
     */
    public List<RemoteStation> list() {
        var self = pingService.selfIdentity();
        var published = cacheRepository.findPublishedElsewhere(self.publicKey(), self.baseUrl());
        List<RemoteStation> stations = new ArrayList<>(published.size());
        for (var station : published) {
            toRemoteStation(station).ifPresent(stations::add);
        }
        return stations;
    }

    private static Optional<RemoteStation> toRemoteStation(PublishedRemoteStation published) {
        var card = published.card();
        var uid = parseUid(card.stationUid());
        var instance = webAddress(published.instanceBaseUrl());
        if (uid.isEmpty() || instance.isEmpty()) return Optional.empty();
        String logoUrl = published.logoStored()
                ? RemoteStationLogoService.publicAddress(published.instancePublicKey(), uid.get())
                : null;
        return Optional.of(new RemoteStation(
                uid.get(),
                card,
                instance.get().getHost(),
                stripTrailingSlash(published.instanceBaseUrl()),
                publicPageUrl(published),
                logoUrl));
    }

    /**
     * The station's public page on its instance, where the card says it has one. The address is built
     * from the instance as it is known here and never taken from the card itself.
     */
    private static @Nullable String publicPageUrl(PublishedRemoteStation published) {
        var named = published.card().contactUrl();
        if (named == null || named.isBlank()) return null;
        return stripTrailingSlash(published.instanceBaseUrl()) + PUBLIC_STATION_PATH + pathSegment(published.card());
    }

    private static Optional<UUID> parseUid(String uid) {
        if (uid == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(uid));
        } catch (IllegalArgumentException notAnIdentifier) {
            return Optional.empty();
        }
    }

    private static Optional<URI> webAddress(String baseUrl) {
        if (baseUrl == null) return Optional.empty();
        try {
            var uri = new URI(baseUrl);
            var scheme = uri.getScheme();
            boolean web = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
            return web && uri.getHost() != null ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    private static String pathSegment(DiscoveryStationCard card) {
        return URLEncoder.encode(card.publicAddress(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * One station of another instance.
     *
     * @param stationUid     the station's identifier
     * @param card           what the instance publishes about it
     * @param instanceHost   the host name of the instance it belongs to
     * @param instanceUrl    the address that instance is known by here, with its port and without a
     *                       trailing slash
     * @param publicPageUrl  the station's public page on that instance, or {@code null} where it has
     *                       none to show
     * @param logoUrl        where this instance serves its copy of the station's logo, or {@code null}
     *                       where it keeps none
     */
    public record RemoteStation(
            UUID stationUid,
            DiscoveryStationCard card,
            String instanceHost,
            String instanceUrl,
            @Nullable String publicPageUrl,
            @Nullable String logoUrl) {}
}
