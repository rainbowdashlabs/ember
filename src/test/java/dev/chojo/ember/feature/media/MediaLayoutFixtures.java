/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The picture sets as earlier builds wrote them, checked in under {@code media/layouts} as a data
 * directory, and the loose pictures under {@code media/fixtures} that uploads are made of.
 *
 * <p>The sets are what every stored picture of an installation looks like: an avatar and a lost and
 * found picture in the sized layout ({@code original.<ext>} and {@code <size>.<ext>}, every size
 * written even above the source), a library photo in the width layout with a legacy {@code orig.webp}
 * beside its {@code orig.png}, and a library document with its drawn first page. Copying them into a
 * storage root and reading them through the current services is what proves an old file is still
 * found under the name it has.
 */
public final class MediaLayoutFixtures {
    /** The station every station-scoped set of the fixture belongs to. */
    public static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    /** The account the fixture's avatar belongs to. */
    public static final UUID ACCOUNT_UID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** The lost and found item whose picture is kept as JPEG. */
    public static final String LOST_ITEM_KEY = "7";

    /** The library photo with sizes and a legacy WebP copy of its original. */
    public static final String LIBRARY_PHOTO = "1111111111111111111111111111111111111111111111111111111111111111";

    /** The library document with a drawn first page. */
    public static final String LIBRARY_SHEET = "2222222222222222222222222222222222222222222222222222222222222222";

    private MediaLayoutFixtures() {}

    /**
     * Copies every stored set of the fixture into a storage root, sidecars included.
     *
     * @param root the root a local backend is opened on
     */
    public static void copyInto(Path root) {
        Path source = resource("media/layouts");
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = root.resolve(source.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The bytes of one file of the stored sets, to compare with what a read hands back.
     *
     * @param relative the path below the data directory
     * @return the file's bytes
     */
    public static byte[] stored(String relative) {
        return bytesOf("media/layouts/" + relative);
    }

    /**
     * The bytes of one loose picture an upload is made of.
     *
     * @param name the file name below {@code media/fixtures}
     * @return the picture's bytes
     */
    public static byte[] picture(String name) {
        return bytesOf("media/fixtures/" + name);
    }

    private static byte[] bytesOf(String resource) {
        try (InputStream in = MediaLayoutFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("No fixture " + resource);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path resource(String name) {
        try {
            var url = MediaLayoutFixtures.class.getClassLoader().getResource(name);
            if (url == null) throw new IllegalArgumentException("No fixture " + name);
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
