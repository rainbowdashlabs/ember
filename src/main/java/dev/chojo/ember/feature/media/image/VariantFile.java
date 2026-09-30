/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import java.util.Optional;

/**
 * One stored file of a picture set, split into the parts its name is read by.
 *
 * <p>{@code w256.webp} is the base {@code w256} with the extension {@code webp}; a name without a dot
 * has an empty extension. Whatever directories the key it came from carried are dropped, since a set
 * lives in one directory and only the last segment names the file.
 *
 * @param fileName  the last segment of the key
 * @param base      the file name before its last dot
 * @param extension the file name after its last dot, lower or upper case as stored
 */
public record VariantFile(String fileName, String base, String extension) {

    /**
     * Splits a storage key into the name of the file it ends in.
     *
     * @param key a key relative to anything, such as {@code <hash>/w256.webp}
     * @return the file it names
     */
    public static VariantFile of(String key) {
        int slash = key.lastIndexOf('/');
        String fileName = slash < 0 ? key : key.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String extension = dot < 0 ? "" : fileName.substring(dot + 1);
        return new VariantFile(fileName, base, extension);
    }

    /** The picture format the extension names, empty for a file that is no picture. */
    public Optional<ImageFormat> format() {
        return ImageFormat.ofExtension(extension);
    }
}
