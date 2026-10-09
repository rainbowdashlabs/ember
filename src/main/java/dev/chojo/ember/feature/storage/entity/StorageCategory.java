/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Everything Ember stores, with its scope kind, quota behaviour, accepted MIME types and flags. A
 * movable category goes to whichever backend the instance or station chose; a local-pinned one always
 * stays on the local backend.
 */
public enum StorageCategory {
    MEDIA_FILES(
            "media/files", StorageScope.Kind.STATION, true, QuotaMode.ENFORCED, MimeLists.ANY, false, Optional.empty()),
    /**
     * The library the instance holds, which every station is served from. Untracked because a
     * quota is a limit on what one station may keep, and this belongs to the instance running them.
     */
    INSTANCE_MEDIA_FILES(
            "media/files",
            StorageScope.Kind.INSTANCE,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.ANY,
            false,
            Optional.empty()),
    MEDIA_IMAGES(
            "media/images",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    KB_FILES("kb-files", StorageScope.Kind.STATION, true, QuotaMode.ENFORCED, MimeLists.ANY, false, Optional.empty()),
    MEMBER_DOCUMENTS(
            "member-documents",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.ANY,
            false,
            Optional.empty()),
    /**
     * The PDFs a station's PDF templates fill in, every uploaded version kept as it arrived. Measured
     * against the same room as the member documents they become.
     */
    DOCUMENT_TEMPLATES(
            "document-templates",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            List.of("application/pdf"),
            false,
            Optional.empty()),
    /**
     * The PDFs an association's PDF templates fill in, kept and counted in its home station.
     */
    ASSOCIATION_DOCUMENT_TEMPLATES(
            "document-templates",
            StorageScope.Kind.ASSOCIATION,
            true,
            QuotaMode.ENFORCED,
            List.of("application/pdf"),
            false,
            Optional.empty()),
    /**
     * The fonts a station uploaded for its documents. Measured against the same room as the documents
     * they print.
     */
    FONTS("fonts", StorageScope.Kind.STATION, true, QuotaMode.ENFORCED, MimeLists.FONTS, false, Optional.empty()),
    /**
     * The fonts an association uploaded for its own documents and those of its stations, kept and
     * counted in its home station.
     */
    ASSOCIATION_FONTS(
            "fonts", StorageScope.Kind.ASSOCIATION, true, QuotaMode.ENFORCED, MimeLists.FONTS, false, Optional.empty()),
    /**
     * The fonts the instance offers every station. Untracked, like the instance's media library.
     */
    INSTANCE_FONTS(
            "fonts", StorageScope.Kind.INSTANCE, true, QuotaMode.UNTRACKED, MimeLists.FONTS, false, Optional.empty()),
    /**
     * Evidence attached to one movement, kept by the station that raised it and read by the owner it went to.
     */
    MOVEMENT_DOCUMENTS(
            "movement-documents",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.ANY,
            false,
            Optional.empty()),
    BOARD_ATTACHMENTS(
            "attachments/board",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.ANY,
            false,
            Optional.empty()),
    IMAGE_AVATAR(
            "images/avatars",
            StorageScope.Kind.ACCOUNT,
            true,
            QuotaMode.TRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    /**
     * A person's own signature picture, one transparent PNG per account, drawn into the documents they
     * sign. A few kilobytes that follow the person to every station, so no station is charged for it.
     */
    IMAGE_SIGNATURE(
            "images/signatures",
            StorageScope.Kind.ACCOUNT,
            true,
            QuotaMode.UNTRACKED,
            List.of("image/png"),
            false,
            Optional.empty()),
    IMAGE_LOST_AND_FOUND(
            "images/lost-and-found",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    IMAGE_LOGO_FRAGMENT(
            "images/logo-fragments",
            StorageScope.Kind.INSTANCE,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    IMAGE_STATION_LOGO(
            "images/logos",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    IMAGE_QUIZ_QUESTION(
            "images/quiz-questions",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    IMAGE_KB_ICON(
            "images/kb-icons",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    IMAGE_KB_IMAGE(
            "images/kb-images",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.ENFORCED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    /**
     * The picture of a wiki file: the image scaled down, or a document's first page. Derived from
     * the file and made again whenever it is missing, so it is not charged to the station, which
     * already pays for the file it was made from.
     */
    IMAGE_KB_FILE_PICTURE(
            "images/kb-file-pictures",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    /**
     * The picture of a station's document template: its first page drawn without a member, kept per
     * version. Derived from the template and made again whenever it is missing, so it is not charged
     * to the station.
     */
    IMAGE_DOCUMENT_TEMPLATE_PICTURE(
            "images/document-template-pictures",
            StorageScope.Kind.STATION,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    /**
     * The picture of an association's document template, the same as a station's, kept with the
     * association.
     */
    IMAGE_ASSOCIATION_DOCUMENT_TEMPLATE_PICTURE(
            "images/document-template-pictures",
            StorageScope.Kind.ASSOCIATION,
            true,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty()),
    DOCUMENT(
            "documents",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("text/markdown"),
            false,
            Optional.empty()),
    DISCOVERY_KEY(
            "discovery",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("application/octet-stream"),
            false,
            Optional.of("0600")),
    MAP_TILE_CACHE(
            "maps/tile-cache",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/jpeg"),
            true,
            Optional.empty()),
    DEMO_AVATAR(
            "demo-avatars",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            List.of("image/png", "image/svg+xml"),
            false,
            Optional.empty()),
    /**
     * The copies this instance keeps of the logos other instances publish for their stations, drawn
     * again here so the discovery page never sends a visitor's browser to another instance. A cache
     * that is fetched again when lost, so it stays on the local backend and is counted nowhere.
     */
    IMAGE_DISCOVERY_LOGO(
            "images/discovery-logos",
            StorageScope.Kind.INSTANCE,
            false,
            QuotaMode.UNTRACKED,
            MimeLists.IMAGES,
            false,
            Optional.empty());

    /** Stands in for the MIME list of a category that accepts any type. */
    public static final List<String> MIME_ANY = MimeLists.ANY;

    private final String prefix;
    private final StorageScope.Kind scopeKind;
    private final boolean movable;
    private final QuotaMode quotaMode;
    private final List<String> acceptedMimeTypes;
    private final boolean accessTimeLru;
    private final Optional<String> posixMode;

    StorageCategory(
            String prefix,
            StorageScope.Kind scopeKind,
            boolean movable,
            QuotaMode quotaMode,
            List<String> acceptedMimeTypes,
            boolean accessTimeLru,
            Optional<String> posixMode) {
        this.prefix = prefix;
        this.scopeKind = scopeKind;
        this.movable = movable;
        this.quotaMode = quotaMode;
        this.acceptedMimeTypes = acceptedMimeTypes;
        this.accessTimeLru = accessTimeLru;
        this.posixMode = posixMode;
    }

    /** The path segment of the category, such as {@code media/files}, without slashes at either end. */
    public String prefix() {
        return prefix;
    }

    public StorageScope.Kind scopeKind() {
        return scopeKind;
    }

    public boolean isMovable() {
        return movable;
    }

    public boolean isLocalPinned() {
        return !movable;
    }

    public QuotaMode quotaMode() {
        return quotaMode;
    }

    public boolean enforcesQuota() {
        return quotaMode == QuotaMode.ENFORCED;
    }

    public boolean tracksUsage() {
        return quotaMode != QuotaMode.UNTRACKED;
    }

    public List<String> acceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    /** Whether a MIME type is accepted, compared without case; any is for {@link #MIME_ANY}. */
    public boolean acceptsMimeType(@Nullable String mimeType) {
        if (acceptedMimeTypes == MIME_ANY) return true;
        if (mimeType == null) return false;
        String normalized = mimeType.toLowerCase().trim();
        for (String accepted : acceptedMimeTypes) {
            if (accepted.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    /** Whether reads record an access time, by which the category's least recently used entries go. */
    public boolean isAccessTimeLru() {
        return accessTimeLru;
    }

    /**
     * The octal POSIX mode such as {@code "0600"} the local backend applies after a write; empty keeps
     * the process umask. Only local-pinned categories carry one.
     */
    public Optional<String> posixMode() {
        return posixMode;
    }

    public enum QuotaMode {
        /** Counted in the usage and refused past the station's limit. */
        ENFORCED,
        /** Counted in the usage but never refused. */
        TRACKED,
        /** Not counted at all. */
        UNTRACKED
    }

    /** Holds the shared lists, which the enum constants cannot reference as fields declared after them. */
    private static final class MimeLists {
        private static final List<String> ANY = List.of("*/*");
        private static final List<String> IMAGES = List.of("image/png", "image/jpeg", "image/webp", "image/gif");
        private static final List<String> FONTS = List.of("font/ttf", "font/otf", "font/woff2", "font/woff");
    }
}
