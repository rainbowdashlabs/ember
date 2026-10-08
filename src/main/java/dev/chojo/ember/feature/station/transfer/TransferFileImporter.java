/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.station.transfer.StationImportContext.NewAccountRef;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies the file side of a station transfer: the stored objects of every movable station-scoped
 * category, and the avatars of the accounts the import created.
 */
@Singleton
public class TransferFileImporter {
    private static final Logger log = LoggerFactory.getLogger(TransferFileImporter.class);
    private final StorageService storageService;
    private final AvatarService avatarService;
    private final ImageVariants images;
    private final MediaStorageService mediaStorageService;

    @Inject
    public TransferFileImporter(
            StorageService storageService,
            AvatarService avatarService,
            ImageVariants images,
            MediaStorageService mediaStorageService) {
        this.storageService = storageService;
        this.avatarService = avatarService;
        this.images = images;
        this.mediaStorageService = mediaStorageService;
    }

    /**
     * @return every station-scoped storage category whose objects travel with a station transfer
     */
    public static List<StorageCategory> transferrableStationCategories() {
        var out = new ArrayList<StorageCategory>();
        for (StorageCategory c : StorageCategory.values()) {
            if (c.scopeKind() != StorageScope.Kind.STATION) continue;
            if (!c.isMovable()) continue;
            out.add(c);
        }
        return out;
    }

    /**
     * Returns the slash-separated parent segment of {@code relativeKey} - for an original key
     * like {@code <hash>/orig.png} this is the {@code <hash>}; for a flat key with no slash
     * the input is returned as-is.
     */
    private static String parentOf(String relativeKey) {
        int slash = relativeKey.lastIndexOf('/');
        return slash < 0 ? relativeKey : relativeKey.substring(0, slash);
    }

    /**
     * Pulls every key in one station-scoped movable category from the source and stores it
     * on the destination's backend, under the key {@link TransferFileKeys} gives it there.
     * Per-key streaming: the response body is piped straight into {@link StorageService#store}.
     * Keys that already exist on the destination are skipped so a retried import after a partial
     * failure is idempotent (cheap exists check rather than a SHA round-trip), and so are keys
     * whose row did not arrive.
     *
     * @param client   the source client for this run
     * @param scope    the destination station scope
     * @param category the category to copy
     * @param idMap    the source-to-destination ids of the imported rows
     * @param progress the run progress, updated per key
     */
    public void copyCategory(
            TransferSourceClient client,
            StorageScope.Station scope,
            StorageCategory category,
            IdRemapper idMap,
            ImportProgress progress) {
        int copied = 0;
        int skipped = 0;
        String after = null;
        boolean totalPinned = false;
        while (true) {
            var page = client.listKeys(category, after);
            Integer total = page.total();
            if (!totalPinned) {
                progress.setSubTotal(total != null ? total : page.keys().size());
                totalPinned = true;
            } else if (total == null) {
                progress.setSubTotal(progress.subTotal() + page.keys().size());
            }
            for (String key : page.keys()) {
                var target = TransferFileKeys.destinationKey(category, key, idMap);
                if (target.isEmpty() || storageService.existsRelative(scope, category, target.get())) {
                    skipped++;
                } else if (streamFile(client, scope, category, key, target.get())) {
                    copied++;
                }
                progress.incrementSub();
            }
            if (page.next() == null) break;
            after = page.next();
        }
        if (copied > 0 || skipped > 0) {
            log.info(
                    "Byte-copied {} key(s) for category {} (skipped {} already present or without their row)",
                    copied,
                    category,
                    skipped);
        }
    }

    /**
     * Streams the avatar for every account this import created on the destination. Existing
     * accounts on the destination are skipped; we never overwrite an avatar a user has already
     * uploaded locally. Missing avatars on the source are normal.
     *
     * @param client      the source client for this run
     * @param newAccounts the accounts this run created
     * @param progress    the run progress, updated per account
     */
    public void copyNewAccountAvatars(
            TransferSourceClient client, List<NewAccountRef> newAccounts, ImportProgress progress) {
        if (newAccounts.isEmpty()) {
            log.info("avatar carry-over: no newly-created accounts, skipping");
            return;
        }
        progress.setSubTotal(newAccounts.size());
        log.info("avatar carry-over: {} newly-created account(s) to fetch", newAccounts.size());
        int copied = 0;
        int skipped = 0;
        for (NewAccountRef ref : newAccounts) {
            var avatar = client.fetchAvatar(ref.sourceUid());
            if (avatar.isEmpty()) {
                skipped++;
            } else {
                try {
                    avatarService.store(
                            ref.destinationUid(),
                            avatar.get().data(),
                            avatar.get().contentType());
                    copied++;
                } catch (Exception e) {
                    log.warn("Failed to import avatar for source account {}", ref.sourceUid(), e);
                    skipped++;
                }
            }
            progress.incrementSub();
        }
        log.info("Avatar carry-over: imported {} (skipped {})", copied, skipped);
    }

    /**
     * Returns {@code true} when the key was streamed successfully; {@code false} when the source
     * answered 404 (the row was deleted concurrently - acceptable, the row will likely be
     * re-listed in a later transfer or stay absent).
     */
    private boolean streamFile(
            TransferSourceClient client,
            StorageScope.Station scope,
            StorageCategory category,
            String sourceKey,
            String targetKey) {
        var file = client.fetchFile(category, sourceKey);
        if (file.isEmpty()) return false;
        try {
            store(scope, category, targetKey, file.get().data(), file.get().contentType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to stream key '" + sourceKey + "' from remote", e);
        }
        return true;
    }

    /**
     * Routes a byte payload received during transfer to the right destination service. A category
     * with an {@link ImageProfile} goes through {@link ImageVariants} so the destination rebuilds
     * the sizes without pulling them over the wire; the library keeps its original itself first.
     * Every other category is a direct {@link StorageService} write.
     */
    private void store(
            StorageScope.Station scope, StorageCategory category, String relativeKey, byte[] body, String contentType)
            throws IOException {
        var profile = ImageProfile.of(category);
        if (profile.isEmpty()) {
            storageService.store(
                    scope, category, relativeKey, new ByteArrayInputStream(body), body.length, contentType);
            return;
        }
        String setKey = parentOf(relativeKey);
        if (profile.get() == ImageProfile.LIBRARY) {
            mediaStorageService.store(scope.stationId(), setKey, body, contentType);
            images.addSizes(scope, category, setKey, body, contentType);
            return;
        }
        images.store(profile.get(), scope, category, setKey, body, 0);
    }
}
