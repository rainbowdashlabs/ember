/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The files one picture has stored, read in one layout, and the one rule for which of them answers a
 * request.
 *
 * <p>The rule: the smallest stored size at or above the size asked for, in a format the reader takes,
 * and the full picture when no size is asked for or none is large enough. A size is never scaled up
 * from a smaller one, and a request above every size gets the full picture rather than the largest
 * size, which is the sharper answer.
 */
public final class VariantSet {
    private final List<VariantFile> originals;
    private final NavigableMap<Integer, List<VariantFile>> sizes;

    private VariantSet(List<VariantFile> originals, NavigableMap<Integer, List<VariantFile>> sizes) {
        this.originals = originals;
        this.sizes = sizes;
    }

    /**
     * Reads a listing of one picture's directory in a layout. Files that are neither the full picture
     * nor a size of this layout are left out, which is what lets two layouts share a directory.
     *
     * @param layout the layout the names are read in
     * @param keys   the stored keys, relative to anything
     * @return the set
     */
    public static VariantSet of(VariantLayout layout, Collection<String> keys) {
        var originals = new ArrayList<VariantFile>();
        var sizes = new TreeMap<Integer, List<VariantFile>>();
        for (String key : keys) {
            VariantFile file = VariantFile.of(key);
            if (layout.isOriginal(file)) {
                originals.add(file);
                continue;
            }
            layout.sizeOf(file).ifPresent(size -> sizes.computeIfAbsent(size, s -> new ArrayList<>())
                    .add(file));
        }
        return new VariantSet(List.copyOf(originals), sizes);
    }

    /** Whether nothing of the picture is stored. */
    public boolean isEmpty() {
        return originals.isEmpty() && sizes.isEmpty();
    }

    /**
     * The full picture as it was taken. Where an older build left a WebP copy beside it, the copy is
     * passed over, since the reader asked for the file itself.
     *
     * @return the full picture, or empty when none is stored
     */
    public Optional<VariantFile> original() {
        return originals.stream()
                .filter(file -> file.format().filter(ImageFormat.WEBP::equals).isEmpty())
                .findFirst()
                .or(() -> originals.stream().findFirst());
    }

    /**
     * The full picture in one format, for a caller that wants a copy of it rather than the file.
     *
     * @param format the format wanted
     * @return the full picture in that format, or empty when none is stored in it
     */
    public Optional<VariantFile> original(ImageFormat format) {
        return originals.stream()
                .filter(file -> file.format().filter(format::equals).isPresent())
                .findFirst();
    }

    /**
     * The smallest stored size at or above the one asked for.
     *
     * @param requested the size asked for; zero or less asks for none
     * @param accepted  the formats the reader takes
     * @return the size, or empty when none is asked for or none is large enough
     */
    public Optional<VariantFile> size(int requested, AcceptedFormats accepted) {
        if (requested <= 0) return Optional.empty();
        for (Map.Entry<Integer, List<VariantFile>> entry :
                sizes.tailMap(requested, true).entrySet()) {
            var taken = entry.getValue().stream().filter(accepted::accepts).findFirst();
            if (taken.isPresent()) return taken;
        }
        return Optional.empty();
    }

    /**
     * The file that answers a request: the size the rule picks, else the full picture.
     *
     * @param requested the size asked for; zero or less asks for the full picture
     * @param accepted  the formats the reader takes
     * @return the file, or empty when nothing is stored
     */
    public Optional<VariantFile> choose(int requested, AcceptedFormats accepted) {
        return size(requested, accepted).or(this::original);
    }
}
