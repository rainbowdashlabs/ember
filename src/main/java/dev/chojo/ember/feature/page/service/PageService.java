/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.entity.PageCell;
import dev.chojo.ember.feature.page.entity.PageImage;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageRepository;
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

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Singleton
public class PageService {
    private static final Logger log = LoggerFactory.getLogger(PageService.class);
    private static final int MAX_DEPTH = 3;
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5 MB

    private final PageRepository pageRepository;
    private final PageImageStorageService imageStorage;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public PageService(PageRepository pageRepository, PageImageStorageService imageStorage) {
        this.pageRepository = pageRepository;
        this.imageStorage = imageStorage;
        List<Extension> extensions = List.of(
                TablesExtension.create(),
                HeadingAnchorExtension.create(),
                AutolinkExtension.create(),
                StrikethroughExtension.create());
        this.markdownParser = Parser.builder().extensions(extensions).build();
        this.htmlRenderer = HtmlRenderer.builder().extensions(extensions).build();
    }

    // --- Page CRUD ---

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

        // Clean up orphaned images
        cleanupOrphanedImages(pageId);

        return true;
    }

    public boolean setPublished(int pageId, boolean published) {
        boolean changed = pageRepository.setPublished(pageId, published);
        if (changed && !published) {
            // Auto-unset landing page if this page is being unpublished
            var page = pageRepository.findById(pageId).orElse(null);
            if (page != null) {
                pageRepository
                        .getLandingPageId(page.stationId())
                        .filter(id -> id == pageId)
                        .ifPresent(id -> pageRepository.setLandingPage(page.stationId(), null));
            }
        }
        return changed;
    }

    public boolean deletePage(int pageId) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;
        imageStorage.deleteAllForPage(pageId);
        return pageRepository.delete(pageId);
    }

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

    // --- Landing page ---

    public void setLandingPage(int stationId, Integer pageId) {
        if (pageId != null) {
            var page =
                    pageRepository.findById(pageId).orElseThrow(() -> new IllegalArgumentException("Page not found"));
            if (page.stationId() != stationId) {
                throw new IllegalArgumentException("Page does not belong to station");
            }
            if (!page.published()) {
                throw new IllegalArgumentException("Landing page must be published");
            }
            if (page.parentId() != null) {
                throw new IllegalArgumentException("Landing page cannot be a subpage");
            }
        }
        pageRepository.setLandingPage(stationId, pageId);
    }

    public Optional<StationPage> getLandingPage(int stationId) {
        return pageRepository
                .getLandingPageId(stationId)
                .flatMap(pageRepository::findById)
                .filter(StationPage::published)
                .map(pageRepository::loadFullTree)
                .map(this::renderMarkdownCells);
    }

    // --- Images ---

    public PageImage uploadImage(int pageId, String fileName, String mimeType, byte[] data) throws IOException {
        if (data.length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image exceeds maximum size of 5 MB");
        }
        var image = pageRepository.createImage(pageId, fileName, mimeType, data.length);
        imageStorage.store(pageId, image.id(), data, mimeType);
        return image;
    }

    public boolean deleteImage(int imageId) {
        var image = pageRepository.findImage(imageId).orElse(null);
        if (image == null) return false;
        imageStorage.delete(image.pageId(), image.id());
        return pageRepository.deleteImage(imageId);
    }

    public Optional<PageImageStorageService.FileData> readImage(int imageId) {
        var image = pageRepository.findImage(imageId).orElse(null);
        if (image == null) return Optional.empty();
        return imageStorage.read(image.pageId(), image.id());
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

    // --- Markdown rendering ---

    private StationPage renderMarkdownCells(StationPage page) {
        var renderedRows = page.rows().stream()
                .map(row -> row.withCells(row.cells().stream()
                        .map(cell -> cell.contentType() == CellContentType.MARKDOWN
                                ? new PageCell(
                                        cell.id(),
                                        cell.rowId(),
                                        cell.sortOrder(),
                                        cell.widthPercent(),
                                        cell.contentType(),
                                        renderMarkdown(cell.content()),
                                        cell.config())
                                : cell)
                        .toList()))
                .toList();
        return page.withRows(renderedRows);
    }

    private String renderMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        var document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }

    // --- Internal helpers ---

    private void cleanupOrphanedImages(int pageId) {
        Set<Integer> referenced = pageRepository.findReferencedImageIds(pageId);
        var allImages = pageRepository.findImagesByPage(pageId);
        for (var image : allImages) {
            if (!referenced.contains(image.id())) {
                imageStorage.delete(pageId, image.id());
                pageRepository.deleteImage(image.id());
            }
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
            throw new IllegalArgumentException("Page hierarchy exceeds maximum depth of " + MAX_DEPTH);
        }
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

    public record RowData(int sortOrder, List<CellData> cells) {}

    public record CellData(
            int sortOrder, double widthPercent, CellContentType contentType, String content, CellConfig config) {}
}
