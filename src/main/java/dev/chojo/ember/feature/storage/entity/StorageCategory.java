/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import java.util.List;

/**
 * Single source of truth for everything Ember stores on disk: scope kind, backend
 * resolvability, quota behaviour, accepted MIME types and per-category flags.
 *
 * <p>Entries are split conceptually into:
 * <ul>
 *   <li><b>Movable</b> - {@link #isLocalPinned()} returns {@code false}; the
 *       {@link dev.chojo.ember.feature.storage.backend.StorageBackendResolver resolver}
 *       may bind the category to any remote backend.</li>
 *   <li><b>Local-pinned</b> - {@link #isLocalPinned()} returns {@code true}; resolver always
 *       short-circuits to the local backend regardless of instance / station override.</li>
 * </ul>
 *
 * <p>{@code MIME_ANY} is the sentinel that disables MIME validation for categories that
 * accept arbitrary uploads (page files, KB files, board attachments).
 */
public enum StorageCategory {
    PAGE_FILES("page-files", StorageScope.Kind.STATION, true, QuotaMode.ENFORCED, MimeLists.ANY, false, false, null),
    PAGE_IMAGES(
            "page-images",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    KB_FILES("kb-files", StorageScope.Kind.STATION, true, QuotaMode.ENFORCED, MimeLists.ANY, false, false, null),
    BOARD_ATTACHMENTS(
            "attachments/board",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.ANY,
            false,
            false,
            null),
    IMAGE_AVATAR(
            "images/avatars",
            StorageScope.Kind.ACCOUNT,
            true,
            QuotaMode.TRACKED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_LOST_AND_FOUND(
            "images/lost-and-found",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_LOGO_FRAGMENT(
            "images/logo-fragments",
            StorageScope.Kind.INSTANCE,
            true,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_STATION_LOGO(
            "images/logos",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_QUIZ_QUESTION(
            "images/quiz-questions",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_KB_ICON(
            "images/kb-icons",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    IMAGE_KB_IMAGE(
            "images/kb-images",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("image/png", "image/jpeg", "image/webp", "image/gif"),
            false,
            false,
            null),
    DOCUMENT(
            "documents",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("text/markdown"),
            false,
            false,
            null),
    DISCOVERY_KEY(
            "discovery",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("application/octet-stream"),
            false,
            false,
            "0600"),
    MAP_TILE_CACHE(
            "maps/tile-cache",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/jpeg"),
            false,
            true,
            null),
    DEMO_AVATAR(
            "demo-avatars",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/svg+xml"),
            false,
            false,
            null);

    /**
     * Sentinel value used in place of an actual MIME list to mark categories that accept any
     * media type. {@link #acceptsMimeType(String)} short-circuits to {@code true} on this
     * sentinel; callers iterating over the list should treat it as "no restriction".
     */
    public static final List<String> MIME_ANY = MimeLists.ANY;

    private final String prefix;
    private final StorageScope.Kind scopeKind;
    private final boolean movable;
    private final QuotaMode quotaMode;
    private final List<String> acceptedMimeTypes;
    private final boolean imageSet;
    private final boolean accessTimeLru;
    private final String posixMode;

    StorageCategory(
            String prefix,
            StorageScope.Kind scopeKind,
            boolean movable,
            QuotaMode quotaMode,
            List<String> acceptedMimeTypes,
            boolean imageSet,
            boolean accessTimeLru,
            String posixMode) {
        this.prefix = prefix;
        this.scopeKind = scopeKind;
        this.movable = movable;
        this.quotaMode = quotaMode;
        this.acceptedMimeTypes = acceptedMimeTypes;
        this.imageSet = imageSet;
        this.accessTimeLru = accessTimeLru;
        this.posixMode = posixMode;
    }

    /**
     * Returns the path prefix this category contributes (without leading or trailing slash).
     * E.g. {@code "page-files"} for {@link #PAGE_FILES}.
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns the scope discriminator this category expects. Mismatches between this and a
     * passed-in {@link StorageScope} are rejected by {@code StorageService} at runtime.
     */
    public StorageScope.Kind scopeKind() {
        return scopeKind;
    }

    /**
     * Whether the resolver may bind this category to a non-local backend. {@code true} for
     * movable categories (page files, attachments, …); {@code false} for local-pinned
     * categories (documents, discovery key, tile cache, demo avatars).
     */
    public boolean isMovable() {
        return movable;
    }

    /**
     * Inverse of {@link #isMovable()}.
     */
    public boolean isLocalPinned() {
        return !movable;
    }

    /**
     * Quota tracking mode - {@code ENFORCED}, {@code TRACKED}, or {@code UNTRACKED}.
     */
    public QuotaMode quotaMode() {
        return quotaMode;
    }

    /**
     * Whether bytes in this category count against and are checked against a station quota.
     */
    public boolean enforcesQuota() {
        return quotaMode == QuotaMode.ENFORCED;
    }

    /**
     * Whether bytes in this category should be tracked in {@code storage_usage}.
     */
    public boolean tracksUsage() {
        return quotaMode != QuotaMode.UNTRACKED;
    }

    /**
     * Immutable list of accepted MIME types. {@link #MIME_ANY} marks "no restriction".
     */
    public List<String> acceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    /**
     * Whether the supplied MIME type is allowed for this category. Returns {@code true} for
     * {@link #MIME_ANY}; otherwise matches case-insensitively against the configured list.
     */
    public boolean acceptsMimeType(String mimeType) {
        if (acceptedMimeTypes == MIME_ANY) return true;
        if (mimeType == null) return false;
        String normalized = mimeType.toLowerCase().trim();
        for (String accepted : acceptedMimeTypes) {
            if (accepted.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /**
     * Whether the producer emits the standard image variant set
     * ({@code original, 1024, 512, 256, 128}) for uploads in this category.
     */
    public boolean isImageSet() {
        return imageSet;
    }

    /**
     * Whether the category requires access-time tracking. Only categories with this flag may
     * resolve to backends that declare {@code BackendCapability.ACCESS_TIME_TRACKING}.
     */
    public boolean isAccessTimeLru() {
        return accessTimeLru;
    }

    /**
     * Optional POSIX mode (e.g. {@code "0600"}) that {@link
     * dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend LocalStorageBackend} applies
     * after write. {@code null} means inherit the process umask. Remote backends ignore the
     * flag; the resolver guarantees POSIX-mode categories are local-pinned.
     */
    public String posixMode() {
        return posixMode;
    }

    /**
     * Quota tracking mode.
     */
    public enum QuotaMode {
        /**
         * Counted in {@code storage_usage} and rejected past the per-station limit.
         */
        ENFORCED,
        /**
         * Counted in {@code storage_usage} but never rejected.
         */
        TRACKED,
        /**
         * Not counted at all.
         */
        UNTRACKED
    }

    /**
     * Nested holder so the {@code MIME_ANY} sentinel can be referenced from enum-literal
     * initializers without triggering Java's "illegal forward reference" check on
     * {@code static final} fields declared after the literals.
     */
    private static final class MimeLists {
        private static final List<String> ANY = List.of("*/*");
    }
}
