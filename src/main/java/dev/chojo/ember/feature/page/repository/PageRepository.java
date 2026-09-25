/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class PageRepository {

    private static final String STATION_PAGE_COLUMNS =
            "id, public_uid, station_id, parent_id, title, slug, visibility, sort_order, meta_description, og_image_id, container_id, created_by, created_at, updated_at";

    // --- Page CRUD ---

    public StationPage create(int stationId, String title, String slug, Integer parentId, int createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_page(station_id, title, slug, parent_id, created_by)
                VALUES(:station_id, :title, :slug, :parent_id, :created_by)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("slug", slug)
                        .bind("parent_id", parentId)
                        .bind("created_by", createdBy),
                StationPage.mapFlat(),
                STATION_PAGE_COLUMNS);
    }

    public Optional<StationPage> findById(int id) {
        return SqlSupport.findById("station_page", STATION_PAGE_COLUMNS, id, StationPage.mapFlat());
    }

    public Optional<StationPage> findByPublicUid(UUID publicUid) {
        return query("SELECT %s FROM station_page WHERE public_uid = :public_uid::uuid;", STATION_PAGE_COLUMNS)
                .single(call().bind("public_uid", publicUid, StandardValueConverter.UUID_STRING))
                .map(StationPage.mapFlat())
                .first();
    }

    public Optional<StationPage> findBySlugAndStation(String slug, int stationId) {
        return query(
                        "SELECT %s FROM station_page WHERE slug = :slug AND station_id = :station_id;",
                        STATION_PAGE_COLUMNS)
                .single(call().bind("slug", slug).bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .first();
    }

    /**
     * The page with this slug directly under {@code parentId}, or at the root when it is
     * {@code null}.
     *
     * <p>A null parent means "match {@code IS NULL}" here, not "no filter", so the predicate is
     * chosen rather than left out - {@link WhereBuilder} drops null-valued predicates, which would
     * make this match a same-slug page at any depth.
     */
    public Optional<StationPage> findBySlugAndParent(int stationId, String slug, Integer parentId) {
        var where = parentId == null
                ? WhereBuilder.create().add("AND parent_id IS NULL")
                : WhereBuilder.create().add("AND parent_id = :parent_id", "parent_id", parentId);
        return query("""
                SELECT %s
                FROM station_page
                WHERE station_id = :station_id
                  AND slug = :slug
                  %s;""", STATION_PAGE_COLUMNS, where.fragment())
                .single(where.apply(call().bind("station_id", stationId).bind("slug", slug)))
                .map(StationPage.mapFlat())
                .first();
    }

    public List<StationPage> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM station_page WHERE station_id = :station_id ORDER BY sort_order;",
                        STATION_PAGE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .all();
    }

    public List<StationPage> findListedByStation(int stationId) {
        return query("""
                SELECT %s
                FROM station_page
                WHERE station_id = :station_id
                  AND visibility = 'PUBLIC'
                ORDER BY sort_order;""", STATION_PAGE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .all();
    }

    /**
     * The pages that put a given form on themselves, by title.
     *
     * <p>Asked before a form is closed to its link alone, because that shuts the address these
     * cells fetch it at and there is no other sign of it: the page goes on rendering and the poll
     * on it stops working for everybody who opens it.
     *
     * @param stationId  the station whose pages are searched
     * @param formPublicUid the form as a cell names it
     * @return the pages holding it, listed ones first, each named once however many cells it has
     */
    public List<PageUsingForm> findPagesEmbedding(int stationId, String formPublicUid) {
        return query("""
                SELECT DISTINCT p.id, p.title, p.visibility
                FROM page_cell c
                JOIN page_row r ON r.id = c.row_id
                JOIN station_page p ON p.container_id = r.container_id
                WHERE p.station_id = :station_id
                  AND c.config ->> 'formPublicUid' = :form_uid
                ORDER BY p.visibility DESC, p.title;""")
                .single(call().bind("station_id", stationId).bind("form_uid", formPublicUid))
                .map(row -> new PageUsingForm(
                        row.getInt("id"), row.getString("title"), row.getEnum("visibility", PageVisibility.class)))
                .all();
    }

    /** One page holding a form, and how far that page itself reaches. */
    public record PageUsingForm(int id, String title, PageVisibility visibility) {}

    /**
     * Whether the station has a page in its menu, without reading any of them.
     *
     * <p>The sitemap index asks this of every station it knows, and the public station endpoint
     * asks it on every render of every public page.
     */
    public boolean anyListed(int stationId) {
        return query("""
                SELECT EXISTS(
                    SELECT 1 FROM station_page WHERE station_id = :station_id AND visibility = 'PUBLIC'
                ) AS present;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getBoolean("present"))
                .first()
                .orElse(false);
    }

    /**
     * Editor's page link picker. Returns a compact shape - {@code publicUid},
     * {@code title}, {@code slug}, {@code updatedAt} - for the pages of the supplied station that
     * somebody outside can open, optionally filtered by case-insensitive title substring. Empty
     * {@code search} returns the most recently updated pages so the picker has something on first
     * focus.
     *
     * <p>Reachable rather than listed: an editor links to a page that is only reachable by its own
     * link as readily as to one in the menu, and is warned about it where they do.
     */
    public List<PickerPage> searchForPicker(int stationId, String search, int limit) {
        var where = WhereBuilder.create().like("AND LOWER(title) LIKE :q", "q", search);
        return query("""
                SELECT public_uid, title, slug, updated_at
                FROM station_page
                WHERE station_id = :station_id
                  AND visibility IN ('UNLISTED', 'PUBLIC')
                  %s
                ORDER BY updated_at DESC
                LIMIT :limit;""", where.fragment())
                .single(where.apply(call().bind("station_id", stationId).bind("limit", limit)))
                .map(row -> new PickerPage(
                        row.get("public_uid", StandardValueConverter.UUID_STRING),
                        row.getString("title"),
                        row.getString("slug"),
                        row.get("updated_at", INSTANT_TIMESTAMP)))
                .all();
    }

    public boolean updateMeta(
            int id, String title, String slug, Integer parentId, String metaDescription, Integer ogImageId) {
        return query("""
                UPDATE station_page
                SET
                    title            = :title,
                    slug             = :slug,
                    parent_id        = :parent_id,
                    meta_description = :meta_description,
                    og_image_id      = :og_image_id,
                    updated_at       = :updated_at
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("slug", slug)
                        .bind("parent_id", parentId)
                        .bind("meta_description", metaDescription)
                        .bind("og_image_id", ogImageId)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Sets a page's visibility and, where it is becoming reachable by a link and holds none yet,
     * mints one in the same statement.
     *
     * <p>One statement rather than two on purpose. Two administrators opening the page at the same
     * moment would otherwise each mint a link and the second would end the first's, which by then
     * has been sent; and between two statements the page would be unlisted with no link, which is a
     * page nobody can reach.
     *
     * @param id         the page
     * @param visibility what it becomes
     * @param mintedToken a fresh token, used only where the page has none and is becoming unlisted
     * @return whether a row changed
     */
    public boolean setVisibility(int id, PageVisibility visibility, String mintedToken) {
        return query("""
                UPDATE station_page
                SET visibility  = :visibility,
                    share_token = CASE
                                      WHEN :mints THEN COALESCE(share_token, :token)
                                      ELSE share_token
                                  END,
                    updated_at  = :updated_at
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("visibility", visibility.name())
                        .bind("mints", visibility == PageVisibility.UNLISTED)
                        .bind("token", mintedToken)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public Optional<String> findShareToken(int id) {
        return query("SELECT share_token FROM station_page WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> row.getString("share_token"))
                .first();
    }

    public Optional<StationPage> findByShareToken(String token) {
        return query("SELECT %s FROM station_page WHERE share_token = :token;", STATION_PAGE_COLUMNS)
                .single(call().bind("token", token))
                .map(StationPage.mapFlat())
                .first();
    }

    /**
     * Replaces a page's link, but only where it still holds the one the caller was shown.
     *
     * <p>Two administrators looking at the same page would otherwise take it in turns to end each
     * other's link without either being told. The one whose statement changes nothing is the one who
     * has to look again.
     *
     * @param id          the page
     * @param expected    the token the caller last saw
     * @param replacement the token to put in its place
     * @return whether the row still held the expected token and was replaced
     */
    public boolean replaceShareToken(int id, String expected, String replacement) {
        return query("""
                UPDATE station_page
                SET share_token = :replacement,
                    updated_at  = :updated_at
                WHERE id = :id AND share_token IS NOT DISTINCT FROM :expected;""")
                .single(call().bind("id", id)
                        .bind("expected", expected)
                        .bind("replacement", replacement)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean hasChildren(int id) {
        return SqlSupport.exists("SELECT 1 FROM station_page WHERE parent_id = :id;", call().bind("id", id));
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("station_page", id);
    }

    /**
     * Points the page at the container holding its blocks. Every page has one; a page created
     * before containers existed was given one by the upgrade.
     */
    public boolean setContainer(int pageId, int containerId) {
        return query("UPDATE station_page SET container_id = :container_id WHERE id = :id;")
                .single(call().bind("id", pageId).bind("container_id", containerId))
                .update()
                .changed();
    }

    public boolean slugExists(int stationId, String slug, int excludePageId) {
        return SqlSupport.exists(
                "SELECT 1 FROM station_page WHERE station_id = :station_id AND slug = :slug AND id != :exclude_id;",
                call().bind("station_id", stationId).bind("slug", slug).bind("exclude_id", excludePageId));
    }

    public int countChildren(int parentId) {
        return SqlSupport.count(
                "SELECT count(*) AS cnt FROM station_page WHERE parent_id = :parent_id;",
                call().bind("parent_id", parentId));
    }

    public int depth(int pageId) {
        int d = 0;
        Integer currentParent = findById(pageId).map(StationPage::parentId).orElse(null);
        while (currentParent != null) {
            d++;
            currentParent = findById(currentParent).map(StationPage::parentId).orElse(null);
        }
        return d;
    }

    public void setLandingPage(int stationId, Integer pageId) {
        query("UPDATE station SET landing_page_id = :page_id WHERE id = :station_id;")
                .single(call().bind("page_id", pageId).bind("station_id", stationId))
                .update();
    }

    // --- Landing page ---

    public Optional<Integer> getLandingPageId(int stationId) {
        return query("SELECT landing_page_id FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getObject("landing_page_id") != null ? row.getInt("landing_page_id") : null)
                .first();
    }

    /**
     * The pages of a station that put one of these wiki articles on themselves.
     *
     * <p>A page cell names an article by number in its settings and no foreign key follows it, so
     * deleting the article leaves the cell falling back to its stand-in title without anybody being
     * told. This is the one question that turns that into something a delete dialog can say first.
     *
     * <p>The number is compared as it is written rather than cast, because the settings of a cell
     * are free-form and one that never held a number would fail the cast rather than not match.
     *
     * @param stationId the station whose pages are asked about
     * @param fileIds   the articles being deleted
     * @return the pages that carry any of them, each named once
     */
    public List<EmbeddingPage> findPagesEmbeddingArticles(int stationId, List<Integer> fileIds) {
        if (fileIds.isEmpty()) return List.of();
        return query("""
                SELECT DISTINCT p.title, p.visibility
                FROM page_cell c
                    JOIN page_row r ON r.id = c.row_id
                    JOIN station_page p ON p.container_id = r.container_id
                WHERE p.station_id = :station_id
                  AND c.content_type = 'KB_ARTICLE'
                  AND c.config ->> 'articleId' = ANY(:article_ids)
                ORDER BY p.title;""")
                .single(call().bind("station_id", stationId)
                        .bind(
                                "article_ids",
                                fileIds.stream().map(String::valueOf).toList(),
                                PostgreSqlTypes.TEXT))
                .map(row -> new EmbeddingPage(
                        row.getString("title"),
                        row.getEnum("visibility", PageVisibility.class).reachable()))
                .all();
    }

    /**
     * A page that carries a wiki article, and whether anybody outside can open that page.
     *
     * <p>Reachable rather than listed: a page nobody can find is still a page somebody was sent, so
     * deleting the article under it is as visible a change as deleting one under a page in the menu.
     */
    public record EmbeddingPage(String title, boolean reachable) {}

    /**
     * Lightweight picker result row for the page picker. Exposes only the public UUID - never the
     * internal integer id.
     */
    public record PickerPage(UUID pageUid, String title, String slug, Instant updatedAt) {}
}
