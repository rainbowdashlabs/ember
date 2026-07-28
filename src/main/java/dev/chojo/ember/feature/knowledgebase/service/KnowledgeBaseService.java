/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.entity.UrlMetadata;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import dev.chojo.ember.feature.storage.service.PresentationCompressor;
import dev.chojo.ember.util.HtmlSanitizer;
import dev.chojo.ember.util.PresentationConverter;
import dev.chojo.ember.util.TextDiff;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");
    private static final Pattern MENTION_PATTERN_LEGACY = Pattern.compile("@\\[(\\d+):([^\\]]+)]");
    private static final Pattern BULK_MENTION_PATTERN =
            Pattern.compile("@\\[(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):(\\d+)]");
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESC_PATTERN = Pattern.compile(
            "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESC_PATTERN_ALT = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']description[\"']", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> LOCALE_TO_TS_CONFIG = Map.of(
            "de", "german",
            "en", "english",
            "fr", "french",
            "es", "spanish",
            "it", "italian",
            "nl", "dutch",
            "pt", "portuguese",
            "ru", "russian");
    private static final Set<String> PRESENTATION_MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.oasis.opendocument.presentation");
    private final KnowledgeBaseRepository repository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final KbFileStorageService fileStorage;
    private final KbCommentRepository kbCommentRepository;
    private final MemberIdentityFactory memberIdentityFactory;
    private final DomainEventBus eventBus;
    private final StationMemberService stationMemberService;
    private final PresentationCompressor officeCompressor;
    private final PdfCompressor pdfCompressor;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public KnowledgeBaseService(
            KnowledgeBaseRepository repository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            KbFileStorageService fileStorage,
            KbCommentRepository kbCommentRepository,
            MemberIdentityFactory memberIdentityFactory,
            DomainEventBus eventBus,
            StationMemberService stationMemberService,
            PresentationCompressor officeCompressor,
            PdfCompressor pdfCompressor) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.fileStorage = fileStorage;
        this.kbCommentRepository = kbCommentRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.eventBus = eventBus;
        this.stationMemberService = stationMemberService;
        this.officeCompressor = officeCompressor;
        this.pdfCompressor = pdfCompressor;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer =
                HtmlRenderer.builder().extensions(extensions).sanitizeUrls(true).build();
    }

    private static String extractJsonString(String json, String key) {
        var pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        return repository.findFolders(stationId, parentId);
    }

    public Optional<KbFolder> findFolder(int id) {
        return repository.findFolderById(id);
    }

    public KbFolder createFolder(int stationId, Integer parentId, String name, String description, int createdBy) {
        var folder = repository.createFolder(stationId, parentId, name, description, createdBy);
        log.info("KB folder {} created in station {} by member {}", folder.id(), stationId, createdBy);
        return folder;
    }

    // -- Files --

    public boolean updateFolder(int id, String name, String description, String iconUrl, int position) {
        boolean updated = repository.updateFolder(id, name, description, iconUrl, position);
        if (updated) {
            log.info("KB folder {} updated", id);
        } else {
            log.warn("KB folder {} update matched no rows", id);
        }
        return updated;
    }

    public boolean deleteFolder(int id) {
        boolean deleted = repository.deleteFolder(id);
        if (deleted) {
            log.info("KB folder {} deleted", id);
        } else {
            log.warn("KB folder {} delete matched no rows", id);
        }
        return deleted;
    }

    public List<KbFile> findFiles(int stationId, Integer folderId) {
        return repository.findFiles(stationId, folderId);
    }

    public List<KbFile> findAllPublicFiles(int stationId, PublicKbMode mode) {
        if (mode == PublicKbMode.OFF) return List.of();
        return repository.findAllFiles(stationId).stream()
                .filter(f -> isPubliclyVisible(mode, null, f.id()))
                .toList();
    }

    public Optional<KbFile> findFile(int id) {
        return repository.findFileById(id);
    }

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
        repository.storeTextContent(file.id(), content);
        // First version stores full content
        repository.createVersion(file.id(), content, true, 1, createdBy);
        updateSearchIndex(file.id(), content);
        log.info("KB markdown file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    public KbFile createYoutubeFile(
            int stationId, Integer folderId, String name, String description, String youtubeUrl, int createdBy) {
        var file = repository.createFile(
                stationId, folderId, name, description, KbFileType.YOUTUBE, null, 0, youtubeUrl, createdBy);
        // Fetch YouTube metadata for search indexing
        String metaText = fetchYoutubeMetadata(youtubeUrl);
        if (metaText != null && !metaText.isBlank()) {
            repository.storeTextContent(file.id(), metaText);
        }
        updateSearchIndex(file.id(), metaText);
        log.info("KB YouTube file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    public KbFile createUploadedFile(
            int stationId,
            Integer folderId,
            String name,
            String description,
            byte[] data,
            String mimeType,
            int createdBy) {
        KbFileType fileType = detectFileType(mimeType, name);
        byte[] payload = compressForStorage(data, mimeType);
        var file = repository.createFile(
                stationId, folderId, name, description, fileType, mimeType, payload.length, null, createdBy);
        if (fileType == KbFileType.TEXT) {
            String text = new String(payload);
            repository.storeTextContent(file.id(), text);
            updateSearchIndex(file.id(), text);
        } else if (fileType == KbFileType.PDF) {
            storeBinaryFile(stationId, file.id(), payload, mimeType);
            String pdfText = extractPdfText(payload);
            if (pdfText != null && !pdfText.isBlank()) {
                repository.storeTextContent(file.id(), pdfText);
            }
            updateSearchIndex(file.id(), pdfText);
        } else if (fileType == KbFileType.PRESENTATION) {
            storeBinaryFile(stationId, file.id(), payload, mimeType);
            repository.updateConversionStatus(file.id(), ConversionStatus.PENDING);
            triggerPresentationConversion(stationId, file.id(), payload, name);
            updateSearchIndex(file.id(), null);
        } else {
            storeBinaryFile(stationId, file.id(), payload, mimeType);
            updateSearchIndex(file.id(), null);
        }
        log.info(
                "KB file {} created in station {} by member {} (type {}, {} bytes)",
                file.id(),
                stationId,
                createdBy,
                fileType,
                payload.length);
        return file;
    }

    public KbFile createLinkFile(
            int stationId, Integer folderId, String name, String description, String linkUrl, int createdBy) {
        // Try to auto-populate name/description from URL metadata
        if ((name == null || name.isBlank()) || (description == null || description.isBlank())) {
            var metadata = fetchUrlMetadata(linkUrl);
            if (name == null || name.isBlank()) {
                name = metadata.title() != null ? metadata.title() : linkUrl;
            }
            if (description == null || description.isBlank()) {
                description = metadata.description() != null ? metadata.description() : "";
            }
        }
        var file = repository.createFile(
                stationId, folderId, name, description, KbFileType.LINK, null, 0, null, linkUrl, createdBy);
        // Store metadata as text content for search snippets
        String metaText = (name != null ? name : "") + " " + description + " " + linkUrl;
        repository.storeTextContent(file.id(), metaText.trim());
        updateSearchIndex(file.id(), metaText.trim());
        log.info("KB link file {} created in station {} by member {}", file.id(), stationId, createdBy);
        return file;
    }

    public UrlMetadata fetchUrlMetadata(String url) {
        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (compatible; EmberBot/1.0)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body == null || body.isBlank()) return new UrlMetadata(null, null);

            // Only look at the first 10KB to avoid huge pages
            if (body.length() > 10_000) body = body.substring(0, 10_000);

            String title = null;
            Matcher titleMatcher = TITLE_PATTERN.matcher(body);
            if (titleMatcher.find()) {
                title = titleMatcher.group(1).trim();
            }

            String desc = null;
            Matcher descMatcher = META_DESC_PATTERN.matcher(body);
            if (descMatcher.find()) {
                desc = descMatcher.group(1).trim();
            } else {
                Matcher descMatcher2 = META_DESC_PATTERN_ALT.matcher(body);
                if (descMatcher2.find()) {
                    desc = descMatcher2.group(1).trim();
                }
            }

            return new UrlMetadata(title, desc);
        } catch (Exception e) {
            log.debug("Failed to fetch URL metadata for {}: {}", url, e.getMessage());
            return new UrlMetadata(null, null);
        }
    }

    public List<KbAccessRestriction> findRestrictions(Integer folderId, Integer fileId) {
        return repository.findRestrictions(folderId, fileId);
    }

    // -- Access Restrictions --

    public void setRestrictions(Integer folderId, Integer fileId, RestrictionSelection selection) {
        repository.clearRestrictions(folderId, fileId);
        for (StationUserType userType : selection.userTypes()) {
            repository.addRestriction(folderId, fileId, userType, null, null, null);
        }
        for (Integer groupId : selection.groupIds()) {
            repository.addRestriction(folderId, fileId, null, groupId, null, null);
        }
        for (Integer tagId : selection.tagIds()) {
            repository.addRestriction(folderId, fileId, null, null, tagId, null);
        }
        for (Integer memberId : selection.memberIds()) {
            repository.addRestriction(folderId, fileId, null, null, null, memberId);
        }
    }

    public boolean canAccess(
            int memberId,
            Integer folderId,
            Integer fileId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        // Check file/folder restrictions
        var rawRestrictions = repository.findRestrictions(folderId, fileId);
        if (!rawRestrictions.isEmpty()) {
            // Determine restriction mode from the entity
            RestrictionMode mode = RestrictionMode.AND;
            if (fileId != null) {
                var file = repository.findFileById(fileId);
                if (file.isPresent() && file.get().restrictionMode() != null)
                    mode = file.get().restrictionMode();
            } else if (folderId != null) {
                var folder = repository.findFolderById(folderId);
                if (folder.isPresent() && folder.get().restrictionMode() != null)
                    mode = folder.get().restrictionMode();
            }
            var restrictions = toRestrictionSet(rawRestrictions, mode);
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        // For files, also check parent folder restrictions (inherited)
        if (fileId != null) {
            var file = repository.findFileById(fileId);
            if (file.isPresent() && file.get().folderId() != null) {
                return canAccessFolder(memberId, file.get().folderId(), memberUserType, memberGroupIds, memberTagIds);
            }
        }

        // For folders, check parent folder restrictions (inherited)
        if (folderId != null) {
            return canAccessFolder(memberId, folderId, memberUserType, memberGroupIds, memberTagIds);
        }

        return true;
    }

    public boolean updateFile(int id, String name, String description, String iconUrl, int position) {
        boolean updated = repository.updateFile(id, name, description, iconUrl, position);
        if (updated) {
            log.info("KB file {} updated", id);
        } else {
            log.warn("KB file {} update matched no rows", id);
        }
        return updated;
    }

    public void setSourceReference(int fileId, int sourceFileId, int sourceStationId) {
        repository.setSourceReference(fileId, sourceFileId, sourceStationId);
    }

    public boolean deleteFile(int id) {
        repository.findFileById(id).ifPresent(f -> fileStorage.delete(f.stationId(), id));
        boolean deleted = repository.deleteFile(id);
        if (deleted) {
            log.info("KB file {} deleted", id);
        } else {
            log.warn("KB file {} delete matched no rows", id);
        }
        return deleted;
    }

    /**
     * Checks if a folder or file is publicly visible based on the station's public KB mode.
     * Items with access restrictions are never public.
     * In ALLOW_ALL mode: public unless explicitly opted out.
     * In DENY_ALL mode: not public unless explicitly opted in.
     * Folder visibility is inherited by child items unless overridden.
     */
    public boolean isPubliclyVisible(PublicKbMode mode, Integer folderId, Integer fileId) {
        if (mode == PublicKbMode.OFF) return false;

        // Items with access restrictions are never public
        if (repository.hasRestrictions(folderId, fileId)) return false;

        // For files, also check parent folder restrictions
        if (fileId != null) {
            var file = repository.findFileById(fileId).orElse(null);
            if (file != null && file.folderId() != null) {
                if (!isPubliclyVisible(mode, file.folderId(), null)) return false;
            }
        }

        // For folders, check parent folder restrictions recursively
        if (folderId != null) {
            var folder = repository.findFolderById(folderId).orElse(null);
            if (folder != null && folder.parentId() != null) {
                if (!isPubliclyVisible(mode, folder.parentId(), null)) return false;
            }
        }

        // Check explicit visibility override
        var override = repository.findPublicVisibility(folderId, fileId);
        return override.orElseGet(() -> mode == PublicKbMode.ALLOW_ALL);

        // Default based on mode
    }

    public void setPublicVisibility(Integer folderId, Integer fileId, boolean visible) {
        repository.setPublicVisibility(folderId, fileId, visible);
    }

    public void removePublicVisibility(Integer folderId, Integer fileId) {
        repository.removePublicVisibility(folderId, fileId);
    }

    // -- Public Visibility --

    public Optional<Boolean> findPublicVisibility(Integer folderId, Integer fileId) {
        return repository.findPublicVisibility(folderId, fileId);
    }

    public void storePresentationResult(int stationId, int fileId, byte[] pdfBytes) {
        try {
            fileStorage.storePresentationPdf(stationId, fileId, pdfBytes);
            String pdfText = extractPdfText(pdfBytes);
            if (pdfText != null && !pdfText.isBlank()) {
                repository.storeTextContent(fileId, pdfText);
            }
            updateSearchIndex(fileId, pdfText);
            repository.updateConversionStatus(fileId, ConversionStatus.SUCCESS);
            log.info("Presentation conversion succeeded for file {}", fileId);
        } catch (Exception e) {
            log.error("Failed to store presentation result for file {}", fileId, e);
            repository.updateConversionStatus(fileId, ConversionStatus.FAILED);
        }
    }

    /**
     * Get the converted PDF content for a presentation file.
     */
    public Optional<byte[]> getPresentationPdf(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        return fileStorage.readPresentationPdf(file.stationId(), fileId).map(KbFileStorageService.FileData::data);
    }

    /**
     * Re-upload a presentation file (replaces original + reconverts PDF).
     */
    public void reuploadPresentation(int fileId, byte[] data, String mimeType, String filename) {
        var file = repository.findFileById(fileId).orElseThrow();
        storeBinaryFile(file.stationId(), fileId, data, mimeType);
        repository.updateConversionStatus(fileId, ConversionStatus.PENDING);
        triggerPresentationConversion(file.stationId(), fileId, data, filename);
        log.info("KB presentation file {} re-uploaded in station {}", fileId, file.stationId());
    }

    public Optional<String> getMarkdownContent(int fileId) {
        return repository.readTextContent(fileId);
    }

    public String renderMarkdown(String markdown) {
        var document = markdownParser.parse(markdown);
        String html = htmlRenderer.render(document);
        return HtmlSanitizer.sanitize(html, HtmlSanitizer.Policy.RICH);
    }

    public Optional<byte[]> getFileContent(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        return fileStorage.read(file.stationId(), fileId).map(KbFileStorageService.FileData::data);
    }

    public Optional<String> getFileContentType(int fileId) {
        var file = repository.findFileById(fileId).orElse(null);
        if (file == null) return Optional.empty();
        return fileStorage.read(file.stationId(), fileId).map(KbFileStorageService.FileData::contentType);
    }

    public void updateMarkdownContent(int fileId, String newContent, int updatedBy) {
        // Get current content to create a diff patch
        String oldContent = repository.readTextContent(fileId).orElse("");
        String patch = TextDiff.createPatch(oldContent, newContent);

        repository.storeTextContent(fileId, newContent);
        int nextVersion = repository.getNextVersion(fileId);
        // Store diff patch (not full content) to save space
        repository.createVersion(fileId, patch, false, nextVersion, updatedBy);
        updateSearchIndex(fileId, newContent);
        log.info("KB file {} content updated to version {} by member {}", fileId, nextVersion, updatedBy);
    }

    public List<KbFileVersion> findVersions(int fileId) {
        return repository.findVersions(fileId);
    }

    // -- Content --

    public Optional<KbFileVersion> findVersion(int fileId, int version) {
        return repository.findVersion(fileId, version);
    }

    /**
     * Reconstructs the content at a specific version by applying patches from version 1 (full) up to the target.
     */
    public Optional<String> reconstructVersion(int fileId, int targetVersion) {
        var allVersions = repository.findVersions(fileId);
        // Sort ascending by version
        allVersions.sort(Comparator.comparingInt(KbFileVersion::version));

        String content = null;
        for (var v : allVersions) {
            if (v.version() > targetVersion) break;
            if (v.isFull()) {
                content = v.patch(); // Full content stored as patch
            } else if (content != null) {
                content = TextDiff.applyPatch(content, v.patch());
            }
        }
        return Optional.ofNullable(content);
    }

    public void revertToVersion(int fileId, int version, int revertedBy) {
        var reconstructed = reconstructVersion(fileId, version);
        if (reconstructed.isEmpty()) return;
        updateMarkdownContent(fileId, reconstructed.get(), revertedBy);
    }

    public List<KbFile> search(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.search(stationId, query, resolveTsConfig(stationId));
    }

    public List<KbSearchResult> searchWithSnippets(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.searchWithSnippets(stationId, query, resolveTsConfig(stationId));
    }

    // -- Versions --

    public List<KbTag> findTagsByStation(int stationId) {
        return repository.findTagsByStation(stationId);
    }

    public List<KbTag> findFileTags(int fileId) {
        return repository.findFileTags(fileId);
    }

    public List<KbFile> findFilesByTag(int stationId, String tagName) {
        return repository.findFilesByTag(stationId, tagName);
    }

    public List<KbFolder> findAllFolders(int stationId) {
        return repository.findAllFolders(stationId);
    }

    // -- Search --

    public List<KbTag> setFileTags(int fileId, List<String> tagNames, int stationId) {
        repository.setFileTags(fileId, tagNames, stationId);
        return repository.findFileTags(fileId);
    }

    public List<KbTag> findFolderTags(int folderId) {
        return repository.findFolderTags(folderId);
    }

    // -- Helpers --

    public List<KbFile> findRelatedFiles(int fileId) {
        return repository.findRelatedFiles(fileId);
    }

    public void setRelatedFiles(int fileId, List<Integer> targetFileIds) {
        repository.setRelatedFiles(fileId, targetFileIds);
    }

    // -- Tags --

    public void addFavourite(int memberId, int fileId) {
        repository.addFavourite(memberId, fileId);
    }

    public boolean removeFavourite(int memberId, int fileId) {
        return repository.removeFavourite(memberId, fileId);
    }

    public List<KbFile> findFavourites(int memberId) {
        return repository.findFavourites(memberId);
    }

    public boolean isFavourite(int memberId, int fileId) {
        return repository.isFavourite(memberId, fileId);
    }

    public List<KbTag> setFolderTags(int folderId, List<String> tagNames, int stationId) {
        repository.setFolderTags(folderId, tagNames, stationId);
        return repository.findFolderTags(folderId);
    }

    public KbComment createComment(
            int stationId, int fileId, Integer parentId, int authorId, String authorName, String content) {
        var identity = memberIdentityFactory.fromMemberId(authorId);
        var comment = kbCommentRepository.create(fileId, parentId, identity, content);
        log.info(
                "KB comment {} created on file {} in station {} by member {}",
                comment.id(),
                fileId,
                stationId,
                authorId);

        var file = findFile(fileId).orElse(null);
        String fileTitle = file != null ? file.name() : "";
        String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;

        Integer parentAuthorId = null;
        if (parentId != null) {
            var parentComment = kbCommentRepository.findById(parentId).orElse(null);
            if (parentComment != null && parentComment.author() != null) {
                parentAuthorId = stationRepository
                        .resolveId(parentComment.author().stationUid())
                        .flatMap(sid -> stationMemberService.resolveId(
                                sid, parentComment.author().memberUid()))
                        .orElse(null);
            }
        }
        eventBus.publish(new CommentCreated(
                stationId,
                CommentEntityType.KB,
                fileId,
                fileTitle,
                comment.id(),
                parentId,
                parentAuthorId,
                authorId,
                authorName,
                preview));

        var matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                var memberUid = UUID.fromString(matcher.group(2));
                stationMemberService.resolveId(stationId, memberUid).ifPresent(mentionedId -> {
                    if (mentionedId != authorId) {
                        eventBus.publish(new MentionedInComment(
                                stationId,
                                mentionedId,
                                authorId,
                                authorName,
                                CommentEntityType.KB,
                                fileId,
                                fileTitle,
                                preview));
                    }
                });
            } catch (IllegalArgumentException ignored) {
            }
        }
        var legacyMatcher = MENTION_PATTERN_LEGACY.matcher(content);
        while (legacyMatcher.find()) {
            int mentionedId = Integer.parseInt(legacyMatcher.group(1));
            if (mentionedId != authorId) {
                eventBus.publish(new MentionedInComment(
                        stationId,
                        mentionedId,
                        authorId,
                        authorName,
                        CommentEntityType.KB,
                        fileId,
                        fileTitle,
                        preview));
            }
        }
        var bulkMatcher = BULK_MENTION_PATTERN.matcher(content);
        while (bulkMatcher.find()) {
            var type = MentionType.valueOf(bulkMatcher.group(1));
            int targetId = Integer.parseInt(bulkMatcher.group(3));
            eventBus.publish(new BulkMentionedInComment(
                    stationId, authorId, authorName, CommentEntityType.KB, fileId, fileTitle, type, targetId, preview));
        }

        return comment;
    }

    /**
     * Deletes a knowledge-base comment and announces the removal with a short content preview.
     *
     * @param stationId the station owning the file the comment belongs to
     * @param commentId the comment to remove
     * @return {@code true} when a comment was removed
     */
    public boolean deleteComment(int stationId, int commentId) {
        var comment = kbCommentRepository.findById(commentId).orElse(null);
        if (comment == null || !kbCommentRepository.delete(commentId)) return false;
        String preview =
                comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
        eventBus.publish(new CommentDeleted(stationId, commentId, preview));
        return true;
    }

    void performPresentationConversion(int stationId, int fileId, byte[] data, String filename) {
        try {
            byte[] pdfBytes = PresentationConverter.toPdf(data, filename);
            storePresentationResult(stationId, fileId, pdfBytes);
        } catch (Exception e) {
            log.error("Presentation conversion failed for file {}", fileId, e);
            repository.updateConversionStatus(fileId, ConversionStatus.FAILED);
        }
    }

    /**
     * Routes a freshly-uploaded blob through the at-rest compressors registered in
     * {@code feature/storage/service}. Office archives and PDFs are
     * recompressed losslessly; everything else is returned untouched. Failures fall back to
     * the original bytes — compression is opportunistic, never a hard requirement.
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

    private String fetchYoutubeMetadata(String youtubeUrl) {
        try {
            String oembedUrl = "https://www.youtube.com/oembed?url="
                    + URLEncoder.encode(youtubeUrl, StandardCharsets.UTF_8)
                    + "&format=json";
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oembedUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                // Simple JSON parsing for title and author_name
                String title = extractJsonString(body, "title");
                String author = extractJsonString(body, "author_name");
                return (title != null ? title : "") + " " + (author != null ? author : "");
            }
        } catch (Exception e) {
            log.debug("Failed to fetch YouTube metadata for {}: {}", youtubeUrl, e.getMessage());
        }
        return null;
    }

    private boolean canAccessFolder(
            int memberId,
            int folderId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var folder = repository.findFolderById(folderId);
        if (folder.isEmpty()) return true;

        var rawRestrictions = repository.findRestrictions(folderId, null);
        if (!rawRestrictions.isEmpty()) {
            RestrictionMode mode =
                    folder.get().restrictionMode() != null ? folder.get().restrictionMode() : RestrictionMode.AND;
            var restrictions = toRestrictionSet(rawRestrictions, mode);
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        // Check parent folder
        if (folder.get().parentId() != null) {
            return canAccessFolder(memberId, folder.get().parentId(), memberUserType, memberGroupIds, memberTagIds);
        }

        return true;
    }

    private RestrictionSet toRestrictionSet(List<KbAccessRestriction> kbRestrictions, RestrictionMode mode) {
        var restrictions = kbRestrictions.stream()
                .map(r -> new Restriction(r.id(), r.userType(), r.groupId(), r.tagId(), r.memberId()))
                .toList();
        return new RestrictionSet(restrictions, mode);
    }

    private void storeBinaryFile(int stationId, int fileId, byte[] data, String contentType) {
        try {
            fileStorage.store(stationId, fileId, data, contentType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store KB file " + fileId + " on disk", e);
        }
    }

    private void triggerPresentationConversion(int stationId, int fileId, byte[] data, String filename) {
        CompletableFuture.runAsync(() -> performPresentationConversion(stationId, fileId, data, filename));
    }

    private void updateSearchIndex(int fileId, String text) {
        var file = repository.findFileById(fileId);
        if (file.isEmpty()) return;
        var f = file.get();

        // Build searchable text: title + description + content
        var sb = new StringBuilder();
        sb.append(f.name()).append(' ');
        if (f.description() != null && !f.description().isBlank()) {
            sb.append(f.description()).append(' ');
        }
        if (text != null && !text.isBlank()) {
            String plain = text.replaceAll("<[^>]+>", " ") // strip HTML tags
                    .replaceAll("[#*_\\[\\]()>`~]", " ") // strip markdown syntax
                    .replaceAll("\\s+", " ")
                    .trim();
            sb.append(plain);
        }

        String combined = sb.toString().trim();
        if (!combined.isBlank()) {
            repository.updateSearchIndex(fileId, combined, resolveTsConfig(f.stationId()));
        }
    }

    private String resolveTsConfig(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(station -> {
                    String locale = station.locale();
                    if (locale == null || locale.isBlank()) return "simple";
                    String lang = locale.contains("-") ? locale.substring(0, locale.indexOf('-')) : locale;
                    return LOCALE_TO_TS_CONFIG.getOrDefault(lang.toLowerCase(), "simple");
                })
                .orElse("simple");
    }

    private String extractPdfText(byte[] data) {
        try (var document = Loader.loadPDF(data)) {
            var stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("Failed to extract text from PDF: {}", e.getMessage());
            return null;
        }
    }

    private KbFileType detectFileType(String mimeType, String filename) {
        if (mimeType != null) {
            if (PRESENTATION_MIME_TYPES.contains(mimeType)) return KbFileType.PRESENTATION;
            if (mimeType.equals("application/pdf")) return KbFileType.PDF;
            if (mimeType.startsWith("image/")) return KbFileType.IMAGE;
            if (mimeType.equals("text/markdown") || filename.endsWith(".md")) return KbFileType.MARKDOWN;
            if (mimeType.startsWith("text/")) return KbFileType.TEXT;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".pptx") || lower.endsWith(".ppt") || lower.endsWith(".odp"))
                return KbFileType.PRESENTATION;
            if (lower.endsWith(".pdf")) return KbFileType.PDF;
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) return KbFileType.MARKDOWN;
            if (lower.endsWith(".txt")) return KbFileType.TEXT;
            if (lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg")
                    || lower.endsWith(".gif")
                    || lower.endsWith(".webp")
                    || lower.endsWith(".svg")) return KbFileType.IMAGE;
        }
        return KbFileType.OTHER;
    }

    /**
     * Resolves the name shown for a member on knowledge-base content, preferring their station
     * display name over the account name and falling back to a placeholder when neither resolves.
     */
    public String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(member -> {
                    if (member.displayName() != null && !member.displayName().isBlank()) {
                        return member.displayName();
                    }
                    if (member.accountId() != null) {
                        return accountRepository
                                .findById(member.accountId())
                                .map(Account::fullName)
                                .orElse("Unbekannt");
                    }
                    return "Unbekannt";
                })
                .orElse("Unbekannt");
    }

    /**
     * Reads the group and user-tag memberships an access check needs, so a whole listing can be
     * filtered against {@link #canAccess(MemberAccess, Integer, Integer)} without re-reading them
     * per row.
     *
     * @param memberId the member whose access is evaluated
     * @param userType the member's station user type
     * @return the member's access context
     */
    public MemberAccess memberAccess(int memberId, StationUserType userType) {
        var groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        var tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(UserTag::id)
                .toList();
        return new MemberAccess(memberId, userType, groupIds, tagIds);
    }

    /**
     * Evaluates the access restrictions on a folder or file against a pre-read access context.
     */
    public boolean canAccess(MemberAccess access, Integer folderId, Integer fileId) {
        return canAccess(access.memberId(), folderId, fileId, access.userType(), access.groupIds(), access.tagIds());
    }

    /**
     * The memberships an access restriction is evaluated against, read once per request.
     */
    public record MemberAccess(int memberId, StationUserType userType, List<Integer> groupIds, List<Integer> tagIds) {}
}
