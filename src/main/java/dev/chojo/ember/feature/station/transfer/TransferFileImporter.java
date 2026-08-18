/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.page.service.PageFileStorageService;
import dev.chojo.ember.feature.page.service.PageImageVariantService;
import dev.chojo.ember.feature.station.transfer.StationImportContext.NewAccountRef;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
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
    private final ImageVariantService imageVariantService;
    private final PageFileStorageService pageFileStorageService;
    private final PageImageVariantService pageImageVariantService;

    @Inject
    public TransferFileImporter(
            StorageService storageService,
            AvatarService avatarService,
            ImageVariantService imageVariantService,
            PageFileStorageService pageFileStorageService,
            PageImageVariantService pageImageVariantService) {
        this.storageService = storageService;
        this.avatarService = avatarService;
        this.imageVariantService = imageVariantService;
        this.pageFileStorageService = pageFileStorageService;
        this.pageImageVariantService = pageImageVariantService;
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
     * on the destination's backend. Per-key streaming: the response body is piped straight
     * into {@link StorageService#store}. Keys that already exist on the destination are
     * skipped so a retried import after a partial failure is idempotent (cheap exists check
     * rather than a SHA round-trip).
     *
     * @param client   the source client for this run
     * @param scope    the destination station scope
     * @param category the category to copy
     * @param progress the run progress, updated per key
     */
    public void copyCategory(
            TransferSourceClient client,
            StorageScope.Station scope,
            StorageCategory category,
            ImportProgress progress) {
        int copied = 0;
        int skipped = 0;
        String after = null;
        boolean totalPinned = false;
        while (true) {
            var page = client.listKeys(category, after);
            if (!totalPinned) {
                progress.setSubTotal(
                        page.total() != null ? page.total() : page.keys().size());
                totalPinned = true;
            } else if (page.total() == null) {
                progress.setSubTotal(progress.subTotal() + page.keys().size());
            }
            for (String key : page.keys()) {
                if (storageService.readRelative(scope, category, key).isPresent()) {
                    skipped++;
                } else if (streamFile(client, scope, category, key)) {
                    copied++;
                }
                progress.incrementSub();
            }
            if (page.next() == null) break;
            after = page.next();
        }
        if (copied > 0 || skipped > 0) {
            log.info("Byte-copied {} key(s) for category {} (skipped {} already present)", copied, category, skipped);
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
            TransferSourceClient client, StorageScope.Station scope, StorageCategory category, String key) {
        var file = client.fetchFile(category, key);
        if (file.isEmpty()) return false;
        try {
            store(scope, category, key, file.get().data(), file.get().contentType());
        } catch (IOException e) {
            throw new RuntimeException("Failed to stream key '" + key + "' from remote", e);
        }
        return true;
    }

    /**
     * Routes a byte payload received during transfer to the right destination service. Image
     * categories go through their variant-generating service so the destination rebuilds the
     * resized / WebP set without pulling the duplicates over the wire. Non-image categories
     * fall back to a direct {@link StorageService} write.
     */
    private void store(
            StorageScope.Station scope, StorageCategory category, String relativeKey, byte[] body, String contentType)
            throws IOException {
        switch (category) {
            case PAGE_FILES -> {
                String contentHash = parentOf(relativeKey);
                pageFileStorageService.store(scope.stationId(), contentHash, body, contentType);
                pageImageVariantService.generateVariants(scope.stationId(), contentHash, body, contentType);
            }
            case PAGE_IMAGES, IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE -> {
                String baseKey = parentOf(relativeKey);
                imageVariantService.store(scope, category, baseKey, body, contentType);
            }
            default ->
                storageService.store(
                        scope, category, relativeKey, new ByteArrayInputStream(body), body.length, contentType);
        }
    }
}
