/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.entity.StationFileFolder;
import dev.chojo.ember.feature.media.entity.StationFileTag;
import dev.chojo.ember.feature.media.repository.MediaFileRepository;
import dev.chojo.ember.feature.media.repository.MediaMetaRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The station media library: uploading into it, browsing it, organising it, and clearing out
 * what nothing points at any more.
 *
 * <p>Two things about it are worth knowing before reading further.
 *
 * <p><b>Uploads are deduplicated per station by content hash.</b> Uploading bytes the station
 * already holds returns the existing file and records the uploader against it, rather than
 * storing a second copy. That is why ownership is a set: without it, the second member to upload
 * the same picture would be handed a file owned by somebody else and would not see the thing they
 * just uploaded.
 *
 * <p><b>Ownership scopes the browser, never delivery.</b> A member holding none of the content
 * permissions sees only what they uploaded themselves, but an image inside a ticket description
 * has to render for everyone who may read that ticket. Ownership answers "what may I pick from",
 * never "what may I see on a page".
 */
@Singleton
public class MediaLibraryService {
    /**
     * What stands in for a station identifier in a file address when the file belongs to the
     * instance rather than to a station. The delivery route takes it literally, so a picture from
     * the instance library is addressed the way a station's is and nothing reading the address
     * needs to know the difference.
     */
    public static final String INSTANCE_SCOPE = "instance";

    private static final Logger log = LoggerFactory.getLogger(MediaLibraryService.class);

    private final MediaFileRepository fileRepository;
    private final MediaMetaRepository metaRepository;
    private final MediaStorageService storage;
    private final MediaVariantService variantService;
    private final MediaReferenceRegistry references;
    private final StorageQuotaService quotaService;

    @Inject
    public MediaLibraryService(
            MediaFileRepository fileRepository,
            MediaMetaRepository metaRepository,
            MediaStorageService storage,
            MediaVariantService variantService,
            MediaReferenceRegistry references,
            StorageQuotaService quotaService) {
        this.fileRepository = fileRepository;
        this.metaRepository = metaRepository;
        this.storage = storage;
        this.variantService = variantService;
        this.references = references;
        this.quotaService = quotaService;
    }

    // --- Upload and delivery ---

    /**
     * Stores a file for the station, or returns the one it already holds when the bytes are
     * identical. Either way {@code memberId} is recorded as an uploader, so a dedup hit still
     * puts the file into that member's own list.
     *
     * @param stationId the station whose library takes it, or {@code null} for the instance's own.
     *                  An instance file counts against no station's quota, because there is no
     *                  station to count it against
     * @param pageId    the page the upload came from, or {@code null} for a station-wide upload
     * @param memberId  the member uploading, or {@code null} when no member is in play (imports,
     *                  seeding, and anything the instance uploads)
     */
    public StationFile upload(
            Integer stationId, Integer pageId, Integer memberId, String fileName, String mimeType, byte[] data)
            throws IOException {
        boolean isImage = mimeType != null && mimeType.startsWith("image/");
        if (stationId != null) {
            if (isImage) {
                quotaService.checkImageSize(stationId, data.length);
            } else {
                quotaService.checkFileSize(stationId, data.length);
            }
        }
        String contentHash = MediaStorageService.hash(data);

        var existing = fileRepository.findByStationAndHash(stationId, contentHash);
        if (existing.isPresent()) {
            // Defensive: a crashed migration could have left the row without its bytes. store()
            // is idempotent for identical bytes.
            storage.store(stationId, contentHash, data, mimeType);
            recordUploader(existing.get().id(), memberId);
            return existing.get();
        }

        if (stationId != null) {
            quotaService.checkQuota(stationId, StorageCategory.MEDIA_FILES, data.length);
        }
        var file = fileRepository.create(pageId, stationId, contentHash, fileName, mimeType, data.length);
        storage.store(stationId, contentHash, data, mimeType);
        if (isImage) {
            variantService.generateVariants(stationId, contentHash, data, mimeType);
        }
        if (stationId != null) {
            quotaService.trackDelta(stationId, StorageCategory.MEDIA_FILES, data.length, 1);
        }
        recordUploader(file.id(), memberId);
        log.info("Media file {} uploaded to station {} ({} bytes)", file.id(), stationId, data.length);
        return file;
    }

    /**
     * The best-fit variant of a file for the given requested width and {@code Accept} header.
     * Non-image files always return the original.
     */
    public Optional<MediaStorageService.FileData> readVariant(
            Integer stationId, String contentHash, Integer requestedWidth, String acceptHeader) {
        return variantService.readBest(stationId, contentHash, requestedWidth, acceptHeader);
    }

