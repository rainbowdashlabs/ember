/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface CellConfig {
    Logger log = LoggerFactory.getLogger(CellConfig.class);
    ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    CellConfig EMPTY = new MarkdownConfig();

    /**
     * Reads stored settings. A stored row that no longer fits its record is logged and read as the
     * empty settings of its kind, so one stale block cannot take the whole page down with it.
     */
    static CellConfig parse(CellContentType type, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return type.emptyConfig();
        }
        try {
            return MAPPER.readValue(json, type.configClass());
        } catch (JacksonException e) {
            log.error("Failed to parse CellConfig for type {}: {}", type, json, e);
            return type.emptyConfig();
        }
    }

    /**
     * Reads stored settings that arrived as a tree, such as the cells of nested rows. As tolerant as
     * {@link #parse(CellContentType, String)}: what does not fit is logged and read as empty.
     */
    static CellConfig parse(CellContentType type, JsonNode node) {
        try {
            return bind(type, node);
        } catch (IllegalArgumentException e) {
            log.error("Failed to read CellConfig for type {}: {}", type, node, e);
            return type.emptyConfig();
        }
    }

    /**
     * Binds settings an author sent, refusing any that do not fit.
     *
     * <p>Which record they are depends on the content type standing next to them, so they cannot be
     * bound while the request is read. Carrying them this far as a tree rather than as JSON text
     * spares them a trip through the serialiser and back that could only lose something. Absent or
     * empty settings are the empty settings of the kind.
     *
     * @throws IllegalArgumentException when the settings are not an object or a value in them does
     *                                  not fit the record of this kind, for example text where a
     *                                  number belongs
     */
    static CellConfig bind(CellContentType type, JsonNode node) {
        if (node == null || node.isNull()) return type.emptyConfig();
        if (!node.isObject()) {
            throw new IllegalArgumentException("The settings of a " + type + " block are not an object");
        }
        if (node.isEmpty()) return type.emptyConfig();
        try {
            return MAPPER.treeToValue(node, type.configClass());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("The settings do not fit a " + type + " block", e);
        }
    }

    /**
     * The settings of a kind of block with nothing set, which is what its record reads an empty
     * object as.
     */
    static CellConfig emptyOf(Class<? extends CellConfig> configClass) {
        return MAPPER.treeToValue(MAPPER.createObjectNode(), configClass);
    }

    /**
     * The public id a block names, kept only when it is a UUID. An author writes a block's settings
     * as they like, and an id that is not one names nothing.
     */
    private static @Nullable String wellFormedUid(@Nullable String raw) {
        if (raw == null) return null;
        try {
            return UUID.fromString(raw).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            log.error("Failed to serialize CellConfig", e);
            return "{}";
        }
    }

    enum ImageFit {
        COVER,
        CONTAIN,
        FILL
    }

    enum CalloutVariant {
        INFO,
        WARNING,
        SUCCESS,
        TIP
    }

    enum GalleryAspectMode {
        /**
         * All gallery items rendered as square thumbnails (object-fit: cover).
         */
        SQUARE,
        /**
         * Each item keeps its natural aspect ratio; max height limits row height and items flow into the next column.
         */
        PRESERVE
    }

    /**
     * How the optional preview image of an external link card is rendered.
     */
    enum ExternalLinkImageDisplay {
        /**
         * Full-width banner above the text (default).
         */
        BANNER,
        /**
         * Square thumbnail to the left of the text.
         */
        ICON
    }

    /**
     * Sort order applied to the resolved member list of an {@code MEMBER_LIST_SPOTLIGHT}.
     */
    enum MemberListSortBy {
        /**
         * Preserve the natural source order or the persistent {@code memberOrder} overlay.
         */
        ORDER,
        /**
         * Alphabetical by display name.
         */
        NAME,
        /**
         * Alphabetical by user type.
         */
        ROLE,
        /**
         * Oldest member first.
         */
        JOIN_DATE
    }

    record MarkdownConfig() implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageConfig(
            @Nullable ImageFit imageFit,
            @Nullable String altText,
            @Nullable Integer maxHeight,
            @Nullable String description,
            @Nullable Double cropTop,
            @Nullable Double cropRight,
            @Nullable Double cropBottom,
            @Nullable Double cropLeft,
            @Nullable Integer borderRadiusPercent,
            @Nullable Integer borderWidthPx,
            @Nullable String borderColor)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VideoConfig(@Nullable Boolean autoplay, @Nullable Boolean loop) implements CellConfig {}

    /**
     * Callout box. The body text lives in cell.content (markdown).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CalloutConfig(
            @Nullable CalloutVariant variant, @Nullable String title) implements CellConfig {}

    /**
     * Quote block. The quote text lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuoteConfig(@Nullable String author, @Nullable String attributionUrl) implements CellConfig {}

    /**
     * Horizontal divider with optional centred label.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DividerConfig(@Nullable String label) implements CellConfig {}

    /**
     * Vertical spacer. Height in CSS pixels.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpacerConfig(@Nullable Integer heightPx) implements CellConfig {}

    /**
     * Collapsible accordion. The body markdown lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccordionConfig(@Nullable String title, @Nullable Boolean openByDefault) implements CellConfig {}

    /**
     * Embedded PDF viewer. url is required; height is in CSS pixels.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PdfConfig(@Nullable String url, @Nullable Integer heightPx) implements CellConfig {}

    /**
     * Download card pointing at any file URL.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileDownloadConfig(
            @Nullable String url,
            @Nullable String label,
            @Nullable String description) implements CellConfig {}

    /**
     * Countdown to a target date.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CountdownConfig(
            @Nullable String targetDate,
            @Nullable String label,
            @Nullable String sublabel) implements CellConfig {}

    /**
     * Featured event card.
     *
     * <p>The editor names a live event by {@code eventUid}, whose name, time and link are read when
     * the block is shown, and may narrow it to one occurrence by {@code date} ({@code YYYY-MM-DD}).
     * The remaining text fields are the older hand-written card, still read where no event is named.
     *
     * <p>An author writes this record as they like, so what names an event is kept only when it is
     * well formed: an id that is not a UUID names nothing, and next to a named event a date that is
     * not a calendar day is dropped rather than carried to every reader.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FeaturedEventConfig(
            @Nullable String title,
            @Nullable String date,
            @Nullable String location,
            @Nullable String description,
            @Nullable String ctaText,
            @Nullable String ctaUrl,
            @Nullable String eventUid,
            @Nullable String descriptionOverride)
            implements CellConfig {

        public FeaturedEventConfig {
            eventUid = wellFormedUid(eventUid);
            if (eventUid != null) date = wellFormedDay(date);
        }

        private static @Nullable String wellFormedDay(@Nullable String raw) {
            if (raw == null) return null;
            try {
                return LocalDate.parse(raw).toString();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    /**
     * List of upcoming events, read live from the station's public events and filtered by
     * {@code categoryIds}, or the older hand-written {@code items}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpcomingEventsConfig(
            @Nullable String title,
            @Nullable List<EventItem> items,
            @Nullable List<Integer> categoryIds,
            @Nullable Integer limit,
            @Nullable Boolean includeFederated)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EventItem(
            @Nullable String title,
            @Nullable String date,
            @Nullable String location,
            @Nullable String url) {}

    /**
     * Link card pointing at a public KB article.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record KbArticleConfig(
            @Nullable Integer articleId, @Nullable String fallbackTitle) implements CellConfig {}

    /**
     * News teaser.
     *
     * <p>The editor names a news entry by {@code newsUid}, whose title, date, text and link are read
     * when the block is shown, and only while the entry is on the station's public blog. The
     * remaining text fields are the older hand-written card: they are still read, so a block stored
     * with them keeps its text in the article's written form, but the block itself shows only the
     * entry it names.
     *
     * <p>An id that is not a UUID names nothing.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NewsTeaserConfig(
            @Nullable String title,
            @Nullable String date,
            @Nullable String summary,
            @Nullable String url,
            @Nullable String imageUrl,
            @Nullable String newsUid)
            implements CellConfig {

        public NewsTeaserConfig {
            newsUid = wellFormedUid(newsUid);
        }
    }

    /**
     * Link card pointing at another station page.
     *
     * <p>The target is named by the page's public uid, which is what the picker hands out and what
     * every other outside reference to a page uses. It was once a database id, which nothing on the
     * outside ever had, so the uid the editor sent was dropped on the way in and every card ever
     * placed pointed nowhere.
     *
     * <p>{@code resolvedTitle} and {@code resolvedHref} are filled in when the page is drawn, never
     * stored: a page's name and address both change, and a copy written into the card would go stale
     * the moment either did. The editor reads the raw cell and sees neither.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PageLinkConfig(
            @Nullable String pageUid,
            @Nullable String fallbackTitle,
            @Nullable String resolvedTitle,
            @Nullable String resolvedHref)
            implements CellConfig {

        public PageLinkConfig(@Nullable String pageUid, @Nullable String fallbackTitle) {
            this(pageUid, fallbackTitle, null, null);
        }

        public PageLinkConfig resolvedAs(String title, String href) {
            return new PageLinkConfig(pageUid, fallbackTitle, title, href);
        }
    }

    /**
     * OpenStreetMap embed via configurable coordinates.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MapConfig(
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable Integer zoom,
            @Nullable Integer heightPx,
            @Nullable String label)
            implements CellConfig {}

    /**
     * Address card with formatted postal address.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddressCardConfig(
            @Nullable String addressLine,
            @Nullable String postalCode,
            @Nullable String city,
            @Nullable String country,
            @Nullable String mapUrl,
            @Nullable String label)
            implements CellConfig {}

    /**
     * Partner stations cell. Either renders an explicit list of station UUIDs, or - when
     * {@code autoFillFromPartners} is {@code true} - every federated partner of the host station.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PartnerStationsConfig(
            @Nullable String title,
            @Nullable List<String> stationUids,
            @Nullable Boolean autoFillFromPartners) implements CellConfig {}

    /**
     * Member spotlight referencing an existing station member by UUID. The displayed name and
     * avatar are resolved live from the member's record. {@code blurb} is an editor-supplied
     * free-form note. {@code showUserType} (default {@code true}) and {@code showTag}
     * (default {@code false}) independently control whether the user type line and the primary
     * visible tag badge are rendered next to the name.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MemberSpotlightConfig(
            @Nullable String memberUid,
            @Nullable String blurb,
            @Nullable Boolean showUserType,
            @Nullable Boolean showTag)
            implements CellConfig {}

    /**
     * Officers row (a.k.a. member-list spotlight) sourced from a group, tag, or manual UUID
     * list. {@code source} is stored as a pass-through {@link JsonNode} so the polymorphic
     * {@code {kind, …}} discriminator survives round-tripping. {@code showUserType} and
     * {@code showTag} independently control whether the user type and the member's primary tag
     * are shown on each card. {@code memberOrder} is the persistent display order applied when
     * {@code sortBy == ORDER}: UUIDs listed there are rendered first in the given order, with
     * members not in the list falling back to the natural source order (memberUids for manual;
     * alphabetical for group / tag).
     *
     * <p>{@code resolvedMembers} is a render-time injection populated by {@code PageService}
     * when the page is served via {@code getPageRendered} so the public visitor never needs to
     * call the auth-gated avatar endpoint. It is never persisted; saves round-trip the same
     * Jackson record and the renderer always replaces it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MemberListConfig(
            @Nullable String title,
            @Nullable JsonNode source,
            @Nullable MemberListSortBy sortBy,
            @Nullable Boolean showUserType,
            @Nullable Boolean showTag,
            @Nullable Map<String, String> memberDescriptions,
            @Nullable List<String> memberOrder,
            @Nullable List<ResolvedMember> resolvedMembers)
            implements CellConfig {}

    /**
     * Render-time card data for a single resolved member-list entry. Mirrors the
     * {@code MemberSearchResult} shape used by the picker so the public renderer can display
     * everything without a follow-up auth-gated request. {@code avatarUrl}, when present,
     * carries the avatar inlined as a {@code data:} URL.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResolvedMember(
            String memberUid,
            String displayName,
            @Nullable String userType,
            @Nullable String displayTag,
            @Nullable String displayTagColor,
            @Nullable String avatarUrl,
            @Nullable String description) {}

    /**
     * Big-number stats counter row.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatsCounterConfig(@Nullable List<StatItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatItem(
            @Nullable String label,
            @Nullable String value,
            @Nullable String suffix) {}

    /**
     * Image gallery - list of items, each with its own image hash + alt + subtext.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageGalleryConfig(
            @Nullable List<GalleryItem> items,
            @Nullable Integer columns,
            @Nullable GalleryAspectMode aspectMode,
            @Nullable Integer maxItemHeightPx)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GalleryItem(
            @Nullable String imageHash,
            @Nullable String altText,
            @Nullable String subtext) {}

    /**
     * Hero banner - full-width image with overlay text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HeroBannerConfig(
            @Nullable String imageHash,
            @Nullable String headline,
            @Nullable String subtitle,
            @Nullable String ctaText,
            @Nullable String ctaUrl)
            implements CellConfig {}

    /**
     * Past event recap: a live past event named by {@code eventUid} with the editor's
     * {@code recapDescription}, or the older hand-written card.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PastEventRecapConfig(
            @Nullable String title,
            @Nullable String date,
            @Nullable String imageHash,
            @Nullable String summary,
            @Nullable String eventUid,
            @Nullable String recapDescription)
            implements CellConfig {}

    /**
     * Tabbed sections.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabsConfig(@Nullable List<TabItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabItem(@Nullable String title, @Nullable String body) {}

    /**
     * Achievements / badges showcase.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementsConfig(
            @Nullable String title, @Nullable List<AchievementItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementItem(
            @Nullable String title,
            @Nullable String description,
            @Nullable String year) {}

    /**
     * External link card with OG-style preview metadata supplied by the admin.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExternalLinkCardConfig(
            @Nullable String url,
            @Nullable String title,
            @Nullable String description,
            @Nullable String imageUrl,
            @Nullable ExternalLinkImageDisplay imageDisplay)
            implements CellConfig {}

    /**
     * Blog feed signup card. Renders the station's public blog RSS/Atom feed URLs together
     * with editor-supplied title + description so visitors can subscribe in their reader of
     * choice. Feed URLs are composed at render time from the host station UID.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BlogSignupConfig(
            @Nullable String title, @Nullable String description) implements CellConfig {}

    /**
     * Embedded audio player.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AudioEmbedConfig(@Nullable String url, @Nullable String title) implements CellConfig {}

    /**
     * Embedded poll. The cell references a public form (purpose = POLL) by its public UUID and
     * the render component fetches the form definition and submits anonymously via the public
     * form endpoints.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PollEmbedConfig(
            @Nullable String formPublicUid, @Nullable Boolean showResultsAfterVote) implements CellConfig {}

    /**
     * Quiz teaser. References one or more public quiz catalogs by id; the renderer pulls a
     * random question from them and reveals the answer on click.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuizTeaserConfig(
            @Nullable String title,
            @Nullable String description,
            @Nullable List<Integer> catalogIds) implements CellConfig {}

    /**
     * Contact form call-to-action. References a public form (purpose = CONTACT) by its public
     * UUID; the cell renders that form for in-page anonymous submission. Headline and body are
     * editor-supplied overrides shown above the form fields.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FormsCtaConfig(
            @Nullable String formPublicUid,
            @Nullable String headlineOverride,
            @Nullable String bodyOverride) implements CellConfig {}

    /**
     * Syntax-highlighted code block. Code lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CodeBlockConfig(@Nullable String language) implements CellConfig {}

    /**
     * Cell that contains nested rows. The rows are stored opaquely as JSON nodes so the existing
     * ContentRow shape (rows of cells of … nested rows) round-trips without a dedicated record type.
     * The frontend treats this as a recursive RowEditData[].
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NestedRowsConfig(@Nullable JsonNode rows) implements CellConfig {}
}
