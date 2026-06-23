/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-domain wrapper for account avatars. The on-disk layout is
 * {@code account/<accountUid>/images/avatars/<accountUid>/<variant>.<ext>}; the variant slot
 * carries the size-set produced by {@link ImageVariantService}.
 */
@Singleton
public class AvatarService {
    private final ImageVariantService variants;

    @Inject
    public AvatarService(ImageVariantService variants) {
        this.variants = variants;
    }

    /** Persists the avatar for an account at all standard size variants. */
    public void store(UUID accountUid, byte[] data, String declaredMime, int maxBytes) throws IOException {
        variants.store(scope(accountUid), StorageCategory.IMAGE_AVATAR, key(accountUid), data, declaredMime, maxBytes);
    }

    /** Convenience overload without an upper-bound size check. */
    public void store(UUID accountUid, byte[] data, String declaredMime) throws IOException {
        store(accountUid, data, declaredMime, 0);
    }

    /** Reads the requested avatar size, falling back to the original when missing. */
    public Optional<ImageVariantService.ImageData> read(UUID accountUid, int size) {
        if (accountUid == null) return Optional.empty();
        return variants.read(scope(accountUid), StorageCategory.IMAGE_AVATAR, key(accountUid), size);
    }

    /** Whether an avatar exists for the given account. */
    public boolean exists(UUID accountUid) {
        if (accountUid == null) return false;
        return variants.exists(scope(accountUid), StorageCategory.IMAGE_AVATAR, key(accountUid));
    }

    /** Removes every size variant for the given account's avatar. */
    public void delete(UUID accountUid) {
        if (accountUid == null) return;
        variants.delete(scope(accountUid), StorageCategory.IMAGE_AVATAR, key(accountUid));
    }

    private StorageScope.Account scope(UUID accountUid) {
        return new StorageScope.Account(accountUid);
    }

    private String key(UUID accountUid) {
        return accountUid.toString();
    }
}
