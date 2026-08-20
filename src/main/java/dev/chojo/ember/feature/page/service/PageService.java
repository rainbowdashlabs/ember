/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageRepository;
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

    private final PageRepository pageRepository;
    private final ContentBlockService blocks;
    private final MediaLibraryService mediaLibrary;
    private final StationMemberRepository stationMemberRepository;
    private final AvatarService avatarService;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    @Inject
    public PageService(
            PageRepository pageRepository,
            ContentBlockService blocks,
            MediaLibraryService mediaLibrary,
            StationMemberRepository stationMemberRepository,
            AvatarService avatarService) {
        this.pageRepository = pageRepository;
        this.blocks = blocks;
        this.mediaLibrary = mediaLibrary;
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
        var page = pageRepository.create(stationId, title, slug, parentId, createdBy);
        var container = blocks.create(stationId);
        pageRepository.setContainer(page.id(), container.id());
        log.info("Page {} created in station {} by member {}", page.id(), stationId, createdBy);
        return pageRepository.findById(page.id()).orElse(page);
    }

    public Optional<StationPage> getPage(int pageId) {
        return pageRepository.findById(pageId).map(this::loadBlocks);
    }

    /**
     * Fills the page in with the blocks of its container. A page created before containers existed
     * was given one by the upgrade, so a page without one is a page nothing has been written into.
     */
    private StationPage loadBlocks(StationPage page) {
        if (page.containerId() == null) return page;
        return page.withRows(blocks.loadRows(page.containerId()));
    }

    public Optional<StationPage> getPageRendered(int pageId) {
        return getPage(pageId).map(this::renderMarkdownCells).map(this::resolveOgImageHash);
    }

    /**
     * Fills in the content hash of the page's social preview image. Media files are served by hash,
     * so a client holding only {@code ogImageId} cannot build the image URL.
     */
    private StationPage resolveOgImageHash(StationPage page) {
        if (page.ogImageId() == null) return page;
        return mediaLibrary
                .findFile(page.ogImageId())
                .map(file -> page.withOgImageHash(file.contentHash()))
                .orElse(page);
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
        return Optional.ofNullable(found).map(this::loadBlocks);
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
            List<ContentBlockService.RowData> rows) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;

        if (parentId != null && parentId != pageId) {
            validateDepth(parentId, 1 + maxChildDepth(pageId));
        }

        if (pageRepository.slugExists(page.stationId(), slug, pageId)) {
            slug = generateUniqueSlug(page.stationId(), slug, pageId);
        }

        pageRepository.updateMeta(pageId, title, slug, parentId, metaDescription, ogImageId);

        var container = blocks.ensure(page.stationId(), page.containerId());
        if (page.containerId() == null) pageRepository.setContainer(pageId, container.id());
        blocks.save(container.id(), rows, ContentBlockService.Scope.PAGE);

        log.info("Page {} saved in station {} ({} rows)", pageId, page.stationId(), rows.size());
        return true;
    }

    public boolean setPublished(int pageId, boolean published) {
        boolean changed = pageRepository.setPublished(pageId, published);
        if (changed) {
            log.info("Page {} publish state set to {}", pageId, published);
        }
        if (changed && !published) {
            // Auto-unset landing page if this page is being unpublished
            pageRepository.findById(pageId).ifPresent(page -> pageRepository
                    .getLandingPageId(page.stationId())
                    .filter(id -> id == pageId)
                    .ifPresent(_ -> pageRepository.setLandingPage(page.stationId(), null)));
        }
        return changed;
    }

    public boolean deletePage(int pageId) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;
        boolean deleted = pageRepository.delete(pageId);
        if (deleted) {
            // The container is the owned side, so nothing cleans it up for us.
            blocks.delete(page.containerId());
            log.info("Page {} deleted from station {}", pageId, page.stationId());
        } else {
            log.warn("Page {} delete matched no rows", pageId);
        }
        return deleted;
    }

    public StationPage duplicatePage(int pageId, int createdBy) {
        var source = pageRepository.findById(pageId).map(this::loadBlocks).orElseThrow();

        String newSlug = generateUniqueSlug(source.stationId(), source.slug() + "-copy", 0);
        var copy = pageRepository.create(
                source.stationId(), source.title() + " (Copy)", newSlug, source.parentId(), createdBy);
        var container = blocks.create(source.stationId());
        pageRepository.setContainer(copy.id(), container.id());
        if (source.containerId() != null) blocks.copyInto(source.containerId(), container.id());

        log.info(
                "Page {} duplicated from page {} in station {} by member {}",
                copy.id(),
                pageId,
                source.stationId(),
                createdBy);
        return pageRepository.findById(copy.id()).map(this::loadBlocks).orElseThrow();
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
        log.info("Landing page for station {} set to page {}", stationId, pageId);
    }

    // --- Landing page ---

    public Optional<StationPage> getLandingPage(int stationId) {
        return pageRepository
                .getLandingPageId(stationId)
                .flatMap(pageRepository::findById)
                .filter(StationPage::published)
                .map(this::loadBlocks)
                .map(this::renderMarkdownCells)
                .map(this::resolveOgImageHash);
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

    // --- Markdown rendering ---

    private StationPage renderMarkdownCells(StationPage page) {
        int stationId = page.stationId();
        var renderedRows = page.rows().stream()
                .map(row -> row.withCells(row.cells().stream()
                        .map(cell -> renderCell(stationId, cell))
                        .toList()))
                .toList();
        return page.withRows(renderedRows);
    }

    private ContentCell renderCell(int stationId, ContentCell cell) {
        if (cell.contentType() == CellContentType.MARKDOWN) {
            return new ContentCell(
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
            return new ContentCell(
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
}
