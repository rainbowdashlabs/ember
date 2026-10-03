/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PictureTags;
import dev.chojo.ember.feature.discovery.entity.RemoteLogoCheck;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient.PictureFetch;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.WebOrigins;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * The copies this instance keeps of the logos other instances publish for their stations.
 *
 * <p>The discovery page shows these copies and never the other instance's own address, so a visitor's
 * browser talks to this instance only, and a logo still shows while its instance is down. A logo is
 * fetched once a card is new and again once {@link #RECHECK_AFTER} has passed, asking with what the
 * other instance sent last time so an unchanged logo is not sent again. It is fetched only from the
 * instance that published the card, is at most {@link #MAX_LOGO_BYTES} long, has to be a raster picture
 * by its own first bytes, and is drawn again as a small square before anyone sees it, so no byte of
 * what came in is ever served.
 *
 * <p>A logo that could not be fetched leaves the copy kept before in place. Where the other instance
 * says there is none, or names an address that is not its own, the copy goes. A peer's due logos are
 * fetched {@value #PARALLEL_FETCHES} at a time.
 *
 * <p>Copies are kept per instance and station, under a short fingerprint of the instance's public key
 * followed by the station identifier, since two instances may publish a station under the same
 * identifier. That fingerprint is also how a copy is addressed from the page.
 */
@Singleton
public class RemoteStationLogoService {
    /** How long a logo is taken as it is before it is asked for again: as long as the cards are. */
    public static final Duration RECHECK_AFTER = DiscoveryStationRefreshScheduler.REFRESH_INTERVAL;

    static final int MAX_LOGO_BYTES = 1024 * 1024;
    static final int PARALLEL_FETCHES = 4;
    static final int SIDE = 256;
    private static final int DISPLAY_SIZE = 128;
    private static final int FINGERPRINT_CHARS = 32;
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{" + FINGERPRINT_CHARS + "}");
    private static final String PUBLIC_PATH = "/api/v1/public/discovery/remote/";
    private static final ImageProfile PROFILE = ImageProfile.ICON_SET;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_DISCOVERY_LOGO;
    private static final StorageScope SCOPE = new StorageScope.Instance();
    private static final Logger log = LoggerFactory.getLogger(RemoteStationLogoService.class);

    private final DiscoveryHttpClient httpClient;
    private final ImageVariants images;
    private final DiscoveryStationCacheRepository cacheRepository;
    private final Executor executor;

    @Inject
    public RemoteStationLogoService(
            DiscoveryHttpClient httpClient,
            ImageVariants images,
            DiscoveryStationCacheRepository cacheRepository,
            TaskScheduler scheduler) {
        this(httpClient, images, cacheRepository, scheduler.executor());
    }

    /**
     * Builds the service on the given executor, so a test can run the fetches one after the other.
     */
    RemoteStationLogoService(
            DiscoveryHttpClient httpClient,
            ImageVariants images,
            DiscoveryStationCacheRepository cacheRepository,
            Executor executor) {
        this.httpClient = httpClient;
        this.images = images;
        this.cacheRepository = cacheRepository;
        this.executor = executor;
    }

    /**
     * The short fingerprint of an instance's public key its logo copies are filed and addressed under.
     */
    public static String fingerprint(String instancePublicKey) {
        return Sha256.hexPrefix(instancePublicKey, FINGERPRINT_CHARS);
    }

    /**
     * Where the discovery page finds the copy of a station's logo kept here.
     */
    public static String publicAddress(String instancePublicKey, UUID stationUid) {
        return PUBLIC_PATH + fingerprint(instancePublicKey) + "/" + stationUid + "/logo?size=" + DISPLAY_SIZE;
    }

    /**
     * Brings the copies of a peer's logos up to date, for every card of it that is due. Never throws for
     * one logo: whatever goes wrong with it leaves its copy as it was.
     *
     * @param peer the peer whose cards were just stored
     * @param now  the time the cards were stored
     */
    public void refresh(DiscoveryPeer peer, Instant now) {
        var due = cacheRepository.findLogosDue(peer.publicKey(), now.minus(RECHECK_AFTER));
        var slots = new Semaphore(PARALLEL_FETCHES);
        var fetches = due.stream()
                .map(check -> CompletableFuture.runAsync(() -> refreshGuarded(peer, check, now, slots), executor))
                .toList();
        fetches.forEach(CompletableFuture::join);
    }

    private void refreshGuarded(DiscoveryPeer peer, RemoteLogoCheck check, Instant now, Semaphore slots) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            refresh(peer, check, now);
        } catch (RuntimeException e) {
            log.warn("Could not refresh the logo of station {} on {}", check.stationUid(), peer.baseUrl(), e);
        } finally {
            slots.release();
        }
    }

    /**
     * Asks for one logo. The copy kept here is trusted to be what the card's row says: it is asked
     * for with the tags it came with, and a copy that went missing from storage is caught where it
     * is read, not looked for on every refresh.
     */
    private void refresh(DiscoveryPeer peer, RemoteLogoCheck check, Instant now) {
        var stationKey = key(peer.publicKey(), check.stationUid());
        if (stationKey.isEmpty()) return;
        String key = stationKey.get();
        var address = onPeer(peer.baseUrl(), check.logoUrl());
        if (address.isEmpty()) {
            drop(peer, check, key, now);
            return;
        }
        var known = check.stored() ? check.tags() : PictureTags.NONE;
        var fetched = httpClient.fetchPicture(address.get(), known, MAX_LOGO_BYTES);
        if (fetched instanceof PictureFetch.Fetched picture && store(key, picture.data())) {
            cacheRepository.recordLogoCheck(peer.publicKey(), check.stationUid(), now, true, picture.tags());
        } else if (fetched instanceof PictureFetch.Absent) {
            drop(peer, check, key, now);
        } else {
            cacheRepository.touchLogoCheck(peer.publicKey(), check.stationUid(), now);
        }
    }

    private void drop(DiscoveryPeer peer, RemoteLogoCheck check, String key, Instant now) {
        images.delete(SCOPE, CATEGORY, key);
        cacheRepository.recordLogoCheck(peer.publicKey(), check.stationUid(), now, false, PictureTags.NONE);
    }

    private boolean store(String key, byte[] data) {
        try {
            images.storeSquare(PROFILE, SCOPE, CATEGORY, key, data, MAX_LOGO_BYTES, SIDE);
            return true;
        } catch (RefusalResponse | IOException e) {
            log.debug("Refused a logo another instance sent for key {}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * The copy of a station's logo kept here.
     *
     * @param fingerprint the instance's fingerprint, as {@link #publicAddress} writes it
     * @param stationUid  the station
     * @param size        the size asked for; {@code 0} for the full copy
     * @return the copy, or empty where none is kept or the fingerprint is none
     */
    public Optional<MediaContent> read(String fingerprint, UUID stationUid, int size) {
        if (!FINGERPRINT.matcher(fingerprint).matches()) return Optional.empty();
        var copy = images.read(PROFILE, SCOPE, CATEGORY, fingerprint + "/" + stationUid, size);
        if (copy.isEmpty()) forgetLost(fingerprint, stationUid.toString());
        return copy;
    }

    /**
     * A copy a card's row says is kept but storage does not hold is a fault: it is logged and the row
     * forgets the copy, so the next refresh fetches the logo afresh instead of asking whether it changed.
     */
    private void forgetLost(String fingerprint, String stationUid) {
        for (var instanceKey : cacheRepository.findLogoHolders(stationUid)) {
            if (!fingerprint(instanceKey).equals(fingerprint)) continue;
            log.warn("The logo copy of station {} from instance {} is missing from storage", stationUid, fingerprint);
            cacheRepository.forgetLogo(instanceKey, stationUid);
        }
    }

    /**
     * Drops the copy of one station's logo, for a card its instance no longer publishes.
     */
    public void forget(String instancePublicKey, String stationUid) {
        key(instancePublicKey, stationUid).ifPresent(key -> images.delete(SCOPE, CATEGORY, key));
    }

    /**
     * Drops the copies of every logo of an instance, for one that is blocked or removed, and forgets
     * having asked for them, so they are fetched afresh should it be shown again.
     */
    public void forgetInstance(String instancePublicKey) {
        images.delete(SCOPE, CATEGORY, fingerprint(instancePublicKey));
        cacheRepository.forgetLogos(instancePublicKey);
    }

    /**
     * Where the copy of a station's logo is filed. A station named by anything but an identifier has
     * none, which keeps whatever another instance sends out of the storage path.
     */
    private static Optional<String> key(String instancePublicKey, String stationUid) {
        try {
            return Optional.of(fingerprint(instancePublicKey) + "/" + UUID.fromString(stationUid));
        } catch (IllegalArgumentException notAnIdentifier) {
            return Optional.empty();
        }
    }

    /**
     * The address to fetch a logo from, where it lies on the instance that published the card. An Ember
     * instance answers a size on its logo address, so one is asked for to keep the transfer small.
     */
    private static Optional<String> onPeer(String baseUrl, @Nullable String logoUrl) {
        if (logoUrl == null || logoUrl.isBlank()) return Optional.empty();
        try {
            URI base = new URI(baseUrl);
            URI logo = new URI(logoUrl);
            if (!WebOrigins.sameOrigin(base, logo)) return Optional.empty();
            return Optional.of(logo.getRawQuery() == null ? logoUrl + "?size=" + SIDE : logoUrl);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
