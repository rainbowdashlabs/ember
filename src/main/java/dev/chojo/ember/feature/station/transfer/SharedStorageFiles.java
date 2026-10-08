/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Puts the files of a moving station where the destination looks for them when the destination took over
 * the station's own storage, so both installations read and write the same objects.
 *
 * <p>Nothing travels between the installations: every file is copied inside that storage, by the storage
 * itself where it can, from the key it has on the source to the key {@link TransferFileKeys} gives it here.
 * The source's objects are only read, and stay where they are until the old station is deleted.
 *
 * <p>A station usually keeps its identifier when it moves, and with it the place its files lie in. The
 * files of a category named by row ids then share their place with the files they are copied to, and since
 * the ids of both installations overlap, the new key of one file can be the old key of another that is
 * still to be read. Such a category is therefore copied in two passes: every file is first staged, under
 * its old key, below {@code transfer/<station identifier>/<transfer>/}, and only once all of them are
 * staged, which a marker beside them records, are they copied from there to their new keys. No file is
 * written over before it has been read, and the staged copies stay the same whatever ids a later attempt
 * hands out. A retried import, which runs under the same transfer, therefore copies from the staged files
 * again instead of from the shared place, which the attempt before may already have written to; staging in
 * place of holding every file in memory until all are read is what keeps a station of any size within
 * reach. The staged copies belong to one transfer, named by a hash of its token, so a later move of the
 * same station never reads what an earlier one left behind.
 *
 * <p>Once the import has finished the staged copies are removed. When it fails and takes away the station
 * it made, every staged file is first put back under its old key and what the import added there is
 * removed, so the source finds its files as they were. A station made under a new identifier loses the
 * copies made for it instead. Each of these steps that cannot be completed throws, naming what was left.
 */
@Singleton
public class SharedStorageFiles {
    private static final Logger log = LoggerFactory.getLogger(SharedStorageFiles.class);
    private static final String STAGING_ROOT = "transfer";
    private static final String STAGED_MARKER = ".staged";

    private final StorageBackendResolver resolver;

