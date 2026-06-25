/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.entity.PageCell;
import dev.chojo.ember.feature.page.entity.PageFile;
import dev.chojo.ember.feature.page.entity.PageFileFolder;
import dev.chojo.ember.feature.page.entity.PageFileTag;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageFileMetaRepository;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.util.HtmlSanitizer;
import io.javalin.http.BadRequestResponse;
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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class PageService {
    private static final Logger log = LoggerFactory.getLogger(PageService.class);
    private static final int MAX_DEPTH = 3;

    private final PageRepository pageRepository;
    private final PageFileMetaRepository metaRepository;
    private final PageFileStorageService imageStorage;
    private final PageImageVariantService variantService;
    private final StorageQuotaService quotaService;
    private final StationMemberRepository stationMemberRepository;
    private final AvatarService avatarService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public PageService(
            PageRepository pageRepository,
            PageFileMetaRepository metaRepository,
            PageFileStorageService imageStorage,
            PageImageVariantService variantService,
            StorageQuotaService quotaService,
            StationMemberRepository stationMemberRepository,
            AvatarService avatarService) {
        this.pageRepository = pageRepository;
        this.metaRepository = metaRepository;
        this.imageStorage = imageStorage;
        this.variantService = variantService;
        this.quotaService = quotaService;
        this.stationMemberRepository = stationMemberRepository;
        this.avatarService = avatarService;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer =
                HtmlRenderer.builder().extensions(extensions).sanitizeUrls(true).build();
    }

    // --- Page CRUD ---

    static String toSlug(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        // Remove diacritics
        String ascii = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Replace non-alphanumeric with hyphens
        String slug = ascii.replaceAll("[^a-z0-9]+", "-");
        // Trim leading/trailing hyphens
        return slug.replaceAll("^-+|-+$", "");
    }

    public StationPage create(int stationId, String title, Integer parentId, int createdBy) {
        if (parentId != null) {
            validateDepth(parentId, 1);
        }
        String slug = generateUniqueSlug(stationId, title, 0);
        return pageRepository.create(stationId, title, slug, parentId, createdBy);
    }

    public Optional<StationPage> getPage(int pageId) {
        return pageRepository.findById(pageId).map(pageRepository::loadFullTree);
    }

    public Optional<StationPage> getPageRendered(int pageId) {
        return getPage(pageId).map(this::renderMarkdownCells);
    }

    public List<StationPage> listPages(int stationId) {
        return pageRepository.findByStation(stationId);
    }

    /**
     * Page picker for the {@code PAGE_LINK} cell. Returns a compact
     * {@code PickerPage} shape (public UUID + title + slug + updatedAt) for published pages of the
     * supplied station, with optional case-insensitive title-substring filter.
     */
    public List<PageRepository.PickerPage> searchPagePicker(int stationId, String search, int limit) {
        return pageRepository.searchForPicker(stationId, search, limit);
    }

    public List<StationPage> listPublishedPages(int stationId) {
        var all = pageRepository.findPublishedByStation(stationId);
        // Filter out children whose parents are unpublished
        Set<Integer> publishedIds = new HashSet<>();
        for (var page : all) {
            publishedIds.add(page.id());
        }
        return all.stream()
                .filter(p -> p.parentId() == null || publishedIds.contains(p.parentId()))
                .toList();
    }

    public Optional<StationPage> getPageByPath(int stationId, String path) {
        String[] segments = path.split("/");
        Integer parentId = null;
        StationPage found = null;
        for (String slug : segments) {
            var page = pageRepository.findBySlugAndParent(stationId, slug, parentId);
            if (page.isEmpty()) return Optional.empty();
            found = page.get();
            parentId = found.id();
        }
        return Optional.ofNullable(found).map(pageRepository::loadFullTree);
    }

    public String getPagePath(StationPage page) {
        List<String> segments = new ArrayList<>();
        segments.add(page.slug());
        Integer parentId = page.parentId();
        while (parentId != null) {
            var parent = pageRepository.findById(parentId).orElse(null);
            if (parent == null) break;
            segments.addFirst(parent.slug());
            parentId = parent.parentId();
        }
        return String.join("/", segments);
    }

    public boolean savePage(
            int pageId,
            String title,
            String slug,
            Integer parentId,
            String metaDescription,
            Integer ogImageId,
            List<RowData> rows) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;

        if (parentId != null && parentId != pageId) {
            validateDepth(parentId, 1 + maxChildDepth(pageId));
        }

        if (pageRepository.slugExists(page.stationId(), slug, pageId)) {
            slug = generateUniqueSlug(page.stationId(), slug, pageId);
        }

        pageRepository.updateMeta(pageId, title, slug, parentId, metaDescription, ogImageId);

        // Delete existing rows/cells and re-insert
        pageRepository.deleteRowsByPage(pageId);
        for (var row : rows) {
            int rowId = pageRepository.insertRow(pageId, row.sortOrder());
            for (var cell : row.cells()) {
                pageRepository.insertCell(
                        rowId,
                        cell.sortOrder(),
                        cell.widthPercent(),
                        cell.contentType(),
                        cell.content(),
                        cell.config());
            }
        }

        return true;
    }

    public boolean setPublished(int pageId, boolean published) {
        boolean changed = pageRepository.setPublished(pageId, published);
        if (changed && !published) {
            // Auto-unset landing page if this page is being unpublished
            pageRepository.findById(pageId).ifPresent(page -> pageRepository
                    .getLandingPageId(page.stationId())
                    .filter(id -> id == pageId)
                    .ifPresent(id -> pageRepository.setLandingPage(page.stationId(), null)));
        }
        return changed;
    }

    public boolean deletePage(int pageId) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;
        return pageRepository.delete(pageId);
    }

    // --- Landing page ---

    public StationPage duplicatePage(int pageId, int createdBy) {
        var source = pageRepository
                .findById(pageId)
                .map(pageRepository::loadFullTree)
                .orElseThrow();

        String newSlug = generateUniqueSlug(source.stationId(), source.slug() + "-copy", 0);
        var copy = pageRepository.create(
                source.stationId(), source.title() + " (Copy)", newSlug, source.parentId(), createdBy);

        for (var row : source.rows()) {
            int newRowId = pageRepository.insertRow(copy.id(), row.sortOrder());
            for (var cell : row.cells()) {
                pageRepository.insertCell(
                        newRowId,
                        cell.sortOrder(),
                        cell.widthPercent(),
                        cell.contentType(),
                        cell.content(),
                        cell.config());
            }
        }

        return pageRepository
                .findById(copy.id())
                .map(pageRepository::loadFullTree)
                .orElseThrow();
    }

    public void setLandingPage(int stationId, Integer pageId) {
        if (pageId != null) {
            var page =
                    pageRepository.findById(pageId).orElseThrow(() -> new IllegalArgumentException("Page not found"));
            if (page.stationId() != stationId) {
                throw new BadRequestResponse("Page does not belong to station");
            }
            if (!page.published()) {
                throw new BadRequestResponse("Landing page must be published");
            }
            if (page.parentId() != null) {
                throw new BadRequestResponse("Landing page cannot be a subpage");
            }
        }
        pageRepository.setLandingPage(stationId, pageId);
    }

    // --- Images ---

    public Optional<StationPage> getLandingPage(int stationId) {
        return pageRepository
                .getLandingPageId(stationId)
                .flatMap(pageRepository::findById)
                .filter(StationPage::published)
                .map(pageRepository::loadFullTree)
                .map(this::renderMarkdownCells);
    }

    public PageFile uploadPageFile(int pageId, String fileName, String mimeType, byte[] data) throws IOException {
        var page = pageRepository
                .findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown page id: " + pageId));
        return uploadFile(page.stationId(), pageId, fileName, mimeType, data);
    }

    /**
     * Uploads a file scoped to the station (no owning page). Used by the station-wide browser.
     */
    public PageFile uploadStationFile(int stationId, String fileName, String mimeType, byte[] data) throws IOException {
        return uploadFile(stationId, null, fileName, mimeType, data);
    }

    /**
     * Returns the best-fit variant of an uploaded page file for the given (requested width,
     * {@code Accept} header). Non-image files always return the original. Used by the public
     * file-serving route to swap in WebP and resized variants transparently.
     */
    public Optional<PageFileStorageService.FileData> readFileVariant(
            int stationId, String contentHash, Integer requestedWidth, String acceptHeader) {
        return variantService.readBest(stationId, contentHash, requestedWidth, acceptHeader);
    }

    public boolean deleteFile(int fileId) {
        var image = pageRepository.findFile(fileId).orElse(null);
        if (image == null) return false;
        boolean deleted = pageRepository.deleteFile(fileId);
        if (deleted) {
            if (image.contentHash() != null) {
                imageStorage.delete(image.stationId(), image.contentHash());
            }
            quotaService.onFileDeleted(image.stationId(), StorageCategory.PAGE_FILES, image.fileSize());
        }
        return deleted;
    }

    public List<PageFile> listFilesByStation(int stationId) {
        return pageRepository.findFilesByStation(stationId);
    }

    /**
     * Lists files in the station with usage + tag-ids.
     */
    public List<FileListing> listFilesWithUsage(int stationId) {
        var files = pageRepository.findFilesByStation(stationId);
        Set<Integer> unused = findUnusedFileIds(stationId);
        var tagAssignments = metaRepository.findTagAssignments(
                files.stream().map(PageFile::id).toList());
        return files.stream()
                .map(f -> new FileListing(f, !unused.contains(f.id()), tagAssignments.getOrDefault(f.id(), Set.of())))
                .toList();
    }

    // --- Folder + tag delegations ---

    public PageFileFolder createFolder(int stationId, Integer parentId, String name, int sortOrder) {
        return metaRepository.createFolder(stationId, parentId, name, sortOrder);
    }

    public List<PageFileFolder> listFolders(int stationId) {
        return metaRepository.findFoldersByStation(stationId);
    }

    public boolean updateFolder(int stationId, int folderId, Integer parentId, String name, int sortOrder) {
        var folder = metaRepository.findFolder(folderId).orElse(null);
        if (folder == null || folder.stationId() != stationId) return false;
        return metaRepository.updateFolder(folderId, parentId, name, sortOrder);
    }

    public boolean deleteFolder(int stationId, int folderId) {
        var folder = metaRepository.findFolder(folderId).orElse(null);
        if (folder == null || folder.stationId() != stationId) return false;
        return metaRepository.deleteFolder(folderId);
    }

    public PageFileTag createTag(int stationId, String name, String color) {
        return metaRepository.createTag(stationId, name, color);
    }

    public List<PageFileTag> listTags(int stationId) {
        return metaRepository.findTagsByStation(stationId);
    }

    public boolean updateTag(int stationId, int tagId, String name, String color) {
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (tag == null || tag.stationId() != stationId) return false;
        return metaRepository.updateTag(tagId, name, color);
    }

    public boolean deleteTag(int stationId, int tagId) {
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (tag == null || tag.stationId() != stationId) return false;
        return metaRepository.deleteTag(tagId);
    }

    public boolean assignTag(int stationId, int fileId, int tagId) {
        var file = pageRepository.findFile(fileId).orElse(null);
        var tag = metaRepository.findTag(tagId).orElse(null);
        if (file == null || file.stationId() != stationId || tag == null || tag.stationId() != stationId) return false;
        metaRepository.assignTag(fileId, tagId);
        return true;
    }

    public boolean unassignTag(int stationId, int fileId, int tagId) {
        var file = pageRepository.findFile(fileId).orElse(null);
        if (file == null || file.stationId() != stationId) return false;
        return metaRepository.unassignTag(fileId, tagId);
    }

    public boolean updateFileMeta(int stationId, int fileId, String altText, String description) {
        var existing = pageRepository.findFile(fileId).orElse(null);
        if (existing == null || existing.stationId() != stationId) return false;
        return pageRepository.updateFileMeta(fileId, altText, description);
    }

    public boolean moveFileToFolder(int stationId, int fileId, Integer folderId) {
        var file = pageRepository.findFile(fileId).orElse(null);
        if (file == null || file.stationId() != stationId) return false;
        return metaRepository.moveFileToFolder(fileId, folderId);
    }

    public Optional<PageFileStorageService.FileData> readFileById(int fileId) {
        var image = pageRepository.findFile(fileId).orElse(null);
        if (image == null || image.contentHash() == null) return Optional.empty();
        return imageStorage.read(image.stationId(), image.contentHash());
    }

    /**
     * Reads a file by station + content hash. Used by the public hash-keyed delivery route.
     */
    public Optional<PageFileStorageService.FileData> readFile(int stationId, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) return Optional.empty();
        var file = pageRepository.findByStationAndHash(stationId, contentHash).orElse(null);
        if (file == null) return Optional.empty();
        return imageStorage.read(stationId, contentHash);
    }

    public boolean hasPublishedPages(int stationId) {
        return !pageRepository.findPublishedByStation(stationId).isEmpty();
    }

    public Optional<Integer> getLandingPageId(int stationId) {
        return pageRepository.getLandingPageId(stationId);
    }

    public Optional<String> getLandingPageSlug(int stationId) {
        return pageRepository
                .getLandingPageId(stationId)
                .flatMap(pageRepository::findById)
                .filter(StationPage::published)
                .map(StationPage::slug);
    }

    /**
     * Returns the ids of files in the station that no cell currently references. Used by the
     * listing endpoint to flag candidates for pruning in the file browser.
     */
    public Set<Integer> findUnusedFileIds(int stationId) {
        Set<String> referenced = collectFileReferences(stationId);
        Set<Integer> unused = new HashSet<>();
        for (var file : pageRepository.findFilesByStation(stationId)) {
            boolean inUse = (file.contentHash() != null && referenced.contains(file.contentHash()))
                    || referenced.contains(String.valueOf(file.id()));
            if (!inUse) unused.add(file.id());
        }
        return unused;
    }

    // --- Markdown rendering ---

    /**
     * Deletes every page file in the station that is no longer referenced from any cell.
     * Returns the number of files removed. Only invoked when the user explicitly asks to prune.
     */
    public int pruneUnusedFiles(int stationId) {
        Set<String> referenced = collectFileReferences(stationId);
        int removed = 0;
        for (var file : pageRepository.findFilesByStation(stationId)) {
            boolean inUse = (file.contentHash() != null && referenced.contains(file.contentHash()))
                    || referenced.contains(String.valueOf(file.id()));
            if (inUse) continue;
            if (file.contentHash() != null) {
                imageStorage.delete(file.stationId(), file.contentHash());
            }
            pageRepository.deleteFile(file.id());
            quotaService.onFileDeleted(stationId, StorageCategory.PAGE_FILES, file.fileSize());
            removed++;
        }
        return removed;
    }

    String generateUniqueSlug(int stationId, String base, int excludePageId) {
        String slug = toSlug(base);
        if (slug.isBlank()) slug = "page";
        if (!pageRepository.slugExists(stationId, slug, excludePageId)) return slug;
        for (int i = 2; i < 1000; i++) {
            String candidate = slug + "-" + i;
            if (!pageRepository.slugExists(stationId, candidate, excludePageId)) return candidate;
        }
        throw new IllegalStateException("Could not generate unique slug");
    }

    private PageFile uploadFile(int stationId, Integer pageId, String fileName, String mimeType, byte[] data)
            throws IOException {
        boolean isImage = mimeType != null && mimeType.startsWith("image/");
        if (isImage) {
            quotaService.checkImageSize(stationId, data.length);
        } else {
            quotaService.checkFileSize(stationId, data.length);
        }
        String contentHash = PageFileStorageService.hash(data);
        // Per-station dedup: if the same bytes were already uploaded for this station, reuse the
        // existing row + on-disk file instead of creating a duplicate. Cells just reference the
        // existing image id.
        var existing = pageRepository.findByStationAndHash(stationId, contentHash);
        if (existing.isPresent()) {
            // Make sure the file is still on disk (defensive: a crashed migration could have left
            // the row orphaned). store() is idempotent for identical bytes.
            imageStorage.store(stationId, contentHash, data, mimeType);
            return existing.get();
        }
        quotaService.checkQuota(stationId, StorageCategory.PAGE_FILES, data.length);
        var image = pageRepository.createFile(pageId, stationId, contentHash, fileName, mimeType, data.length);
        imageStorage.store(stationId, contentHash, data, mimeType);
        if (isImage) {
            variantService.generateVariants(stationId, contentHash, data, mimeType);
        }
        quotaService.trackDelta(stationId, StorageCategory.PAGE_FILES, data.length, 1);
        return image;
    }

    private StationPage renderMarkdownCells(StationPage page) {
        int stationId = page.stationId();
        var renderedRows = page.rows().stream()
                .map(row -> row.withCells(row.cells().stream()
                        .map(cell -> renderCell(stationId, cell))
                        .toList()))
                .toList();
        return page.withRows(renderedRows);
    }

    private PageCell renderCell(int stationId, PageCell cell) {
        if (cell.contentType() == CellContentType.MARKDOWN) {
            return new PageCell(
                    cell.id(),
                    cell.rowId(),
                    cell.sortOrder(),
                    cell.widthPercent(),
                    cell.contentType(),
                    renderMarkdown(cell.content()),
                    cell.config());
        }
        if (cell.contentType() == CellContentType.MEMBER_LIST_SPOTLIGHT
                && cell.config() instanceof CellConfig.MemberListConfig officers) {
            var resolved = MemberListResolver.resolve(
                    stationMemberRepository,
                    avatarService,
                    stationId,
                    officers.source(),
                    officers.sortBy(),
                    officers.memberDescriptions(),
                    officers.memberOrder());
            return new PageCell(
                    cell.id(),
                    cell.rowId(),
                    cell.sortOrder(),
                    cell.widthPercent(),
                    cell.contentType(),
                    cell.content(),
                    new CellConfig.MemberListConfig(
                            officers.title(),
                            officers.source(),
                            officers.sortBy(),
                            officers.showUserType(),
                            officers.showTag(),
                            officers.memberDescriptions(),
                            officers.memberOrder(),
                            resolved));
        }
        return cell;
    }

    // --- Internal helpers ---

    private String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        var document = markdownParser.parse(markdown);
        String html = htmlRenderer.render(document);
        return HtmlSanitizer.sanitize(html, HtmlSanitizer.Policy.RICH);
    }

    /**
     * Collects every page-file reference used anywhere in the station: IMAGE cell contents
     * (which may be a content hash or a legacy numeric id) plus any {@code imageId} /
     * {@code imageIds} fields inside cell config, including those buried inside NESTED_ROWS
     * sub-cells.
     */
    private Set<String> collectFileReferences(int stationId) {
        Set<String> out = new HashSet<>();
        for (var cell : pageRepository.findAllCellsByStation(stationId)) {
            collectFromCell(cell.contentType(), cell.content(), cell.config(), out);
        }
        return out;
    }

    private void collectFromCell(CellContentType type, String content, CellConfig config, Set<String> out) {
        if (type == CellContentType.IMAGE && content != null && !content.isBlank()) {
            out.add(content.trim());
        }
        if (config == null) return;
        try {
            var json = CellConfig.MAPPER.valueToTree(config);
            walkJsonForImageRefs(json, out);
        } catch (Exception e) {
            log.debug("Failed to walk cell config for file refs", e);
        }
    }

    private void walkJsonForImageRefs(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            var typeNode = node.get("contentType");
            if (typeNode != null && "IMAGE".equals(typeNode.asString())) {
                var contentNode = node.get("content");
                if (contentNode != null && !contentNode.isNull()) {
                    String v = contentNode.asString();
                    if (v != null && !v.isBlank()) out.add(v.trim());
                }
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey();
                var value = entry.getValue();
                if (("imageHash".equals(name) || "ogImageId".equals(name)) && value != null && !value.isNull()) {
                    out.add(value.asString());
                } else if ("imageHashes".equals(name) && value != null && value.isArray()) {
                    value.forEach(v -> out.add(v.asString()));
                } else {
                    walkJsonForImageRefs(value, out);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> walkJsonForImageRefs(child, out));
        }
    }

    private int maxChildDepth(int pageId) {
        var children =
                pageRepository
                        .findByStation(pageRepository
                                .findById(pageId)
                                .map(StationPage::stationId)
                                .orElse(0))
                        .stream()
                        .filter(p -> pageId == (p.parentId() != null ? p.parentId() : 0))
                        .toList();

        if (children.isEmpty()) return 0;
        return 1 + children.stream().mapToInt(c -> maxChildDepth(c.id())).max().orElse(0);
    }

    private void validateDepth(int parentId, int additionalLevels) {
        int currentDepth = pageRepository.depth(parentId) + 1; // parent is already at some depth
        if (currentDepth + additionalLevels > MAX_DEPTH) {
            throw new BadRequestResponse("Page hierarchy exceeds maximum depth of " + MAX_DEPTH);
        }
    }

    public record FileListing(PageFile file, boolean inUse, Set<Integer> tagIds) {}

    public record RowData(int sortOrder, List<CellData> cells) {}

    public record CellData(
            int sortOrder, double widthPercent, CellContentType contentType, String content, CellConfig config) {}
}
