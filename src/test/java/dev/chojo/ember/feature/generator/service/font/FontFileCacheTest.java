/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.FilePaths;
import dev.chojo.ember.util.Sha256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Font files kept on the instance's disk, read from storage once and handed to Typst by the set. */
class FontFileCacheTest {

    private static DocumentFont uploaded(int id, byte[] data) {
        return new DocumentFont(
                id,
                new Owner.Station(1),
                "Wachenschrift",
                FontStyle.REGULAR,
                "wache.ttf",
                FontOutline.TRUETYPE,
                TestFonts.LISU_FAMILY,
                data.length,
                Sha256.hex(data),
                Instant.EPOCH,
                null);
    }

    /** A face is read from storage the first time only, later letters take the kept file. */
    @Test
    void aFaceIsReadOnce(@TempDir Path root) throws IOException {
        var cache = new FontFileCache(root);
        byte[] data = TestFonts.lisu();
        var reads = new AtomicInteger();
        Function<FontFace, Optional<byte[]>> read = face -> {
            reads.incrementAndGet();
            return Optional.of(data);
        };

        var first = cache.fileOf(uploaded(7, data), read).orElseThrow();
        var again = new FontFileCache(root).fileOf(uploaded(7, data), read).orElseThrow();

        assertEquals(first, again);
        assertEquals(1, reads.get());
        assertArrayEquals(data, Files.readAllBytes(first));
    }

    /** A face whose stored file is gone has no kept file either. */
    @Test
    void aGoneFaceHasNoFile(@TempDir Path root) {
        var cache = new FontFileCache(root);

        assertEquals(Optional.empty(), cache.fileOf(uploaded(8, TestFonts.lycian()), face -> Optional.empty()));
    }

    /** A letter's directory holds exactly its files, and the same set is laid out once. */
    @Test
    void aSetOfFilesGetsADirectoryOfItsOwn(@TempDir Path root) throws IOException {
        var cache = new FontFileCache(root);
        var lisu = cache.fileOf(uploaded(1, TestFonts.lisu()), face -> Optional.of(TestFonts.lisu()))
                .orElseThrow();
        var liberation = cache.fileOf(BundledFont.face(), face -> Optional.of(BundledFont.data()))
                .orElseThrow();

        var both = cache.directoryOf(List.of(lisu, liberation));
        var sameAgain = cache.directoryOf(List.of(liberation, lisu));
        var one = cache.directoryOf(List.of(liberation));

        assertEquals(both, sameAgain);
        assertNotEquals(both, one);
        var expected =
                Stream.of(liberation, lisu).map(FilePaths::nameOf).sorted().toList();
        assertEquals(expected, namesIn(both));
        assertEquals(List.of(FilePaths.nameOf(liberation)), namesIn(one));
    }

    private static List<String> namesIn(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(FilePaths::nameOf).sorted().toList();
        }
    }
}
