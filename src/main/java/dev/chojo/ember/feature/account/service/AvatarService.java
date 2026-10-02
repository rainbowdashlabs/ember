/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-domain wrapper for account avatars. The on-disk layout is
 * {@code account/<accountUid>/images/avatars/<accountUid>/<variant>.<ext>}; the variant slot
 * carries the {@link ImageProfile#ICON_SET} written by {@link ImageVariants}.
 */
@Singleton
public class AvatarService {
    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final ImageProfile PROFILE = ImageProfile.ICON_SET;
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_AVATAR;

    private final ImageVariants images;

    @Inject
    public AvatarService(ImageVariants images) {
        this.images = images;
    }

    /**
     * Persists the avatar for an account at all standard size variants.
     */
    public void store(UUID accountUid, byte[] data, String declaredMime, int maxBytes) throws IOException {
        images.store(PROFILE, scope(accountUid), CATEGORY, key(accountUid), data, maxBytes);
        log.info("Avatar stored for account {} ({} bytes, mime={})", accountUid, data.length, declaredMime);
    }

    /**
     * Convenience overload without an upper-bound size check.
     */
    public void store(UUID accountUid, byte[] data, String declaredMime) throws IOException {
        store(accountUid, data, declaredMime, 0);
    }

    /**
     * Reads the requested avatar size, falling back to the original when missing.
     */
    public Optional<MediaContent> read(UUID accountUid, int size) {
        if (accountUid == null) return Optional.empty();
        return images.read(PROFILE, scope(accountUid), CATEGORY, key(accountUid), size);
    }

    /**
     * Whether an avatar exists for the given account.
     */
    public boolean exists(UUID accountUid) {
        if (accountUid == null) return false;
        return images.exists(PROFILE, scope(accountUid), CATEGORY, key(accountUid));
    }

    /**
     * Removes every size variant for the given account's avatar.
     */
    public void delete(UUID accountUid) {
        if (accountUid == null) return;
        images.delete(scope(accountUid), CATEGORY, key(accountUid));
        log.info("Avatar deleted for account {}", accountUid);
    }

    private StorageScope.Account scope(UUID accountUid) {
        return new StorageScope.Account(accountUid);
    }

    private String key(UUID accountUid) {
        return accountUid.toString();
    }
}