    /**
     * Reads a file by station and content hash, which is how the delivery routes address it.
     */
    public Optional<MediaStorageService.FileData> read(Integer stationId, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) return Optional.empty();
        if (fileRepository.findByStationAndHash(stationId, contentHash).isEmpty()) return Optional.empty();
        return storage.read(stationId, contentHash);
    }

    public Optional<MediaStorageService.FileData> readById(int fileId) {
        var file = fileRepository.findById(fileId).orElse(null);
        if (file == null || file.contentHash() == null) return Optional.empty();
        return storage.read(file.stationId(), file.contentHash());
    }

    public Optional<StationFile> findFile(int fileId) {
        return fileRepository.findById(fileId);
    }

    public Optional<StationFile> findByHash(int stationId, String contentHash) {
        return fileRepository.findByStationAndHash(stationId, contentHash);
    }

    // --- Browsing ---

    /**
     * The whole library, for a member who holds one of the content permissions.
     *
     * @param stationId the station whose library to list, or null for the instance's own
     */
    public List<FileListing> listLibrary(Integer stationId) {
        return decorate(stationId, fileRepository.findByStation(stationId));
    }

    /**
     * Only what this member uploaded, for everybody else. A flat list, because organising station
     * media stays with the people who hold a content permission - that is what makes this
     * ownership light.
     */
    public List<FileListing> listOwnUploads(int stationId, int memberId) {
        return decorate(stationId, fileRepository.findByUploader(stationId, memberId));
    }

    private List<FileListing> decorate(Integer stationId, List<StationFile> files) {
        // Which files nothing points at is worked out by reading a station's content. The instance
        // has none of that to read, so its files are listed without the claim rather than with a
        // guess: saying "unused" about a file nobody has looked for would be worse than saying
        // nothing.
        Set<Integer> unused = stationId == null ? Set.of() : findUnusedFileIds(stationId);
        var ids = files.stream().map(StationFile::id).toList();
        var tagAssignments = metaRepository.findTagAssignments(ids);
        var uploaders = fileRepository.findFirstUploaders(ids);
        return files.stream()
                .map(f -> new FileListing(
                        f,
                        !unused.contains(f.id()),
                        tagAssignments.getOrDefault(f.id(), Set.of()),
                        uploaders.get(f.id())))
                .toList();
    }

    // --- Deletion ---

    /**
     * Removes the file, its bytes and every uploader row. This is the manager's delete.
     *
     * <p>A file some entry hands out as an attachment is refused rather than deleted, and the
     * refusal says how many entries would break. The database enforces the same thing a second
     * time through {@code ON DELETE RESTRICT}, which makes an attached file the only sort that
     * survives a bug in the reference registry.
     */
    public boolean deleteFile(int fileId) {
        var file = fileRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        int handedOut = references.handedOutBy(fileId);
        if (handedOut > 0) {
            throw new BadRequestResponse(
                    "This file is attached to " + handedOut + " news entry/entries. Detach it there first.");
        }
        boolean deleted = fileRepository.delete(fileId);
        if (deleted) {
            if (file.contentHash() != null) {
                storage.delete(file.stationId(), file.contentHash());
            }
            if (file.stationId() != null) {
                quotaService.onFileDeleted(file.stationId(), StorageCategory.MEDIA_FILES, file.fileSize());
            }
            log.info("Media file {} deleted from station {}", fileId, file.stationId());
        }
        return deleted;
    }

    /**
     * The "I uploaded the wrong picture" escape for a member without a content permission: their
     * uploader row goes, and the file goes with it only when the set empties and nothing
     * references it. Deleting bytes cannot be a per-owner act, so it must not let one member take
     * away an image another has already put into a ticket.
     *
     * @return whether the member had uploaded the file at all
     */
    public boolean releaseUpload(int fileId, int memberId) {
        var file = fileRepository.findById(fileId).orElse(null);
        if (file == null) return false;
        if (!fileRepository.removeUploader(fileId, memberId)) return false;
        if (fileRepository.hasAnyUploader(fileId)) return true;
        // An instance file has no station whose content could point at it, and no member uploaded
        // it either, so this path is never walked for one.
        if (file.stationId() != null && isReferenced(file, references.collect(file.stationId()))) return true;
        deleteFile(fileId);
        return true;
    }

    public boolean mayRelease(int fileId, int memberId) {
        return fileRepository.hasUploader(fileId, memberId);
    }

    // --- Pruning ---

    /**
     * The files nothing points at any more. A file somebody claims as their upload is never in
     * this set: an image may outlive the first place it was used, and taking it away from the
     * member who brought it in is the worse of the two mistakes.
     */
    public Set<Integer> findUnusedFileIds(int stationId) {
        Set<String> referenced = references.collect(stationId);
        Set<Integer> owned = new HashSet<>(fileRepository.findOwnedFileIds(stationId));
        Set<Integer> unused = new HashSet<>();
        for (var file : fileRepository.findByStation(stationId)) {
            if (owned.contains(file.id())) continue;
            if (!isReferenced(file, referenced)) unused.add(file.id());
        }
        return unused;
    }

    /**
     * Deletes every unreferenced file in the station and returns how many went. Only ever invoked
     * when a manager explicitly asks for it.
     */
    public int pruneUnusedFiles(int stationId) {
        Set<Integer> unused = findUnusedFileIds(stationId);
        int removed = 0;
        for (var file : fileRepository.findByStation(stationId)) {
            if (!unused.contains(file.id())) continue;
            if (file.contentHash() != null) {
                storage.delete(file.stationId(), file.contentHash());
            }
            fileRepository.delete(file.id());
            quotaService.onFileDeleted(stationId, StorageCategory.MEDIA_FILES, file.fileSize());
            removed++;
        }
        log.info("Pruned {} unused media files from station {}", removed, stationId);
        return removed;
    }

    private boolean isReferenced(StationFile file, Set<String> referenced) {
        return (file.contentHash() != null && referenced.contains(file.contentHash()))
                || referenced.contains(String.valueOf(file.id()));
    }

    // --- Metadata ---

    public boolean updateFileMeta(int stationId, int fileId, String altText, String description) {
        var existing = fileRepository.findById(fileId).orElse(null);
        if (existing == null || !Integer.valueOf(stationId).equals(existing.stationId())) {
            log.warn("Metadata update for media file {} skipped: not a file of station {}", fileId, stationId);
            return false;
        }
        boolean updated = fileRepository.updateMeta(fileId, altText, description);
        if (updated) log.info("Media file {} was given new alt text and description", fileId);
        else log.warn("Metadata update for media file {} affected zero rows", fileId);
        return updated;
    }

    public boolean moveFileToFolder(int stationId, int fileId, Integer folderId) {
        var file = fileRepository.findById(fileId).orElse(null);
        if (file == null || !Integer.valueOf(stationId).equals(file.stationId())) return false;
        boolean moved = metaRepository.moveFileToFolder(fileId, folderId);
        if (moved) {
            log.info("Media file {} moved to folder {} in station {}", fileId, folderId, stationId);
        }
        return moved;
    }

    // --- Folders ---

    public StationFileFolder createFolder(int stationId, Integer parentId, String name, int sortOrder) {
        var folder = metaRepository.createFolder(stationId, parentId, name, sortOrder);
        log.info("Media folder {} created in station {}", folder.id(), stationId);
        return folder;
    }

    public List<StationFileFolder> listFolders(int stationId) {
        return metaRepository.findFoldersByStation(stationId);
    }

    public boolean updateFolder(int stationId, int folderId, Integer parentId, String name, int sortOrder) {
        var folder = metaRepository.findFolder(folderId).orElse(null);
        if (folder == null || folder.stationId() != stationId) return false;
        boolean updated = metaRepository.updateFolder(folderId, parentId, name, sortOrder);
        if (updated) {
            log.info("Media folder {} updated in station {}", folderId, stationId);
        }
        return updated;
    }

    public boolean deleteFolder(int stationId, int folderId) {
        var folder = metaRepository.findFolder(folderId).orElse(null);
        if (folder == null || folder.stationId() != stationId) return false;
        boolean deleted = metaRepository.deleteFolder(folderId);
        if (deleted) {
            log.info("Media folder {} deleted from station {}", folderId, stationId);
        }
        return deleted;
    }

    // --- Tags ---

    public StationFileTag createTag(int stationId, String name, String color) {
        var tag = metaRepository.createTag(stationId, name, color);
        log.info("Media tag {} created in station {}", tag.id(), stationId);
        return tag;
    }

    public List<StationFileTag> listTags(int stationId) {
        return metaRepository.findTagsByStation(stationId);
    }

    public boolean updateTag(int stationId, int tagId, String name, String color) {
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (tag == null || tag.stationId() != stationId) return false;
        boolean updated = metaRepository.updateTag(tagId, name, color);
        if (updated) {
            log.info("Media tag {} updated in station {}", tagId, stationId);
        }
        return updated;
    }

    public boolean deleteTag(int stationId, int tagId) {
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (tag == null || tag.stationId() != stationId) return false;
        boolean deleted = metaRepository.deleteTag(tagId);
        if (deleted) {
            log.info("Media tag {} deleted from station {}", tagId, stationId);
        }
        return deleted;
    }

    public boolean assignTag(int stationId, int fileId, int tagId) {
        var file = fileRepository.findById(fileId).orElse(null);
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (file == null || file.stationId() != stationId || tag == null || tag.stationId() != stationId) return false;
        metaRepository.assignTag(fileId, tagId);
        log.info("Media file {} was tagged {}", fileId, tagId);
        return true;
    }

    public boolean unassignTag(int stationId, int fileId, int tagId) {
        var file = fileRepository.findById(fileId).orElse(null);
        if (file == null || file.stationId() != stationId) return false;
        boolean unassigned = metaRepository.unassignTag(fileId, tagId);
        if (unassigned) log.info("Media file {} lost the tag {}", fileId, tagId);
        return unassigned;
    }

    private void recordUploader(int fileId, Integer memberId) {
        if (memberId == null) return;
        fileRepository.addUploader(fileId, memberId);
    }

    /**
     * A file as the browser shows it.
     *
     * @param inUse      whether anything in the station points at the file
     * @param uploadedBy the member who first brought the file in, or {@code null} for a file that
     *                   predates uploader tracking
     */
    public record FileListing(StationFile file, boolean inUse, Set<Integer> tagIds, Integer uploadedBy) {}
}