    @Inject
    public SharedStorageFiles(StorageBackendResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Copies every file of one category to the key its row has on the destination. A file whose row did
     * not arrive is left out.
     *
     * @param run      the import
     * @param category the category to copy
     * @param idMap    the source-to-destination ids of the imported rows
     * @param progress the run progress, updated per key
     */
    public void copyCategory(Run run, StorageCategory category, IdRemapper idMap, ImportProgress progress) {
        boolean inPlace = run.inPlace();
        if (inPlace && !TransferFileKeys.renumbers(category)) {
            progress.setSubTotal(0);
            return;
        }
        StorageBackend backend = resolver.forScope(run.destination(), category);
        String from = inPlace ? staged(backend, run, category) : run.sourceBase(category);
        String to = run.destinationBase(category);
        List<String> keys = relativeKeys(backend, from);
        progress.setSubTotal(keys.size());
        int copied = 0;
        for (String key : keys) {
            Optional<String> target = TransferFileKeys.destinationKey(category, key, idMap);
            if (target.isPresent() && backend.copy(from + "/" + key, to + "/" + target.get())) copied++;
            progress.incrementSub();
        }
        log.info("Copied {} of {} key(s) of category {} inside the shared storage", copied, keys.size(), category);
    }

    /**
     * Removes the staged copies of an import. Every category is tried, whatever happens to the others.
     *
     * @param run the import
     * @throws StorageException when the staged copies of a category could not be removed, naming where
     *                          they stay
     */
    public void release(Run run) {
        if (!run.inPlace()) return;
        forEachCategory(stagedCategories(), run, "remove the staged copies", category -> {
            StorageBackend backend = resolver.forScope(run.destination(), category);
            backend.delete(run.marker(category));
            deleteAll(backend, run.stagingBase(category));
        });
    }

    /**
     * Takes back what a failed import that made its station wrote. A station that kept its identifier
     * gives the source back its files: every staged file returns to its old key, a key the import added is
     * removed, and the staged copies go. A station made under a new identifier loses every copy made for
     * it.
     *
     * @param run the import
     * @throws StorageException when the files could not be taken back. The staged copies then stay, so a
     *                          retried import still copies from them
     */
    public void restore(Run run) {
        if (!run.inPlace()) {
            forEachCategory(
                    TransferFileImporter.transferrableStationCategories(), run, "remove the copies", category -> {
                        deleteAll(resolver.forScope(run.destination(), category), run.destinationBase(category));
                    });
            return;
        }
        for (StorageCategory category : stagedCategories()) {
            try {
                StorageBackend backend = resolver.forScope(run.destination(), category);
                if (backend.exists(run.marker(category))) restoreCategory(backend, run, category);
            } catch (RuntimeException e) {
                throw new StorageException(
                        "Could not give back the files of category %s for %s; the staged copies stay below %s"
                                .formatted(category, run.sourceUid(), run.stagingBase(category)),
                        e);
            }
        }
        release(run);
    }

    private static void restoreCategory(StorageBackend backend, Run run, StorageCategory category) {
        String staging = run.stagingBase(category);
        String source = run.sourceBase(category);
        var stagedKeys = new HashSet<>(relativeKeys(backend, staging));
        for (String key : stagedKeys) {
            backend.copy(staging + "/" + key, source + "/" + key);
        }
        for (String key : relativeKeys(backend, source)) {
            if (!stagedKeys.contains(key)) backend.delete(source + "/" + key);
        }
        log.info("Restored {} file(s) of category {} for {}", stagedKeys.size(), category, run.sourceUid());
    }

    /**
     * Runs one step over every category, and throws once all were tried when any of them failed.
     */
    private static void forEachCategory(
            List<StorageCategory> categories, Run run, String step, Consumer<StorageCategory> action) {
        List<RuntimeException> failures = new ArrayList<>();
        List<StorageCategory> failed = new ArrayList<>();
        for (StorageCategory category : categories) {
            try {
                action.accept(category);
            } catch (RuntimeException e) {
                failures.add(e);
                failed.add(category);
            }
        }
        if (failures.isEmpty()) return;
        var error = new StorageException("Could not %s of categories %s for %s"
                .formatted(step, failed, run.destination().prefix()));
        failures.forEach(error::addSuppressed);
        throw error;
    }

    /**
     * The place every file of the category is read from: the staged copies, made first when no marker says
     * they are complete.
     */
    private static String staged(StorageBackend backend, Run run, StorageCategory category) {
        String staging = run.stagingBase(category);
        String marker = run.marker(category);
        if (backend.exists(marker)) return staging;
        deleteAll(backend, staging);
        String source = run.sourceBase(category);
        for (String key : relativeKeys(backend, source)) {
            backend.copy(source + "/" + key, staging + "/" + key);
        }
        backend.store(marker, new ByteArrayInputStream(new byte[0]), 0, ObjectMetadata.of("text/plain"));
        return staging;
    }

    private static List<StorageCategory> stagedCategories() {
        return TransferFileImporter.transferrableStationCategories().stream()
                .filter(TransferFileKeys::renumbers)
                .toList();
    }

    private static List<String> relativeKeys(StorageBackend backend, String base) {
        return backend.listByPrefix(base + "/").stream()
                .map(key -> key.substring(base.length() + 1))
                .toList();
    }

    private static void deleteAll(StorageBackend backend, String base) {
        for (String key : backend.listByPrefix(base + "/")) {
            backend.delete(key);
        }
    }

    /**
     * One import onto storage the destination shares with the source.
     *
     * @param destination the station the files are copied for
     * @param sourceUid   the identifier the station has on the source, which names the place its files lie in
     * @param transfer    the name of the transfer the import runs under, which keeps its staged copies apart
     *                    from those of any other move of the station
     */
    public record Run(StorageScope.Station destination, UUID sourceUid, String transfer) {
        /**
         * The import under one transfer token, named by a hash of the token so the token itself never
         * appears in the storage.
         *
         * @param destination the station the files are copied for
         * @param sourceUid   the identifier the station has on the source
         * @param token       the transfer token the import runs under, the same for every retry
         * @return the import
         */
        public static Run forTransfer(StorageScope.Station destination, UUID sourceUid, String token) {
            return new Run(destination, sourceUid, Sha256.hexPrefix(token, 32));
        }

        /** Whether the station kept its identifier, so its files are copied within the place they lie in. */
        boolean inPlace() {
            return destination.stationUid().equals(sourceUid);
        }

        String sourceBase(StorageCategory category) {
            return new StorageScope.Station(destination.stationId(), sourceUid).prefix() + "/" + category.prefix();
        }

        String destinationBase(StorageCategory category) {
            return destination.prefix() + "/" + category.prefix();
        }

        String stagingBase(StorageCategory category) {
            return STAGING_ROOT + "/" + sourceUid + "/" + transfer + "/" + category.prefix();
        }

        String marker(StorageCategory category) {
            return stagingBase(category) + STAGED_MARKER;
        }
    }
}
