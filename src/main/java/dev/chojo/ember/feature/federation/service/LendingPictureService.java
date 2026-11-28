/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingPicture;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.service.InventoryPictureService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The photos of gear partner stations offer for lending.
 *
 * <p>The lending station hands out a photo only when it belongs to an inventory or a kind with
 * something on offer to the asking station right now, drawn small before it leaves, and never one
 * heavier than a borrower keeps. The borrowing station keeps its own copy, decoded and drawn again
 * here as a small square so no byte of what came in is ever served, and serves the browser only that
 * copy. Each asking station keeps copies of its own, so a station sees only what was offered to it. A
 * photo's key changes with every new photo, so a copy never shows an old photo. A photo that could not
 * be kept is asked for again on the next view.
 *
 * <p>Copies are kept for a while only. They are filed under the period of {@link #KEEP_FOR} they were
 * fetched in, read from the current period or the one before, and every older period is removed by
 * {@link #removeExpiredCopies}. A copy therefore lives between one and two periods, after which the
 * partner is asked again, which is also how a photo the partner stopped offering disappears here.
 */
@Singleton
public class LendingPictureService implements FederationServer, TaskSource {
    private static final Logger log = LoggerFactory.getLogger(LendingPictureService.class);
    private static final ImageProfile PROFILE = ImageProfile.ICON_SET;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_PARTNER_LENDING;
    private static final StorageScope SCOPE = new StorageScope.Instance();
    /** The side the lending station draws a photo at before it travels. */
    static final int TRAVEL_SIDE = 256;
    /** The most a photo may weigh when it arrives, well above a drawn {@link #TRAVEL_SIDE} picture. */
    static final int MAX_PICTURE_BYTES = 1024 * 1024;
    /** How long one period of kept copies lasts; a copy lives between one and two of them. */
    static final Duration KEEP_FOR = Duration.ofHours(12);

    private final LendingService lendingService;
    private final InventoryPictureService pictures;
    private final FederationService federationService;
    private final FederationTransport transport;
    private final ImageVariants images;
    private final StorageService storage;
    private final Clock clock;

    @Inject
    public LendingPictureService(
            LendingService lendingService,
            InventoryPictureService pictures,
            FederationService federationService,
            FederationTransport transport,
            ImageVariants images,
            StorageService storage) {
        this(lendingService, pictures, federationService, transport, images, storage, Clock.systemUTC());
    }

    LendingPictureService(
            LendingService lendingService,
            InventoryPictureService pictures,
            FederationService federationService,
            FederationTransport transport,
            ImageVariants images,
            StorageService storage,
            Clock clock) {
        this.lendingService = lendingService;
        this.pictures = pictures;
        this.federationService = federationService;
        this.transport = transport;
        this.images = images;
        this.storage = storage;
        this.clock = clock;
    }

    /** Registers the answer this station gives a lending partner asking for a photo. */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteLendingRoutes.GET_PICTURE,
                (partner, params, body) -> servePicture(partner, params.text("pictureKey")));
    }

    /**
     * A photo of offered gear for a partner, drawn small.
     *
     * @param partner    the partnership the question arrived on
     * @param pictureKey the key the partner saw in what is offered
     * @return the photo
     */
    public RemoteLendingPicture servePicture(ServingPartner partner, String pictureKey) {
        if (!lendingService.isPictureOffered(partner, pictureKey)) {
            throw FederationRefusal.LENDING_PICTURE_NOT_OFFERED.raise();
        }
        MediaContent picture;
        try {
            picture = pictures.readByKey(partner.servingStationId(), pictureKey, TRAVEL_SIDE);
        } catch (RefusalResponse e) {
            throw FederationRefusal.LENDING_PICTURE_NOT_OFFERED.raise();
        }
        if (picture.data().length > MAX_PICTURE_BYTES) throw FederationRefusal.LENDING_PICTURE_NOT_OFFERED.raise();
        return new RemoteLendingPicture(
                picture.contentType(), Base64.getEncoder().encodeToString(picture.data()));
    }

    /**
     * A partner's photo of offered gear for the browser, from this station's copy, fetched once.
     *
     * @param stationId         the asking station
     * @param partnerStationUid the partner station the photo belongs to
     * @param pictureKey        the key that came with the partner's offer
     * @param size              the side wanted, {@code 0} for the copy as kept
     * @return the photo
     */
    public MediaContent federatedPicture(int stationId, UUID partnerStationUid, UUID pictureKey, int size) {
        FederationPartner partner = lendingPartner(stationId, partnerStationUid);
        String copy = stationId + "/" + partnerStationUid + "/" + pictureKey;
        long period = currentPeriod();
        for (long kept : new long[] {period, period - 1}) {
            Optional<MediaContent> found = images.read(PROFILE, SCOPE, CATEGORY, kept + "/" + copy, size);
            if (found.isPresent()) return found.get();
        }
        String key = period + "/" + copy;
        RemoteLendingPicture fetched = transport.get(
                partner, RemoteLendingRoutes.GET_PICTURE.at(pictureKey.toString()), RemoteLendingPicture.class);
        try {
            byte[] data = Base64.getDecoder().decode(fetched.base64());
            images.storeSquare(PROFILE, SCOPE, CATEGORY, key, data, MAX_PICTURE_BYTES, TRAVEL_SIDE);
        } catch (IllegalArgumentException | IOException e) {
            log.warn("A lending photo of partner station {} could not be kept", partnerStationUid, e);
            throw FederationRefusal.LENDING_PICTURE_NOT_OFFERED.raise();
        }
        return images.read(PROFILE, SCOPE, CATEGORY, key, size)
                .orElseThrow(FederationRefusal.LENDING_PICTURE_NOT_OFFERED::raise);
    }

    /** Removes every kept copy older than the current period and the one before it. */
    public void removeExpiredCopies() {
        long oldestKept = currentPeriod() - 1;
        var expired = storage.listKeys(SCOPE, CATEGORY, "").stream()
                .map(key -> key.substring(0, Math.max(key.indexOf('/'), 0)))
                .distinct()
                .filter(period -> isOlderPeriod(period, oldestKept))
                .toList();
        for (String period : expired) {
            storage.deletePrefix(SCOPE, CATEGORY, period + "/");
        }
        if (!expired.isEmpty()) log.info("Removed {} expired period(s) of partner lending photos", expired.size());
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "partner-lending-photo-expiry",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofHours(1)),
                this::removeExpiredCopies));
    }

    private long currentPeriod() {
        return clock.instant().getEpochSecond() / KEEP_FOR.toSeconds();
    }

    private static boolean isOlderPeriod(String period, long oldestKept) {
        try {
            return Long.parseLong(period) < oldestKept;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private FederationPartner lendingPartner(int stationId, UUID partnerStationUid) {
        return federationService.findPartners(stationId).stream()
                .filter(partner -> partnerStationUid.equals(partner.partnerStationId()))
                .filter(partner -> partner.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(partner ->
                        federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.IMPORT))
                .findFirst()
                .orElseThrow(FederationRefusal.LENDING_PICTURE_NOT_OFFERED::raise);
    }
}
