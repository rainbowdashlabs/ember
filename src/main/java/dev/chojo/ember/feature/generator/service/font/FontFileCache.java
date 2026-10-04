/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.BuiltInFace;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.util.FilePaths;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The font files letters are printed with, kept on the instance's own disk, so a letter neither reads
 * every face from storage nor writes it out anew.
 *
 * <p>Every file is named by the SHA-256 of what it holds, so a name always stands for the same bytes: a
 * file once written is never written again, and one of an older release or an earlier upload can never
 * be mistaken for another. A letter is handed a directory holding exactly the files it prints with, so
 * Typst finds no family of another owner there; a directory is named by the files it holds and made once
 * for each such set, its files linked to the kept ones where the disk allows it and copied where not.
 * Files and directories appear whole or not at all, so letters drawn at once never see one half
 * written.
 */
@Singleton
public class FontFileCache {
    // TODO: remove the kept files and directories of fonts that were deleted
    private static final Logger log = LoggerFactory.getLogger(FontFileCache.class);

    /** Where the files are kept below the working directory of the instance. */
    public static final Path DEFAULT_ROOT = Path.of("data", "font-cache");

    private final Path files;
    private final Path sets;
    private final Map<String, String> builtInNames = new ConcurrentHashMap<>();

    @Inject
    public FontFileCache() {
        this(DEFAULT_ROOT);
    }

    /**
     * @param root the directory the files are kept in, which a test chooses
     */
    public FontFileCache(Path root) {
        this.files = root.resolve("files").toAbsolutePath();
        this.sets = root.resolve("sets").toAbsolutePath();
    }

    /**
     * The kept file of a face, written from what {@code read} gives where it is not kept yet.
     *
     * @param face the face
     * @param read reads the file of a face, empty where it is gone or Typst carries the face itself
     * @return the file, or empty where there is none to keep
     */
    public Optional<Path> fileOf(FontFace face, Function<FontFace, Optional<byte[]>> read) {
        if (face instanceof DocumentFont font) {
            Path kept = files.resolve(font.sha256() + extensionOf(face));
            if (Files.isRegularFile(kept)) return Optional.of(kept);
        }
        return read.apply(face).map(data -> keep(nameOf(face, data), data));
    }

    /**
     * A directory holding exactly the given kept files, made the first time this set is asked for.
     *
     * @param kept files this cache keeps
     * @return the directory
     */
    public Path directoryOf(Collection<Path> kept) {
        var names = kept.stream().map(FilePaths::nameOf).distinct().sorted().toList();
        Path directory = sets.resolve(Sha256.hex(String.join("\n", names)));
        if (Files.isDirectory(directory)) return directory;
        try {
            Files.createDirectories(sets);
            Path building = Files.createDirectory(sets.resolve(".building-" + UUID.randomUUID()));
            for (String name : names) link(building.resolve(name), files.resolve(name));
            moveInPlace(building, directory);
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException("The fonts of a letter could not be laid out in " + directory, e);
        }
    }

    private String nameOf(FontFace face, byte[] data) {
        return switch (face) {
            case DocumentFont font -> font.sha256() + extensionOf(face);
            case BuiltInFace builtIn ->
                builtInNames.computeIfAbsent(builtIn.identity(), ignored -> Sha256.hex(data) + extensionOf(face));
        };
    }

    private static String extensionOf(FontFace face) {
        return face.outline() == FontOutline.CFF ? ".otf" : ".ttf";
    }

    private Path keep(String name, byte[] data) {
        Path kept = files.resolve(name);
        if (Files.isRegularFile(kept)) return kept;
        try {
            Files.createDirectories(files);
            Path writing = files.resolve(".writing-" + UUID.randomUUID());
            Files.write(writing, data);
            moveInPlace(writing, kept);
            return kept;
        } catch (IOException e) {
            throw new UncheckedIOException("The font file " + kept + " could not be kept", e);
        }
    }

    private static void link(Path link, Path existing) throws IOException {
        try {
            Files.createLink(link, existing);
        } catch (IOException | UnsupportedOperationException notLinkable) {
            Files.copy(existing, link);
        }
    }

    /**
     * Moves what was built aside to its name in one step; where another letter put it there first, that
     * one stays and what was built is thrown away.
     */
    private static void moveInPlace(Path built, Path target) throws IOException {
        try {
            Files.move(built, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException alreadyThere) {
            delete(built);
        } catch (IOException e) {
            if (!Files.exists(target)) throw e;
            delete(built);
        }
    }

    private static void delete(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(each);
            }
        } catch (IOException e) {
            log.warn("{} could not be removed", path, e);
        }
    }
}
