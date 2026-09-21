/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.util.FilePicture;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The picture of a wiki file, which is what lets a wiki of photographs and sheets be browsed by what
 * is in it rather than by name.
 *
 * <p>Made when a file is stored, and made on the first request for a file that was stored before
 * this existed. Doing only the first would leave every file already in a wiki without a picture
 * until somebody uploaded it again, which nobody is going to do; doing only the second would put a
 * PDF render in front of the first reader of every file. Keyed by {@code file-<id>} under the owning
 * station's scope.
 *
 * <p>A file no picture can be made of, an image in a format the variants cannot read or a PDF that
 * will not open, is remembered as such until the process restarts or the file is stored again, so
 * a grid showing it does not try and fail and warn about it on every visit.
 */
@Singleton
public class KbFilePictureService {
    private static final Logger log = LoggerFactory.getLogger(KbFilePictureService.class);
    private static final StorageCategory CATEGORY = StorageCategory.IMAGE_KB_FILE_PICTURE;
    private static final int PDF_DPI = 96;
    private static final String PDF_TYPE = "application/pdf";
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> UNTYPED = Set.of("application/octet-stream", "binary/octet-stream");

    private final ImageVariantService variants;
    private final KbFileStorageService files;
    private final StationRepository stationRepository;
    private final Set<String> unmakeable = ConcurrentHashMap.newKeySet();

    @Inject
    public KbFilePictureService(
            ImageVariantService variants, KbFileStorageService files, StationRepository stationRepository) {
        this.variants = variants;
        this.files = files;
        this.stationRepository = stationRepository;
    }

    /**
     * Makes the picture of a file from its bytes, replacing whatever picture it had. A file of a
     * kind that has none loses any picture it had before, which is what a PDF replaced by a
     * spreadsheet should do.
     */
    public void make(int stationId, int fileId, String mimeType, byte[] data) {
        unmakeable.remove(memo(stationId, fileId));
        String type = pictureType(mimeType, data);
        if (!FilePicture.exists(type)) {
            delete(stationId, fileId);
            return;
        }
        try {
            var picture = FilePicture.of(type, data, PDF_DPI);
            if (picture.isEmpty()) {
                unmakeable.add(memo(stationId, fileId));
                return;
            }
            variants.store(scope(stationId), CATEGORY, key(fileId), picture.get(), type);
        } catch (Exception e) {
            unmakeable.add(memo(stationId, fileId));
            log.warn("No picture could be made of wiki file {} in station {}", fileId, stationId, e);
        }
    }

    /**
     * The picture of a file at the size asked for, made from the stored file first where it has none
     * yet. Empty for a file of a kind that has no picture, or one none could be made of.
     *
     * @param size the longest side wanted, answered by the nearest size kept at or above it
     */
    public Optional<ImageVariantService.ImageData> read(int stationId, int fileId, String mimeType, int size) {
        var existing = variants.read(scope(stationId), CATEGORY, key(fileId), size);
        if (existing.isPresent() || !mayHavePicture(mimeType)) return existing;
        if (unmakeable.contains(memo(stationId, fileId))) return Optional.empty();
        files.read(stationId, fileId).ifPresent(file -> make(stationId, fileId, mimeType, file.data()));
        return variants.read(scope(stationId), CATEGORY, key(fileId), size);
    }

    /**
     * What a picture is made from: the stored type where it names something, and what the bytes turn
     * out to be where it names nothing.
     *
     * <p>A phone often uploads without saying what it sends, and the file is stored as a stream of
     * bytes. The wiki still calls a {@code .jpg} a picture by its name, so its tile asks for one, and
     * a type that says nothing must not be taken for proof that there is none. A declared type that
     * says something else, a spreadsheet's, is believed.
     */
    static String pictureType(String storedType, byte[] data) {
        if (!isUntyped(storedType)) return storedType;
        var image = ImageVariantService.sniffImageMime(data);
        if (image.isPresent()) return image.get();
        return startsWith(data, PDF_SIGNATURE) ? PDF_TYPE : storedType;
    }

    /** Whether a file of this stored type may turn out to have a picture once its bytes are read. */
    private static boolean mayHavePicture(String storedType) {
        return FilePicture.exists(storedType) || isUntyped(storedType);
    }

    private static boolean isUntyped(String storedType) {
        return storedType == null || storedType.isBlank() || UNTYPED.contains(storedType.toLowerCase(Locale.ROOT));
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        return Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** Removes every size of a file's picture. */
    public void delete(int stationId, int fileId) {
        variants.delete(scope(stationId), CATEGORY, key(fileId));
    }

    /**
     * The key a file's picture is kept under. Exposed so that deleting a file's bytes can take its
     * picture with it, wherever that deletion happens.
     */
    public static String key(int fileId) {
        return "file-" + fileId;
    }

    private static String memo(int stationId, int fileId) {
        return stationId + "/" + fileId;
    }

    private StorageScope.Station scope(int stationId) {
        return new StorageScope.Station(stationId, stationRepository.resolveUid(stationId));
    }
}
