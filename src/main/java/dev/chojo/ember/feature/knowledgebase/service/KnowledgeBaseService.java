/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbSearchResult;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.entity.UrlMetadata;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class KnowledgeBaseService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);
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

    private final KnowledgeBaseRepository repository;
    private final StationRepository stationRepository;
    private final KbFileStorageService fileStorage;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public KnowledgeBaseService(
            KnowledgeBaseRepository repository,
            StationRepository stationRepository,
            KbFileStorageService fileStorage,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.fileStorage = fileStorage;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    // -- Folders --

    public List<KbFolder> findFolders(int stationId, Integer parentId) {
        return repository.findFolders(stationId, parentId);
    }

    public Optional<KbFolder> findFolder(int id) {
        return repository.findFolderById(id);
    }

    public KbFolder createFolder(int stationId, Integer parentId, String name, String description, int createdBy) {
        return repository.createFolder(stationId, parentId, name, description, createdBy);
    }

    public boolean updateFolder(int id, String name, String description, String iconUrl, int position) {
        return repository.updateFolder(id, name, description, iconUrl, position);
    }

    public boolean deleteFolder(int id) {
        return repository.deleteFolder(id);
    }

    // -- Files --

    public List<KbFile> findFiles(int stationId, Integer folderId) {
        return repository.findFiles(stationId, folderId);
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
        var file = repository.createFile(
                stationId, folderId, name, description, fileType, mimeType, data.length, null, createdBy);
        if (fileType == KbFileType.TEXT) {
            String text = new String(data);
            repository.storeTextContent(file.id(), text);
            updateSearchIndex(file.id(), text);
        } else if (fileType == KbFileType.PDF) {
            storeBinaryFile(file.id(), data, mimeType);
            String pdfText = extractPdfText(data);
            if (pdfText != null && !pdfText.isBlank()) {
                repository.storeTextContent(file.id(), pdfText);
            }
            updateSearchIndex(file.id(), pdfText);
        } else {
            storeBinaryFile(file.id(), data, mimeType);
            updateSearchIndex(file.id(), null);
        }
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
        String metaText = (name != null ? name : "") + " " + (description != null ? description : "") + " " + linkUrl;
        repository.storeTextContent(file.id(), metaText.trim());
        updateSearchIndex(file.id(), metaText.trim());
        return file;
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

    private static String extractJsonString(String json, String key) {
        var pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        var matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
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

    // -- Access Restrictions --

    public List<KbAccessRestriction> findRestrictions(Integer folderId, Integer fileId) {
        return repository.findRestrictions(folderId, fileId);
    }

    public void setRestrictions(
            Integer folderId,
            Integer fileId,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        repository.clearRestrictions(folderId, fileId);
        for (Integer roleId : roleIds) {
            repository.addRestriction(folderId, fileId, roleId, null, null, null);
        }
        for (Integer groupId : groupIds) {
            repository.addRestriction(folderId, fileId, null, groupId, null, null);
        }
        for (Integer tagId : tagIds) {
            repository.addRestriction(folderId, fileId, null, null, tagId, null);
        }
        for (Integer memberId : memberIds) {
            repository.addRestriction(folderId, fileId, null, null, null, memberId);
        }
    }

    public boolean canAccess(
            int memberId,
            Integer folderId,
            Integer fileId,
            List<Integer> memberRoleIds,
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
            if (!restrictions.matches(memberRoleIds, memberGroupIds, memberTagIds, memberId)) return false;
        }

        // For files, also check parent folder restrictions (inherited)
        if (fileId != null) {
            var file = repository.findFileById(fileId);
            if (file.isPresent() && file.get().folderId() != null) {
                return canAccessFolder(memberId, file.get().folderId(), memberRoleIds, memberGroupIds, memberTagIds);
            }
        }

        // For folders, check parent folder restrictions (inherited)
        if (folderId != null) {
            return canAccessFolder(memberId, folderId, memberRoleIds, memberGroupIds, memberTagIds);
        }

        return true;
    }

    private boolean canAccessFolder(
            int memberId,
            int folderId,
            List<Integer> memberRoleIds,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var folder = repository.findFolderById(folderId);
        if (folder.isEmpty()) return true;

        var rawRestrictions = repository.findRestrictions(folderId, null);
        if (!rawRestrictions.isEmpty()) {
            RestrictionMode mode =
                    folder.get().restrictionMode() != null ? folder.get().restrictionMode() : RestrictionMode.AND;
            var restrictions = toRestrictionSet(rawRestrictions, mode);
            if (!restrictions.matches(memberRoleIds, memberGroupIds, memberTagIds, memberId)) return false;
        }

        // Check parent folder
        if (folder.get().parentId() != null) {
            return canAccessFolder(memberId, folder.get().parentId(), memberRoleIds, memberGroupIds, memberTagIds);
        }

        return true;
    }

    private RestrictionSet toRestrictionSet(List<KbAccessRestriction> kbRestrictions, RestrictionMode mode) {
        var restrictions = kbRestrictions.stream()
                .map(r -> new Restriction(r.id(), r.roleId(), r.groupId(), r.tagId(), r.memberId()))
                .toList();
        return new RestrictionSet(restrictions, mode);
    }

    public boolean updateFile(int id, String name, String description, String iconUrl, int position) {
        return repository.updateFile(id, name, description, iconUrl, position);
    }

    public void setSourceReference(int fileId, int sourceFileId, int sourceStationId) {
        repository.setSourceReference(fileId, sourceFileId, sourceStationId);
    }

    public boolean deleteFile(int id) {
        fileStorage.delete(id);
        return repository.deleteFile(id);
    }

    // -- Public Visibility --

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

    public Optional<Boolean> findPublicVisibility(Integer folderId, Integer fileId) {
        return repository.findPublicVisibility(folderId, fileId);
    }

    private void storeBinaryFile(int fileId, byte[] data, String contentType) {
        try {
            fileStorage.store(fileId, data, contentType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store KB file " + fileId + " on disk", e);
        }
    }

    // -- Content --

    public Optional<String> getMarkdownContent(int fileId) {
        return repository.readTextContent(fileId);
    }

    public String renderMarkdown(String markdown) {
        var document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }

    public Optional<byte[]> getFileContent(int fileId) {
        return fileStorage.read(fileId).map(KbFileStorageService.FileData::data);
    }

    public Optional<String> getFileContentType(int fileId) {
        var diskData = fileStorage.read(fileId);
        return diskData.map(KbFileStorageService.FileData::contentType);
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
    }

    // -- Versions --

    public List<KbFileVersion> findVersions(int fileId) {
        return repository.findVersions(fileId);
    }

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

    // -- Search --

    public List<KbFile> search(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.search(stationId, query, resolveTsConfig(stationId));
    }

    public List<KbSearchResult> searchWithSnippets(int stationId, String query) {
        if (query == null || query.isBlank()) return List.of();
        return repository.searchWithSnippets(stationId, query, resolveTsConfig(stationId));
    }

    // -- Helpers --

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

    // -- Tags --

    public List<KbTag> findTagsByStation(int stationId) {
        return repository.findTagsByStation(stationId);
    }

    public List<KbTag> findFileTags(int fileId) {
        return repository.findFileTags(fileId);
    }

    public List<KbFile> findFilesByTag(int stationId, String tagName) {
        return repository.findFilesByTag(stationId, tagName);
    }

    public List<KbTag> setFileTags(int fileId, List<String> tagNames, int stationId) {
        repository.setFileTags(fileId, tagNames, stationId);
        return repository.findFileTags(fileId);
    }

    public List<KbTag> findFolderTags(int folderId) {
        return repository.findFolderTags(folderId);
    }

    // -- Related Files --

    public List<KbFile> findRelatedFiles(int fileId) {
        return repository.findRelatedFiles(fileId);
    }

    public void setRelatedFiles(int fileId, List<Integer> targetFileIds) {
        repository.setRelatedFiles(fileId, targetFileIds);
    }

    // -- Favourites --

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
            if (mimeType.equals("application/pdf")) return KbFileType.PDF;
            if (mimeType.startsWith("image/")) return KbFileType.IMAGE;
            if (mimeType.equals("text/markdown") || filename.endsWith(".md")) return KbFileType.MARKDOWN;
            if (mimeType.startsWith("text/")) return KbFileType.TEXT;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
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

    // -- Federated KB --

    public List<SharedKbItem> browseSharedKb(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<SharedKbItem>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);

            futures.add(CompletableFuture.supplyAsync(() -> {
                var items = new ArrayList<SharedKbItem>();
                if (partner.isRemote()) {
                    browseSharedKbViaHttp(stationId, partner, remoteStationId, items);
                } else {
                    browseSharedKbDirect(remoteStationId, partner, items);
                }
                return items;
            }));
        }
        return collectResults(futures);
    }

    private void browseSharedKbDirect(int remoteStationId, FederationPartner partner, List<SharedKbItem> result) {
        var shares = federationRepository.findKbShares(remoteStationId);
        for (var share : shares) {
            if (share.fileId() != null) {
                findFile(share.fileId()).ifPresent(file -> {
                    var summary = KbFileSummary.of(file);
                    result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.KB, file.id(), file.name(), file.description());
                });
            } else if (share.folderId() != null) {
                for (var file : findFiles(remoteStationId, share.folderId())) {
                    var summary = KbFileSummary.of(file);
                    result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
                    federationRepository.upsertMetadataCache(
                            partner.id(), ContentType.KB, file.id(), file.name(), file.description());
                }
            }
        }
    }

    private void browseSharedKbViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId, List<SharedKbItem> result) {
        var files = federationHttpClient.fetchSharedKbFiles(
                partner.remoteHost(), localStationId, getPrivateKey(localStationId));
        for (var remoteFile : files) {
            var summary = new KbFileSummary(
                    remoteFile.id(),
                    remoteStationId,
                    null,
                    remoteFile.name(),
                    remoteFile.description(),
                    KbFileType.valueOf(remoteFile.fileType() != null ? remoteFile.fileType() : "MARKDOWN"),
                    Instant.now(),
                    false);
            result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(), ContentType.KB, remoteFile.id(), remoteFile.name(), remoteFile.description());
        }
    }

    public List<FederatedSearchResult> searchFederatedKb(int stationId, String query) {
        var futures = new ArrayList<CompletableFuture<List<FederatedSearchResult>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.KB_SHARE, Direction.IMPORT)) continue;
            int remoteStationId = resolvePartnerStationId(partner);
            String stationName = stationRepository
                    .findByUid(partner.partnerStationId())
                    .map(Station::name)
                    .orElse("?");
            String stationUid = partner.partnerStationId().toString();

            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return searchKbViaHttp(stationId, partner, remoteStationId, stationName, stationUid, query);
                } else {
                    return searchKbDirect(remoteStationId, stationName, stationUid, query);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<FederatedSearchResult> searchKbDirect(
            int remoteStationId, String stationName, String stationUid, String query) {
        return searchWithSnippets(remoteStationId, query).stream()
                .map(r -> new FederatedSearchResult(KbFileSummary.of(r.file()), r.snippet(), stationName, stationUid))
                .toList();
    }

    private List<FederatedSearchResult> searchKbViaHttp(
            int localStationId,
            FederationPartner partner,
            int remoteStationId,
            String stationName,
            String stationUid,
            String query) {
        String privateKey = getPrivateKey(localStationId);
        if (privateKey == null) return List.of();
        var results = federationHttpClient.searchKb(partner.remoteHost(), localStationId, privateKey, query);
        return results.stream()
                .map(r -> new FederatedSearchResult(
                        new KbFileSummary(
                                r.id(), remoteStationId, null, r.name(), r.description(), null, Instant.now(), false),
                        r.snippet(),
                        stationName,
                        stationUid))
                .toList();
    }

    /**
     * Fetches a single KB file from a federated partner, transparently handling local/remote.
     */
    public KbFile getFederatedKbFile(int localStationId, UUID partnerStationUid, int fileId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            String json = federationHttpClient.signedGetJson(
                    partner.remoteHost(), "/remote/kb/files/" + fileId, localStationId, getPrivateKey(localStationId));
            if (json == null) throw new IllegalStateException("Failed to fetch file from remote partner");
            try {
                return federationHttpClient.getMapper().readValue(json, KbFile.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote KB file response", e);
            }
        }
        var file = findFile(fileId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (file.stationId() != partnerStationId) {
            throw new IllegalArgumentException("File does not belong to this partner");
        }
        return file;
    }

    /**
     * Fetches KB file content from a federated partner, transparently handling local/remote.
     */
    public String getFederatedKbFileContent(int localStationId, UUID partnerStationUid, int fileId) {
        var partner = resolveActivePartner(localStationId, partnerStationUid);
        if (partner.isRemote()) {
            return federationHttpClient.fetchKbFileContent(
                    partner.remoteHost(), fileId, localStationId, getPrivateKey(localStationId));
        }
        var file = findFile(fileId).orElseThrow();
        int partnerStationId = resolvePartnerStationId(partner);
        if (file.stationId() != partnerStationId) {
            throw new IllegalArgumentException("File does not belong to this partner");
        }
        return getMarkdownContent(fileId).orElse("");
    }

    public KbFile copyKbFile(int fileId, int targetStationId, int createdBy) {
        var source = findFile(fileId).orElseThrow();
        String content;
        var partner = findPartnerForStation(targetStationId, source.stationId());
        if (partner != null && partner.isRemote()) {
            content = federationHttpClient.fetchKbFileContent(
                    partner.remoteHost(), fileId, targetStationId, getPrivateKey(targetStationId));
        } else {
            content = getMarkdownContent(fileId).orElse("");
        }
        var copied = createMarkdownFile(targetStationId, null, source.name(), source.description(), content, createdBy);
        setSourceReference(copied.id(), source.id(), source.stationId());
        if (isFavourite(createdBy, fileId)) {
            addFavourite(createdBy, copied.id());
        }
        return findFile(copied.id()).orElseThrow();
    }

    // -- Federation helpers --

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    private int resolvePartnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    private FederationPartner resolveActivePartner(int localStationId, UUID partnerStationUid) {
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        return partner;
    }

    private FederationPartner findPartnerForStation(int localStationId, int remoteStationId) {
        var partners = federationService.findPartners(localStationId);
        for (var partner : partners) {
            int partnerRemoteId = resolvePartnerStationId(partner);
            if (partnerRemoteId == remoteStationId && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return partner;
            }
        }
        return null;
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation results", e);
            }
        }
        return result;
    }

    public record SharedKbItem(KbFileSummary file, int sourceStationId, int partnerId) {}

    public record FederatedSearchResult(KbFileSummary file, String snippet, String stationName, String stationUid) {}
}
