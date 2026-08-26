/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.content.service.ContentProjection;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Markdown;
import dev.chojo.ember.util.TextDiff;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a knowledge-base file says: its stored text body, the binary payload behind an upload, the
 * rendered markdown, and the version history that lets an edit be traced back and undone.
 *
 * <p>Only the first version of a file holds its full text; every later version stores a diff
 * against its predecessor, so reading an old version means replaying the patches up to it.
 */
@Singleton
public class KbContentService {
    private static final Logger log = LoggerFactory.getLogger(KbContentService.class);

    private final KnowledgeBaseRepository repository;
    private final ContentBlockService blocks;
    private final StationRepository stationRepository;
    private final KbFileStorageService fileStorage;
    private final KbSearchService searchService;

    @Inject
    public KbContentService(
            KnowledgeBaseRepository repository,
            ContentBlockService blocks,
            StationRepository stationRepository,
            KbFileStorageService fileStorage,
            KbSearchService searchService) {
        this.repository = repository;
        this.blocks = blocks;
        this.stationRepository = stationRepository;
        this.fileStorage = fileStorage;
        this.searchService = searchService;
    }

    /**
     * Reads the stored text body of a file.
     *
     * @param fileId the file to read
     * @return the body, or empty when the file has none
     */
    public Optional<String> getMarkdownContent(int fileId) {
        return repository.readTextContent(fileId);
    }

    /**
     * Renders markdown to sanitised HTML that is safe to embed in a page.
     *
     * @param markdown the markdown source
     * @return the rendered HTML
     */
    public String renderMarkdown(String markdown) {
        return Markdown.toHtml(markdown);
    }

    /**
     * Reads the stored binary payload of an uploaded file.
     *
     * @param fileId the file to read
     * @return the payload, or empty when nothing is stored
     */
    public Optional<byte[]> getFileContent(int fileId) {
        return readStored(fileId).map(KbFileStorageService.FileData::data);
    }

    /**
     * Reads the MIME type the binary payload of an uploaded file was stored under.
     *
     * @param fileId the file to read
     * @return the MIME type, or empty when nothing is stored
     */
    public Optional<String> getFileContentType(int fileId) {
        return readStored(fileId).map(KbFileStorageService.FileData::contentType);
    }

    /**
     * Stores the first version of a markdown file: the full text, a version-one entry holding that
     * same text, and the search index.
     *
     * @param fileId    the freshly created file
     * @param content   the initial body
     * @param createdBy the authoring member
     */
    public void initialiseMarkdown(int fileId, String content, int createdBy) {
        repository.storeTextContent(fileId, content);
        repository.createVersion(fileId, content, true, 1, createdBy);
        searchService.reindex(fileId, content);
    }

    /**
     * Stores a text body verbatim and refreshes the file's search index.
     *
     * @param fileId the file to store for
     * @param text   the body to store
     */
    public void storeText(int fileId, String text) {
        repository.storeTextContent(fileId, text);
        searchService.reindex(fileId, text);
    }

    /**
     * Stores text that was extracted from a binary payload. Nothing is stored when the extraction
     * yielded no usable text, but the search index is refreshed either way so the file still
     * becomes findable by its title and description.
     *
     * @param fileId the file to store for
     * @param text   the extracted text, possibly {@code null}
     */
    public void storeExtractedText(int fileId, String text) {
        if (text != null && !text.isBlank()) {
            storeText(fileId, text);
            return;
        }
        searchService.reindex(fileId, text);
    }

    /**
     * Replaces the body of a file and records the change as a new version. The version holds only
     * the diff against the previous body.
     *
     * @param fileId     the file to update
     * @param newContent the new body
     * @param updatedBy  the editing member
     */
    public void updateMarkdownContent(int fileId, String newContent, int updatedBy) {
        String oldContent = repository.readTextContent(fileId).orElse("");
        String patch = TextDiff.createPatch(oldContent, newContent);

        repository.storeTextContent(fileId, newContent);
        int nextVersion = repository.getNextVersion(fileId);
        repository.createVersion(fileId, patch, false, nextVersion, updatedBy);
        searchService.reindex(fileId, newContent);
        log.info("KB file {} content updated to version {} by member {}", fileId, nextVersion, updatedBy);
    }

    // --- Blocks ---

