/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.image.VariantSet;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The files of a station moving to another instance, as the destination pulls them: listed a page at a
 * time, then read one by one.
 *
 * <p>Only originals are handed over. Smaller copies of a picture are made again on the other side, so
 * carrying them would only be carrying bytes twice.
 */
@Singleton
public class StationTransferFileService {
    private static final Logger log = LoggerFactory.getLogger(StationTransferFileService.class);
    private static final int DEFAULT_LIST_LIMIT = 500;
    private static final int MAX_LIST_LIMIT = 2000;

    private final StationRepository stationRepository;
    private final StorageService storageService;

    @Inject
    public StationTransferFileService(StationRepository stationRepository, StorageService storageService) {
        this.stationRepository = stationRepository;
        this.storageService = storageService;
    }

    /**
     * One page of the keys of a category, sorted, after the cursor.
     *
     * @param stationId the station moving away
     * @param category  the category, one a station keeps and may move
     * @param after     the last key of the page before, or null for the first page
     * @param limit     how many keys at most, zero or less for the default; held at 2000 at most
     * @return the page, with the cursor for the next one
     */
    public ListKeysResponse page(int stationId, StorageCategory category, String after, int limit) {
        int size = limit <= 0 ? DEFAULT_LIST_LIMIT : Math.min(limit, MAX_LIST_LIMIT);
        List<String> sorted =
                new ArrayList<>(originalsOnly(category, storageService.listKeys(scopeOf(stationId), category, "")));
        Collections.sort(sorted);
        int start = startAfter(sorted, after);
        int end = Math.min(start + size, sorted.size());
        List<String> page = sorted.subList(start, end);
        String next = end < sorted.size() ? page.getLast() : null;
        log.info(
                "[export] listing files: station {} category {} after='{}' limit={} → {} key(s), next={}",
                stationId,
                category,
                after == null ? "" : after,
                size,
                page.size(),
                next == null ? "none" : "present");
        return new ListKeysResponse(List.copyOf(page), next, sorted.size());
    }

    /**
     * The bytes of one file.
     *
     * @param stationId the station moving away
     * @param category  the category it is kept under
     * @param key       its key within the category
     * @return the open stream, for the caller to close
     */
    public StoredStream open(int stationId, StorageCategory category, String key) {
        var stream = storageService
                .readRelative(scopeOf(stationId), category, key)
                .orElseThrow(Refusal.TRANSFER_FILE_NOT_HERE::raise);
        log.info(
                "[export] streaming file: station {} category {} key '{}' ({} bytes)",
                stationId,
                category,
                key,
                stream.contentLength());
        return stream;
    }

    private StorageScope.Station scopeOf(int stationId) {
        Station station =
                stationRepository.findById(stationId).orElseThrow(Refusal.STATION_NOT_HERE_FOR_TRANSFER::raise);
        return new StorageScope.Station(stationId, station.uid());
    }

    private static int startAfter(List<String> sorted, String after) {
        if (after == null || after.isBlank()) return 0;
        int index = Collections.binarySearch(sorted, after);
        return index >= 0 ? index + 1 : -index - 1;
    }

    /**
     * Strips derived image variants (smaller-width resizes and re-encoded WebP copies) from a
     * raw category listing so the destination only pulls the bytes it cannot regenerate locally.
     * For categories that never have variants the input is returned unchanged.
     */
    public static List<String> originalsOnly(StorageCategory category, List<String> keys) {
        var profile = ImageProfile.of(category);
        if (profile.isEmpty()) return keys;

        Map<String, List<String>> byDir = new LinkedHashMap<>();
        for (String key : keys) {
            int slash = key.lastIndexOf('/');
            String dir = slash < 0 ? "" : key.substring(0, slash);
            byDir.computeIfAbsent(dir, _ -> new ArrayList<>()).add(key);
        }

        var out = new ArrayList<String>(byDir.size());
        for (var entry : byDir.entrySet()) {
            String dir = entry.getKey();
            VariantSet.of(profile.get().layout(), entry.getValue())
                    .original()
                    .ifPresent(
                            original -> out.add(dir.isEmpty() ? original.fileName() : dir + "/" + original.fileName()));
        }
        return out;
    }

    /**
     * Wire shape of {@code GET /files/{category}}. {@code total} is the count of original keys
     * for the whole category, repeated identically on every page so the destination can pin its
     * progress denominator on the first response and never see it grow as later pages arrive.
     */
    public record ListKeysResponse(List<String> keys, String next, int total) {}
}
