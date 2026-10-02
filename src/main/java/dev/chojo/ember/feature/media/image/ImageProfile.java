/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import dev.chojo.ember.feature.storage.entity.StorageCategory;

import java.util.List;
import java.util.Optional;

/**
 * How one family of pictures is sized, named and capped.
 *
 * <p>The two sized families share their numbers today and are kept apart because they are read for
 * different things: an icon set is drawn small and often (avatars, logos, the pieces of the Ember
 * logo, wiki folder icons), content is looked at (lost and found, quiz questions, wiki images and
 * the pictures of wiki files and member documents). The library takes its widths from the
 * {@code imageVariantsWidths} setting instead, which is why its list here is empty.
 */
public enum ImageProfile {
    /** Small pictures drawn often. */
    ICON_SET(VariantLayout.SIZED, List.of(64, 128, 256, 512, 1024), 2048),
    /** Pictures that are looked at. */
    CONTENT(VariantLayout.SIZED, List.of(64, 128, 256, 512, 1024), 2048),
    /** The media library, including the drawn first page of a document. */
    LIBRARY(VariantLayout.LIBRARY, List.of(), 0);

    private final VariantLayout layout;
    private final List<Integer> sizes;
    private final int maxOriginalSide;

    ImageProfile(VariantLayout layout, List<Integer> sizes, int maxOriginalSide) {
        this.layout = layout;
        this.sizes = sizes;
        this.maxOriginalSide = maxOriginalSide;
    }

    /**
     * The family the pictures of a storage category belong to.
     *
     * <p>Member documents have none: their pictures share the category with the documents themselves,
     * so the category cannot say which of its files is a picture.
     *
     * @param category the storage category
     * @return the family, or empty for a category that holds no picture sets
     */
    public static Optional<ImageProfile> of(StorageCategory category) {
        return switch (category) {
            case IMAGE_AVATAR, IMAGE_STATION_LOGO, IMAGE_LOGO_FRAGMENT, IMAGE_KB_ICON -> Optional.of(ICON_SET);
            case MEDIA_IMAGES, IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_IMAGE, IMAGE_KB_FILE_PICTURE ->
                Optional.of(CONTENT);
            case MEDIA_FILES, INSTANCE_MEDIA_FILES -> Optional.of(LIBRARY);
            default -> Optional.empty();
        };
    }

    /** The layout the family's files are named in. */
    public VariantLayout layout() {
        return layout;
    }

    /** The sizes written for every picture of the family, smallest first; empty for the library. */
    public List<Integer> sizes() {
        return sizes;
    }

    /** The longest side the full picture is scaled down to, or zero where it is kept as it came. */
    public int maxOriginalSide() {
        return maxOriginalSide;
    }
}