    /**
     * Turns a plain markdown article into one built from blocks, putting what the author already
     * wrote into a single markdown block.
     *
     * <p>The switch is one way, and the file type stays {@code MARKDOWN}: a rich article is still
     * an article, and a new type would ripple through detection, icons, exportability and every
     * switch on the type for no gain.
     */
    public Optional<KbFile> switchToRich(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        if (file.contentMode() == ContentMode.RICH) return Optional.of(file);
        if (file.fileType() != KbFileType.MARKDOWN) {
            throw new BadRequestResponse("Only a markdown article can be built from blocks");
        }

        var container = blocks.create(file.stationId());
        String existing = repository.readTextContent(fileId).orElse("");
        if (!existing.isBlank()) {
            blocks.save(
                    container.id(),
                    List.of(new ContentBlockService.RowData(
                            0,
                            List.of(new ContentBlockService.CellData(
                                    0, 100.0, CellContentType.MARKDOWN, existing, CellConfig.EMPTY)))),
                    ContentBlockService.Scope.ARTICLE);
        }
        if (!repository.setRichMode(fileId, container.id())) {
            blocks.delete(container.id());
            return Optional.empty();
        }
        log.info("KB file {} switched to rich mode with container {}", fileId, container.id());
        return repository.findFileById(fileId);
    }

    /**
     * The blocks a rich article is built from, in reading order.
     */
    public List<ContentRow> loadBlocks(KbFile file) {
        if (file.containerId() == null) return List.of();
        return blocks.loadRows(file.containerId());
    }

    /**
     * Saves the blocks of a rich article and stores the projection of them as the article's body.
     *
     * <p>It goes through the same store as a hand-written body, so the search index, the version
     * history and the PDF export stay exactly where they are and need to learn nothing about
     * blocks. That is the whole point of storing the projection.
     */
    public Optional<KbFile> saveBlocks(int fileId, List<ContentBlockService.RowData> rows, int updatedBy) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        if (file.contentMode() != ContentMode.RICH || file.containerId() == null) {
            throw new BadRequestResponse("This article is not built from blocks");
        }

        blocks.save(file.containerId(), rows, ContentBlockService.Scope.ARTICLE);

        var stationUid = stationRepository.resolveUid(file.stationId());
        String markdown = ContentProjection.toMarkdown(
                blocks.loadRows(file.containerId()), hash -> "/api/v1/public/media/" + stationUid + "/" + hash);
        updateMarkdownContent(fileId, markdown, updatedBy);
        return repository.findFileById(fileId);
    }

    /**
     * Deletes the blocks of an article. The container is the owned side, so whatever deletes the
     * article has to say so.
     */
    public void deleteBlocks(KbFile file) {
        blocks.delete(file.containerId());
        log.info("Dropped the blocks of knowledge file {}", file.id());
    }

    /**
     * Lists the version history of a file.
     *
     * @param fileId the file to list for
     * @return the versions
     */
    public List<KbFileVersion> findVersions(int fileId) {
        return repository.findVersions(fileId);
    }

    /**
     * Reads a single version entry of a file.
     *
     * @param fileId  the file the version belongs to
     * @param version the version number
     * @return the version, or empty when it does not exist
     */
    public Optional<KbFileVersion> findVersion(int fileId, int version) {
        return repository.findVersion(fileId, version);
    }

    /**
     * Reconstructs the body a file had at a given version by replaying the stored patches from the
     * first full version up to the target.
     *
     * @param fileId        the file to reconstruct
     * @param targetVersion the version to stop at
     * @return the reconstructed body, or empty when the file has no versions
     */
    public Optional<String> reconstructVersion(int fileId, int targetVersion) {
        var allVersions = repository.findVersions(fileId);
        allVersions.sort(Comparator.comparingInt(KbFileVersion::version));

        String content = null;
        for (var version : allVersions) {
            if (version.version() > targetVersion) break;
            if (version.isFull()) {
                content = version.patch();
            } else if (content != null) {
                content = TextDiff.applyPatch(content, version.patch());
            }
        }
        return Optional.ofNullable(content);
    }

    /**
     * Restores the body a file had at a given version, recording the restore as a new version.
     * Versions that cannot be reconstructed leave the file untouched.
     *
     * @param fileId     the file to revert
     * @param version    the version to restore
     * @param revertedBy the member performing the revert
     */
    public void revertToVersion(int fileId, int version, int revertedBy) {
        var reconstructed = reconstructVersion(fileId, version);
        if (reconstructed.isEmpty()) return;
        updateMarkdownContent(fileId, reconstructed.get(), revertedBy);
    }

    private Optional<KbFileStorageService.FileData> readStored(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        return fileStorage.read(file.stationId(), fileId);
    }
}
