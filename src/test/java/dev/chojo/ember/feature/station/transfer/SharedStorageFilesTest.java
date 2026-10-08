/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageContainers;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.backend.s3.S3BackendConfig;
import dev.chojo.ember.feature.storage.backend.s3.S3StorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Files copied inside storage the destination took over: each lands under its row's new id although the
 * ids of both sides overlap, a retry copies what the source held whatever ids it hands out, and the source
 * gets its files back when the import fails. Run against the local disk and an S3 server.
 */
@Tag("storage")
class SharedStorageFilesTest {
    private static final StorageCategory WIKI = StorageCategory.KB_FILES;
    private static final StorageCategory MEDIA = StorageCategory.MEDIA_FILES;
    private static final Map<Integer, String> SOURCE = Map.of(1, "one", 2, "two", 3, "three");

    private static LocalStorageBackend local;
    private static S3StorageBackend s3;

    @AfterAll
    static void closeBackends() {
        if (s3 != null) s3.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void overlappingIdsLandUnderTheirNewIds(String kind) {
        var fixture = new Fixture(backend(kind));

        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 3, 3, 1), fixture.progress());

        assertEquals(Map.of(1, "three", 2, "one", 3, "two"), fixture.wikiFiles());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void aRetryCopiesWhatTheSourceHeldWhateverIdsItHandsOut(String kind) {
        var fixture = new Fixture(backend(kind));
        fixture.failOnCopy(fixture.wikiBase(), 2);

        assertThrows(StorageException.class, () -> fixture.files()
                .copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 3, 3, 1), fixture.progress()));
        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 3, 2, 1, 3, 2), fixture.progress());
        fixture.files().release(fixture.run());

        assertEquals(Map.of(1, "two", 2, "three", 3, "one"), fixture.wikiFiles());
        assertEquals(List.of(), fixture.staged());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void anInterruptedStagingIsStagedAgain(String kind) {
        var fixture = new Fixture(backend(kind));
        fixture.failOnCopy("transfer/", 2);

        assertThrows(StorageException.class, () -> fixture.files()
                .copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 3, 3, 1), fixture.progress()));
        assertEquals(Map.of(1, "one", 2, "two", 3, "three"), fixture.wikiFiles());
        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 3, 3, 1), fixture.progress());

        assertEquals(Map.of(1, "three", 2, "one", 3, "two"), fixture.wikiFiles());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void aFailedImportGivesTheSourceItsFilesBack(String kind) {
        var fixture = new Fixture(backend(kind));
        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 4, 3, 1), fixture.progress());

        fixture.files().restore(fixture.run());

        assertEquals(SOURCE, fixture.wikiFiles());
        assertEquals(List.of(), fixture.staged());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void filesWhoseRowDidNotArriveStayAsTheyWere(String kind) {
        var fixture = new Fixture(backend(kind));

        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 5), fixture.progress());
        fixture.files().release(fixture.run());

        assertEquals(Map.of(1, "one", 2, "two", 3, "three", 5, "one"), fixture.wikiFiles());
        assertEquals(List.of(), fixture.staged());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void aCategoryNotNamedByRowsStaysInPlace(String kind) {
        var fixture = new Fixture(backend(kind));
        fixture.store(fixture.sourceBase(MEDIA) + "/abc/orig.png", "picture");

        fixture.files().copyCategory(fixture.run(), MEDIA, renumbered(), fixture.progress());

        assertEquals(
                List.of(fixture.sourceBase(MEDIA) + "/abc/orig.png"),
                fixture.backend().listByPrefix(fixture.sourceBase(MEDIA) + "/"));
        assertEquals(List.of(), fixture.staged());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "s3"})
    void aStationUnderAnotherIdentifierGetsCopiesBesideTheSource(String kind) {
        var fixture = new Fixture(backend(kind), false);
        fixture.store(fixture.sourceBase(MEDIA) + "/abc/orig.png", "picture");

        fixture.files().copyCategory(fixture.run(), WIKI, renumbered(1, 2, 2, 3, 3, 1), fixture.progress());
        fixture.files().copyCategory(fixture.run(), MEDIA, renumbered(), fixture.progress());

        assertEquals(SOURCE, fixture.wikiFiles());
        var destination = fixture.run().destination().prefix();
        assertEquals("three", fixture.read(destination + "/" + WIKI.prefix() + "/1/content"));
        assertEquals("one", fixture.read(destination + "/" + WIKI.prefix() + "/2/content"));
        assertEquals("two", fixture.read(destination + "/" + WIKI.prefix() + "/3/content"));
        assertEquals("picture", fixture.read(destination + "/" + MEDIA.prefix() + "/abc/orig.png"));
        assertFalse(fixture.backend().exists(fixture.run().marker(WIKI)));
    }

    private static StorageBackend backend(String kind) {
        return switch (kind) {
            case "local" -> local();
            case "s3" -> s3();
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static synchronized StorageBackend local() {
        if (local == null) {
            try {
                local = new LocalStorageBackend(Files.createTempDirectory("ember-shared-storage"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return local;
    }

    private static synchronized StorageBackend s3() {
        if (s3 == null) {
            StorageContainers.s3();
            s3 = new S3StorageBackend(new S3BackendConfig(
                    StorageContainers.s3Endpoint(),
                    StorageContainers.REGION,
                    StorageContainers.BUCKET,
                    StorageContainers.USER,
                    StorageContainers.PASSWORD,
                    true,
                    Optional.empty(),
                    ""));
        }
        return s3;
    }

    private static IdRemapper renumbered(int... sourceToDestination) {
        var idMap = new IdRemapper();
        for (int i = 0; i < sourceToDestination.length; i += 2) {
            idMap.put("kb_file", sourceToDestination[i], sourceToDestination[i + 1]);
        }
        return idMap;
    }

    /**
     * One station on the storage, under a fresh identifier, with three wiki files named by the ids 1 to 3.
     */
    private static final class Fixture {
        private final StorageBackend backend;
        private final UUID sourceUid = UUID.randomUUID();
        private final SharedStorageFiles.Run run;
        private final SharedStorageFiles files;

        Fixture(StorageBackend backend) {
            this(backend, true);
        }

        /**
         * @param keepsIdentifier whether the destination took over the source's identifier, as a moved
         *                        station does wherever it is free
         */
        Fixture(StorageBackend backend, boolean keepsIdentifier) {
            this.backend = spy(backend);
            this.run = new SharedStorageFiles.Run(
                    new StorageScope.Station(1, keepsIdentifier ? sourceUid : UUID.randomUUID()), sourceUid);
            var resolver = mock(StorageBackendResolver.class);
            when(resolver.forScope(any(), any())).thenReturn(this.backend);
            this.files = new SharedStorageFiles(resolver);
            SOURCE.forEach((id, text) -> store(wikiBase() + "/" + id + "/content", text));
        }

        StorageBackend backend() {
            return backend;
        }

        SharedStorageFiles.Run run() {
            return run;
        }

        SharedStorageFiles files() {
            return files;
        }

        ImportProgress progress() {
            return new ImportProgress(1, sourceUid, "Shared", List.of(), "", "", ImportProgress.Target.NEW_STATION);
        }

        String sourceBase(StorageCategory category) {
            return run.sourceBase(category);
        }

        String wikiBase() {
            return sourceBase(WIKI);
        }

        /** The wiki files under the source's identifier, by the id that names them. */
        Map<Integer, String> wikiFiles() {
            var out = new TreeMap<Integer, String>();
            for (String key : backend.listByPrefix(wikiBase() + "/")) {
                String id = key.substring(
                        wikiBase().length() + 1, key.indexOf('/', wikiBase().length() + 1));
                out.put(Integer.parseInt(id), read(key));
            }
            return out;
        }

        List<String> staged() {
            return backend.listByPrefix("transfer/" + sourceUid + "/");
        }

        void store(String key, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            backend.store(key, new ByteArrayInputStream(bytes), bytes.length, ObjectMetadata.of("text/plain"));
        }

        String read(String key) {
            try (var stream = backend.read(key).orElseThrow()) {
                return new String(stream.body().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Lets the {@code nth} copy onto a key under {@code prefix} fail once, as a storage that went away. */
        void failOnCopy(String prefix, int nth) {
            var seen = new AtomicInteger();
            doAnswer(call -> {
                        String target = call.getArgument(1);
                        if (target.startsWith(prefix) && seen.incrementAndGet() == nth) {
                            throw new StorageException("the storage went away");
                        }
                        return call.callRealMethod();
                    })
                    .when(backend)
                    .copy(anyString(), anyString());
        }
    }
}
