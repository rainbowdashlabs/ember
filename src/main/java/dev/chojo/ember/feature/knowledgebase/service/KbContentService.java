/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.util.HtmlSanitizer;
import dev.chojo.ember.util.TextDiff;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
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
    private final KbFileStorageService fileStorage;
    private final KbSearchService searchService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public KbContentService(
            KnowledgeBaseRepository repository, KbFileStorageService fileStorage, KbSearchService searchService) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.searchService = searchService;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer =
                HtmlRenderer.builder().extensions(extensions).sanitizeUrls(true).build();
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
        var document = markdownParser.parse(markdown);
        return HtmlSanitizer.sanitize(htmlRenderer.render(document), HtmlSanitizer.Policy.RICH);
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
