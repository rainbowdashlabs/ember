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
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageRepository;
import dev.chojo.ember.util.Markdown;
import dev.chojo.ember.util.ShareTokens;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Singleton
public class PageService {
    private static final Logger log = LoggerFactory.getLogger(PageService.class);
    private static final int MAX_DEPTH = 3;

    private final PageRepository pageRepository;
    private final ContentBlockService blocks;
    private final MediaLibraryService mediaLibrary;
    private final CellDescriptions descriptions;
    private final StationMemberRepository stationMemberRepository;
    private final AvatarService avatarService;
    private final ShareTokens shareTokens;

    @Inject
    public PageService(
            PageRepository pageRepository,
            ContentBlockService blocks,
            MediaLibraryService mediaLibrary,
            CellDescriptions descriptions,
            StationMemberRepository stationMemberRepository,
            AvatarService avatarService,
            ShareTokens shareTokens) {
        this.pageRepository = pageRepository;
        this.blocks = blocks;
        this.mediaLibrary = mediaLibrary;
        this.descriptions = descriptions;
        this.stationMemberRepository = stationMemberRepository;
        this.avatarService = avatarService;
        this.shareTokens = shareTokens;
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
            refuseUnlistedParent(parentId);
        }
        String slug = generateUniqueSlug(stationId, title, 0);
        var page = pageRepository.create(stationId, title, slug, parentId, createdBy);
        var container = blocks.create(stationId);
        pageRepository.setContainer(page.id(), container.id());
        log.info("Page {} created in station {} by member {}", page.id(), stationId, createdBy);
        return pageRepository.findById(page.id()).orElse(page);
    }

    /**
     * A page reached only by its link stands on its own, so nothing may be filed under one: a child
     * of a page that is not in the tree has no address anybody could work out.
     */
    private void refuseUnlistedParent(int parentId) {
        pageRepository.findById(parentId).ifPresent(parent -> {
            if (parent.visibility() == PageVisibility.UNLISTED) {
                throw new BadRequestResponse("A page reached by its link alone cannot hold pages under it");
            }
        });
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
     * Page picker for the page link cell. Returns a compact {@code PickerPage} shape (public UUID +
     * title + slug + updatedAt) for the pages of the supplied station that somebody outside can
     * open, with optional case-insensitive title-substring filter.
     */
    public List<PageRepository.PickerPage> searchPagePicker(int stationId, String search, int limit) {
        return pageRepository.searchForPicker(stationId, search, limit);
    }

    /**
     * The pages that belong in the station's menu, its public page list and its sitemap.
     *
     * <p>A page whose parent is not itself listed is left out, and so is one whose grandparent is
     * not: the check walks the whole line rather than the nearest parent. Looking only at the parent
     * let a page three deep out through a draft above it, which put it in the sitemap.
     */
    public List<StationPage> listListedPages(int stationId) {
        var all = pageRepository.findListedByStation(stationId);
        var listed = new HashMap<Integer, StationPage>();
        for (var page : all) {
            listed.put(page.id(), page);
        }
        return all.stream().filter(page -> lineIsListed(page, listed)).toList();
    }

    /**
     * Whether every page above this one is listed too.
     *
     * <p>The walk reads the pages it was handed rather than asking for each ancestor again: an
     * ancestor it may continue through is by definition one of them, since the step before checked
     * exactly that. Asking anyway cost a query per ancestor per page, on the menu, the sitemap and
     * every public render.
     */
    private boolean lineIsListed(StationPage page, Map<Integer, StationPage> listed) {
        var parentId = page.parentId();
        var seen = new HashSet<Integer>();
        while (parentId != null) {
            var parent = listed.get(parentId);
            if (parent == null || !seen.add(parentId)) return false;
            parentId = parent.parentId();
        }
        return true;
    }

    /**
     * The page a path of slugs spells, where every page along that path is listed.
     *
     * <p>Every step is checked and not only the last one. A published page under an unpublished one
     * used to be served at a path spelling its unpublished parent's slug, which handed out a page
     * nobody had published and named one nobody was meant to know about.
     */
    public Optional<StationPage> getPageByPath(int stationId, String path) {
        String[] segments = path.split("/");
        Integer parentId = null;
        StationPage found = null;
        for (String slug : segments) {
            var page = pageRepository.findBySlugAndParent(stationId, slug, parentId);
            if (page.isEmpty() || !page.get().visibility().listed()) return Optional.empty();
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
            refuseUnlistedParent(parentId);
        }
        if (parentId != null && page.visibility() == PageVisibility.UNLISTED) {
            throw new BadRequestResponse("A page reached by its link alone does not sit under another");
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

    /**
     * Moves a page between draft, reachable by its link, and public.
     *
     * <p>A page reached by its link stands outside the page tree, so becoming one detaches the page
     * from its parent and is refused while it still has children of its own. Lifting those children
     * to the top by itself would change every one of their public addresses without saying so, and
     * which of them belongs where is the editor's judgement rather than this method's.
     *
     * @param pageId     the page
     * @param visibility what it becomes
     * @return whether anything changed
     * @throws BadRequestResponse where the page still has children and is leaving the tree
     */
    public boolean setVisibility(int pageId, PageVisibility visibility) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return false;
        if (visibility == PageVisibility.UNLISTED && pageRepository.hasChildren(pageId)) {
            throw new BadRequestResponse("A page with pages under it cannot be reached by a link alone");
        }

        boolean minting = visibility == PageVisibility.UNLISTED;
        boolean changed = pageRepository.setVisibility(pageId, visibility, minting ? shareTokens.mint() : null);
        if (!changed) return false;
        log.info("Page {} visibility set to {}", pageId, visibility);

        if (visibility == PageVisibility.UNLISTED && page.parentId() != null) {
            pageRepository.updateMeta(
                    pageId, page.title(), page.slug(), null, page.metaDescription(), page.ogImageId());
        }
        if (!visibility.listed()) {
            pageRepository
                    .getLandingPageId(page.stationId())
                    .filter(id -> id == pageId)
                    .ifPresent(_ -> pageRepository.setLandingPage(page.stationId(), null));
        }
        return true;
    }

    /**
     * The link a page reachable by one is reached at, for the screen that shows it.
     *
     * <p>A page keeps the one link it was given. Opening it to everybody does not end it and does
     * not hide it either, because the link goes on working and somebody who handed it out is owed
     * the ability to see it and to replace it. Only a draft has none to show: nobody outside can
     * open it by any address at all.
     *
     * @param pageId the page
     * @return its token, or empty where it has none or nobody outside could use one
     */
    public Optional<String> shareToken(int pageId) {
        return pageRepository
                .findById(pageId)
                .filter(page -> page.visibility().reachable())
                .flatMap(page -> pageRepository.findShareToken(pageId));
    }

    /**
     * Replaces a page's link, ending every copy of the one it held. Where it had none, this is how
     * the first one is made.
     *
     * @param pageId   the page
     * @param expected the link the caller was shown, so two administrators cannot take it in turns
     *                 to end each other's without being told
     * @return the new link, or empty where the page has since been given a different one
     * @throws BadRequestResponse where nobody outside the station could open the page anyway, so a
     *                            link to it would be one that leads nowhere
     */
    public Optional<String> replaceShareToken(int pageId, String expected) {
        var page = pageRepository.findById(pageId).orElse(null);
        if (page == null) return Optional.empty();
        if (!page.visibility().reachable()) {
            throw new BadRequestResponse("A page nobody outside can open is not reached by a link either");
        }
        String replacement = shareTokens.mint();
        if (!pageRepository.replaceShareToken(pageId, expected, replacement)) return Optional.empty();
        log.info("Page {} share link replaced", pageId);
        return Optional.of(replacement);
    }

    public Optional<StationPage> getSharedPage(String token) {
        return pageRepository
                .findByShareToken(token)
                .filter(page -> page.visibility().reachable())
                .map(this::loadBlocks)
                .map(this::renderMarkdownCells)
                .map(this::resolveOgImageHash);
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
            if (!page.visibility().listed()) {
                throw new BadRequestResponse("A landing page has to be public");
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
                .filter(page -> page.visibility().listed())
                .map(this::loadBlocks)
                .map(this::renderMarkdownCells)
                .map(this::resolveOgImageHash);
    }

    /**
     * Whether the station has a page for its own public site, which is what decides whether that
     * site exists at all.
     *
     * <p>Listed rather than reachable on purpose. A station whose only page is one reached by its
     * link has no public site, and saying otherwise would put an empty pages menu on it and name it
     * in the sitemap on the strength of a page nobody is meant to find.
     */
    /**
     * Whether the station has any page in its menu at all.
     *
     * <p>Asked once per station while the sitemap index is built and once per public station
     * request, so it asks the database whether one exists rather than reading every listed page in
     * order to look at the size of the list.
     */
    public boolean hasListedPages(int stationId) {
        return pageRepository.anyListed(stationId);
    }

    public Optional<Integer> getLandingPageId(int stationId) {
        return pageRepository.getLandingPageId(stationId);
    }

    public Optional<String> getLandingPageSlug(int stationId) {
        return pageRepository
                .getLandingPageId(stationId)
                .flatMap(pageRepository::findById)
                .filter(page -> page.visibility().listed())
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
            return cell.withContent(Markdown.toHtml(cell.content()));
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
            return cell.withConfig(new CellConfig.MemberListConfig(
                    officers.title(),
                    officers.source(),
                    officers.sortBy(),
                    officers.showUserType(),
                    officers.showTag(),
                    officers.memberDescriptions(),
                    officers.memberOrder(),
                    resolved));
        }
        return descriptions.describe(stationId, cell);
    }

    // --- Internal helpers ---

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
