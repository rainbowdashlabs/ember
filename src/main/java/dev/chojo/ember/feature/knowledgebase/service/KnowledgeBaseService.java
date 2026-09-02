/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.cluster.service.ClusterAutoShareService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository.FolderPathNode;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.util.PdfText;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The folder and file tree of a station's knowledge base: creating entries of every kind, reading
 * and moving them around, and the per-member favourites and cross-references that sit on top.
 *
 * <p>What an entry <em>says</em> lives in {@link KbContentService}, who may see it in
 * {@link KbAccessService}, and the concerns specific to one kind of upload in
 * {@link KbPresentationService} and {@link KbLinkMetadataService}. Creating a file is the point
 * where those meet: the entry is written here, then handed to whichever of them owns the rest.
 */
@Singleton
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseRepository repository;
    private final KbFileStorageService fileStorage;
    private final KbContentService contentService;
    private final KbAccessService accessService;
    private final KbPresentationService presentationService;
    private final KbLinkMetadataService linkMetadataService;
    private final PresentationCompressor officeCompressor;
    private final PdfCompressor pdfCompressor;
    private final ClusterAutoShareService autoShareService;

    @Inject
    public KnowledgeBaseService(
            KnowledgeBaseRepository repository,
            KbFileStorageService fileStorage,
            KbContentService contentService,
            KbAccessService accessService,
            KbPresentationService presentationService,
            KbLinkMetadataService linkMetadataService,
            PresentationCompressor officeCompressor,
            PdfCompressor pdfCompressor,
            ClusterAutoShareService autoShareService) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.contentService = contentService;
        this.accessService = accessService;
        this.presentationService = presentationService;
        this.linkMetadataService = linkMetadataService;
        this.officeCompressor = officeCompressor;
        this.pdfCompressor = pdfCompressor;
        this.autoShareService = autoShareService;
    }

    /**
     * Lists the folders directly below a parent folder.
     *
     * @param stationId the station to list for
     * @param parentId  the parent folder, or {@code null} for the tree root
     * @return the child folders
     */
    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        return repository.findFolders(stationId, parentId);
    }

    /**
     * Lists every folder of a station, at any depth.
     *
     * @param stationId the station to list for
     * @return the station's folders
     */
    public List<KbFolder> findAllFolders(int stationId) {
        return repository.findAllFolders(stationId);
    }

    /**
     * Reads a single folder.
     *
     * @param id the folder to read
     * @return the folder, or empty when it does not exist
     */
    public Optional<KbFolder> findFolder(int id) {
        return repository.findFolderById(id);
    }

    /**
     * The written path of many folders at once, from the root down, so a listing whose rows sit in
     * different folders spells them out in one query rather than one lookup per level per row.
     *
     * @param folderIds the folders to spell out
     * @return the path per folder id, each starting at the root
     */
    public Map<Integer, String> findFolderPaths(List<Integer> folderIds) {
        var paths = new HashMap<Integer, String>();
        repository
                .findFolderPaths(folderIds)
                .forEach((folderId, path) -> paths.put(
                        folderId, "/" + path.stream().map(FolderPathNode::name).collect(Collectors.joining("/"))));
        return paths;
    }

    /**
     * Every folder from the root down to each of the given folders, by id, so a check asking
     * whether something sits inside a set of folders does not climb one lookup at a time.
     *
     * @param folderIds the folders to walk up from
     * @return the ancestry per folder id, itself included
     */
    public Map<Integer, Set<Integer>> findFolderAncestries(List<Integer> folderIds) {
        var ancestries = new HashMap<Integer, Set<Integer>>();
        repository
                .findFolderPaths(folderIds)
                .forEach((folderId, path) -> ancestries.put(
                        folderId, path.stream().map(FolderPathNode::id).collect(Collectors.toSet())));
        return ancestries;
    }

    /**
     * Creates a folder.
     *
     * @param stationId   the station the folder belongs to
     * @param parentId    the parent folder, or {@code null} for the tree root
     * @param name        the folder name
     * @param description the folder description
     * @param createdBy   the creating member
     * @return the created folder
     */
    public KbFolder createFolder(int stationId, Integer parentId, String name, String description, int createdBy) {
        var folder = repository.createFolder(stationId, parentId, name, description, createdBy);
        autoShareService.shareKbFolder(stationId, folder.id());
        log.info("KB folder {} created in station {} by member {}", folder.id(), stationId, createdBy);
        return folder;
    }

    /**
     * Renames a folder and updates its presentation.
     *
     * @param id          the folder to update
     * @param name        the new name
     * @param description the new description
     * @param iconUrl     the new icon, or {@code null} for none
     * @param position    the new sort position
     * @return {@code true} when the folder existed
     */
    public boolean updateFolder(int id, String name, String description, String iconUrl, int position) {
        boolean updated = repository.updateFolder(id, name, description, iconUrl, position);
        if (updated) {
            log.info("KB folder {} updated", id);
        } else {
            log.warn("KB folder {} update matched no rows", id);
        }
        return updated;
    }

    /**
     * Deletes a folder.
     *
     * @param id the folder to delete
     * @return {@code true} when the folder existed
     */
    public boolean deleteFolder(int id) {
        boolean deleted = repository.deleteFolder(id);
        if (deleted) {
            log.info("KB folder {} deleted", id);
        } else {
            log.warn("KB folder {} delete matched no rows", id);
        }
        return deleted;
    }

    /**
     * Lists the files directly inside a folder.
     *
     * @param stationId the station to list for
     * @param folderId  the folder, or {@code null} for the tree root
     * @return the files
     */
    public List<KbFile> findFiles(int stationId, Integer folderId) {
        return repository.findFiles(stationId, folderId);
    }

    /**
     * Lists every file of a station that is visible on its public knowledge base.
     *
     * @param stationId the station to list for
     * @param mode      the station's public knowledge-base mode
     * @return the publicly visible files, empty when the public knowledge base is off
     */
    public List<KbFile> findAllPublicFiles(int stationId, PublicKbMode mode) {
        if (mode == PublicKbMode.OFF) return List.of();
        return repository.findAllFiles(stationId).stream()
                .filter(file -> accessService.isPubliclyVisible(mode, null, file.id()))
                .toList();
    }

    /**
     * Reads a single file.
     *
     * @param id the file to read
     * @return the file, or empty when it does not exist
     */
    public Optional<KbFile> findFile(int id) {
        return repository.findFileById(id);
    }

    /**
     * Creates a markdown file and stores its first version.
     *
     * @param stationId   the station the file belongs to
     * @param folderId    the folder to create it in, or {@code null} for the tree root
     * @param name        the file name
     * @param description the file description
     * @param content     the markdown body
     * @param createdBy   the creating member
     * @return the created file
     */
    public KbFile createMarkdownFile(
            int stationId, Integer folderId, String name, String description, String content, int createdBy) {
        var file = repository.createFile(
                stationId,
                folderId,
                name,
                description,
                KbFileType.MARKDOWN,
                "text/markdown",
                content.length(),
                null,
                createdBy);
        contentService.initialiseMarkdown(file.id(), content, createdBy);
        autoShareService.shareKbFile(stationId, file.id());
        log.info("KB markdown file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    /**
     * Creates an entry pointing at a YouTube video, indexing the video's title and channel so it
     * can be searched for by more than its URL.
     *
     * @param stationId   the station the file belongs to
     * @param folderId    the folder to create it in, or {@code null} for the tree root
     * @param name        the file name
     * @param description the file description
     * @param youtubeUrl  the video URL
     * @param createdBy   the creating member
     * @return the created file
     */
    public KbFile createYoutubeFile(
            int stationId, Integer folderId, String name, String description, String youtubeUrl, int createdBy) {
        var file = repository.createFile(
                stationId, folderId, name, description, KbFileType.YOUTUBE, null, 0, youtubeUrl, createdBy);
        contentService.storeExtractedText(file.id(), linkMetadataService.fetchYoutubeMetadata(youtubeUrl));
        autoShareService.shareKbFile(stationId, file.id());
        log.info("KB YouTube file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    /**
     * Stores an uploaded file, detecting what kind of document it is and routing it accordingly:
     * text is indexed as-is, a PDF has its text extracted, a slide deck is queued for conversion,
     * and anything else is kept as an opaque payload.
     *
     * @param stationId   the station the file belongs to
     * @param folderId    the folder to create it in, or {@code null} for the tree root
     * @param name        the file name
     * @param description the file description
     * @param data        the uploaded bytes
     * @param mimeType    the MIME type reported for the upload
     * @param createdBy   the creating member
     * @return the created file
     */
    public KbFile createUploadedFile(
            int stationId,
            Integer folderId,
            String name,
            String description,
            byte[] data,
            String mimeType,
            int createdBy) {
        KbFileType fileType = KbFileTypeDetector.detect(mimeType, name);
        byte[] payload = compressForStorage(data, mimeType);
        var file = repository.createFile(
                stationId, folderId, name, description, fileType, mimeType, payload.length, null, createdBy);
        switch (fileType) {
            case TEXT -> contentService.storeText(file.id(), new String(payload, StandardCharsets.UTF_8));
            case PDF -> {
                fileStorage.store(stationId, file.id(), payload, mimeType);
                contentService.storeExtractedText(file.id(), PdfText.extract(payload));
            }
            case PRESENTATION -> {
                fileStorage.store(stationId, file.id(), payload, mimeType);
                presentationService.startConversion(stationId, file.id(), payload, name);
                contentService.storeExtractedText(file.id(), null);
            }
            default -> {
                fileStorage.store(stationId, file.id(), payload, mimeType);
                contentService.storeExtractedText(file.id(), null);
            }
        }
        autoShareService.shareKbFile(stationId, file.id());
        log.info(
                "KB file {} created in station {} by member {} (type {}, {} bytes)",
                file.id(),
                stationId,
                createdBy,
                fileType,
                payload.length);
        return file;
    }

    /**
     * Creates an entry pointing at an external page. A name or description left blank is filled in
     * from what the page says about itself.
     *
     * @param stationId   the station the file belongs to
     * @param folderId    the folder to create it in, or {@code null} for the tree root
     * @param name        the file name, blank to take it from the page
     * @param description the file description, blank to take it from the page
     * @param linkUrl     the page URL
     * @param createdBy   the creating member
     * @return the created file
     */
    public KbFile createLinkFile(
            int stationId, Integer folderId, String name, String description, String linkUrl, int createdBy) {
        if (isBlank(name) || isBlank(description)) {
            var metadata = linkMetadataService.fetchUrlMetadata(linkUrl);
            if (isBlank(name)) {
                name = metadata.title() != null ? metadata.title() : linkUrl;
            }
            if (isBlank(description)) {
                description = metadata.description() != null ? metadata.description() : "";
            }
        }
        var file = repository.createFile(
                stationId, folderId, name, description, KbFileType.LINK, null, 0, null, linkUrl, createdBy);
        contentService.storeText(file.id(), ((name != null ? name : "") + " " + description + " " + linkUrl).trim());
        autoShareService.shareKbFile(stationId, file.id());
        log.info("KB link file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    /**
     * Renames a file and updates its presentation.
     *
     * @param id          the file to update
     * @param name        the new name
     * @param description the new description
     * @param iconUrl     the new icon, or {@code null} for none
     * @param position    the new sort position
     * @return {@code true} when the file existed
     */
    public boolean updateFile(int id, String name, String description, String iconUrl, int position) {
        boolean updated = repository.updateFile(id, name, description, iconUrl, position);
        if (updated) {
            log.info("KB file {} updated", id);
        } else {
            log.warn("KB file {} update matched no rows", id);
        }
        return updated;
    }

    /**
     * Records that a file was copied from another station's knowledge base.
     *
     * @param fileId          the local copy
     * @param sourceFileId    the file it was copied from
     * @param sourceStationId the station it was copied from
     */
    public void setSourceReference(int fileId, int sourceFileId, int sourceStationId) {
        repository.setSourceReference(fileId, sourceFileId, sourceStationId);
        log.info("KB file {} is recorded as a copy of file {} at station {}", fileId, sourceFileId, sourceStationId);
    }

    /**
     * Deletes a file along with the binary payload behind it.
     *
     * @param id the file to delete
     * @return {@code true} when the file existed
     */
    public boolean deleteFile(int id) {
        repository.findFileById(id).ifPresent(file -> {
            fileStorage.delete(file.stationId(), id);
            // The container is the owned side, so nothing else would clean it up.
            contentService.deleteBlocks(file);
        });
        boolean deleted = repository.deleteFile(id);
        if (deleted) {
            log.info("KB file {} deleted", id);
        } else {
            log.warn("KB file {} delete matched no rows", id);
        }
        return deleted;
    }

    /**
     * Lists the files cross-referenced from a file.
     *
     * @param fileId the file to list for
     * @return the related files
     */
    public List<KbFile> findRelatedFiles(int fileId) {
        return repository.findRelatedFiles(fileId);
    }

    /**
     * Lists the files that cross-reference a file.
     *
     * <p>Derived from the rows that already exist rather than written alongside them, so a
     * reference points both ways without either side being able to take the other's away, and
     * every reference written so far reads back here without anything being changed.
     *
     * @param fileId the file being pointed at
     * @return the files pointing at it
     */
    public List<KbFile> findBacklinks(int fileId) {
        return repository.findBacklinks(fileId);
    }

    /**
     * Lists the articles of a station that were changed most recently.
     *
     * @param stationId the station to list for
     * @param limit     how many to answer with
     * @return the articles, newest change first
     */
    public List<KbFile> findRecentFiles(int stationId, int limit) {
        return repository.findRecentFiles(stationId, limit);
    }

    /**
     * Replaces the cross-references of a file.
     *
     * @param fileId        the file to update
     * @param targetFileIds the files it should point at
     */
    public void setRelatedFiles(int fileId, List<Integer> targetFileIds) {
        repository.setRelatedFiles(fileId, targetFileIds);
        log.info("KB file {} now points at {} related file(s)", fileId, targetFileIds.size());
    }

    /**
     * Marks a file as a favourite of a member.
     *
     * @param memberId the member
     * @param fileId   the file
     */
    public void addFavourite(int memberId, int fileId) {
        repository.addFavourite(memberId, fileId);
        log.debug("Member {} marked KB file {} as a favourite", memberId, fileId);
    }

    /**
     * Drops a member's favourite mark from a file.
     *
     * @param memberId the member
     * @param fileId   the file
     * @return {@code true} when the file was a favourite
     */
    public boolean removeFavourite(int memberId, int fileId) {
        boolean removed = repository.removeFavourite(memberId, fileId);
        if (removed) log.debug("Member {} unmarked KB file {}", memberId, fileId);
        return removed;
    }

    /**
     * Lists the files a member marked as favourites.
     *
     * @param memberId the member
     * @return the favourite files
     */
    public List<KbFile> findFavourites(int memberId) {
        return repository.findFavourites(memberId);
    }

    /**
     * Whether a member marked a file as a favourite.
     *
     * @param memberId the member
     * @param fileId   the file
     * @return {@code true} when the file is a favourite
     */
    public boolean isFavourite(int memberId, int fileId) {
        return repository.isFavourite(memberId, fileId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Routes a freshly-uploaded blob through the at-rest compressors registered in
     * {@code feature/storage/service}. Office archives and PDFs are recompressed losslessly;
     * everything else is returned untouched. Failures fall back to the original bytes -
     * compression is opportunistic, never a hard requirement.
     */
    private byte[] compressForStorage(byte[] data, String mimeType) {
        if (officeCompressor.shouldCompress(mimeType, data.length)) {
            return officeCompressor.compress(data);
        }
        if (pdfCompressor.shouldCompress(mimeType, data.length)) {
            return pdfCompressor.compress(data);
        }
        return data;
    }
}
