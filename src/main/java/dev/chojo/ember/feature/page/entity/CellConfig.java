/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface CellConfig {
    Logger log = LoggerFactory.getLogger(CellConfig.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    CellConfig EMPTY = new MarkdownConfig();

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
            ImageFit imageFit,
            String altText,
            Integer maxHeight,
            String description,
            Double cropTop,
            Double cropRight,
            Double cropBottom,
            Double cropLeft,
            Integer borderRadiusPercent,
            Integer borderWidthPx,
            String borderColor)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VideoConfig(Boolean autoplay, Boolean loop) implements CellConfig {}

    /**
     * Callout box. The body text lives in cell.content (markdown).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CalloutConfig(CalloutVariant variant, String title) implements CellConfig {}

    /**
     * Quote block. The quote text lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuoteConfig(String author, String attributionUrl) implements CellConfig {}

    /**
     * Horizontal divider with optional centred label.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DividerConfig(String label) implements CellConfig {}

    /**
     * Vertical spacer. Height in CSS pixels.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpacerConfig(Integer heightPx) implements CellConfig {}

    /**
     * Collapsible accordion. The body markdown lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccordionConfig(String title, Boolean openByDefault) implements CellConfig {}

    /**
     * Embedded PDF viewer. url is required; height is in CSS pixels.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PdfConfig(String url, Integer heightPx) implements CellConfig {}

    /**
     * Download card pointing at any file URL.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileDownloadConfig(String url, String label, String description) implements CellConfig {}

    /**
     * Countdown to a target date.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CountdownConfig(String targetDate, String label, String sublabel) implements CellConfig {}

    /**
     * Featured event card. Admin-curated; all fields are content-driven.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FeaturedEventConfig(
            String title, String date, String location, String description, String ctaText, String ctaUrl)
            implements CellConfig {}

    /**
     * Curated list of upcoming events.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpcomingEventsConfig(String title, List<EventItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EventItem(String title, String date, String location, String url) {}

    /**
     * Link card pointing at a public KB article.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record KbArticleConfig(Integer articleId, String fallbackTitle) implements CellConfig {}

    /**
     * Static news teaser.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NewsTeaserConfig(String title, String date, String summary, String url, String imageUrl)
            implements CellConfig {}

    /**
     * Link card pointing at another public StationPage.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PageLinkConfig(Integer pageId, String fallbackTitle) implements CellConfig {}

    /**
     * OpenStreetMap embed via configurable coordinates.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MapConfig(Double latitude, Double longitude, Integer zoom, Integer heightPx, String label)
            implements CellConfig {}

    /**
     * Address card with formatted postal address.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddressCardConfig(
            String addressLine, String postalCode, String city, String country, String mapUrl, String label)
            implements CellConfig {}

    /**
     * Partner stations cell. Either renders an explicit list of station UUIDs, or — when
     * {@code autoFillFromPartners} is {@code true} — every federated partner of the host station.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PartnerStationsConfig(String title, List<String> stationUids, Boolean autoFillFromPartners)
            implements CellConfig {}

    /**
     * Member spotlight referencing an existing station member by UUID. The displayed name and
     * avatar are resolved live from the member's record. {@code blurb} is an editor-supplied
     * free-form note. {@code showUserType} (default {@code true}) and {@code showTag}
     * (default {@code false}) independently control whether the user type line and the primary
     * visible tag badge are rendered next to the name.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MemberSpotlightConfig(String memberUid, String blurb, Boolean showUserType, Boolean showTag)
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
            String title,
            JsonNode source,
            MemberListSortBy sortBy,
            Boolean showUserType,
            Boolean showTag,
            Map<String, String> memberDescriptions,
            List<String> memberOrder,
            List<ResolvedMember> resolvedMembers)
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
            String userType,
            String displayTag,
            String displayTagColor,
            String avatarUrl,
            String description) {}

    /**
     * Big-number stats counter row.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatsCounterConfig(List<StatItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatItem(String label, String value, String suffix) {}

    /**
     * Image gallery — list of items, each with its own image hash + alt + subtext.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageGalleryConfig(
            List<GalleryItem> items, Integer columns, GalleryAspectMode aspectMode, Integer maxItemHeightPx)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GalleryItem(String imageHash, String altText, String subtext) {}

    /**
     * Hero banner — full-width image with overlay text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HeroBannerConfig(String imageHash, String headline, String subtitle, String ctaText, String ctaUrl)
            implements CellConfig {}

    /**
     * Past event recap.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PastEventRecapConfig(String title, String date, String imageHash, String summary) implements CellConfig {}

    /**
     * Tabbed sections.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabsConfig(List<TabItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabItem(String title, String body) {}

    /**
     * Achievements / badges showcase.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementsConfig(String title, List<AchievementItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementItem(String title, String description, String year) {}

    /**
     * External link card with OG-style preview metadata supplied by the admin.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExternalLinkCardConfig(
            String url, String title, String description, String imageUrl, ExternalLinkImageDisplay imageDisplay)
            implements CellConfig {}

    /**
     * Blog feed signup card. Renders the station's public blog RSS/Atom feed URLs together
     * with editor-supplied title + description so visitors can subscribe in their reader of
     * choice. Feed URLs are composed at render time from the host station UID.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BlogSignupConfig(String title, String description) implements CellConfig {}

    /**
     * Embedded audio player.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AudioEmbedConfig(String url, String title) implements CellConfig {}

    /**
     * Embedded poll. The cell references a public form (purpose = POLL) by its public UUID and
     * the render component fetches the form definition and submits anonymously via the public
     * form endpoints (concept §3.14, §4.4).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PollEmbedConfig(String formPublicUid, Boolean showResultsAfterVote) implements CellConfig {}

    /**
     * Quiz teaser. References one or more public quiz catalogs by id; the renderer pulls a
     * random question from them and reveals the answer on click (concept §3.15).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuizTeaserConfig(String title, String description, List<Integer> catalogIds) implements CellConfig {}

    /**
     * Contact form call-to-action. References a public form (purpose = CONTACT) by its public
     * UUID; the cell renders that form for in-page anonymous submission. Headline and body are
     * editor-supplied overrides shown above the form fields (concept §3.16).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FormsCtaConfig(String formPublicUid, String headlineOverride, String bodyOverride) implements CellConfig {}

    /**
     * Syntax-highlighted code block. Code lives in cell.content.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CodeBlockConfig(String language) implements CellConfig {}

    /**
     * Cell that contains nested rows. The rows are stored opaquely as JSON nodes so the existing
     * PageRow shape (rows of cells of … nested rows) round-trips without a dedicated record type.
     * The frontend treats this as a recursive RowEditData[].
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NestedRowsConfig(JsonNode rows) implements CellConfig {}

    static CellConfig parse(CellContentType type, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return type.emptyConfig();
        }
        try {
            return MAPPER.readValue(json, type.configClass());
        } catch (Exception e) {
            log.error("Failed to parse CellConfig for type {}: {}", type, json, e);
            return type.emptyConfig();
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
}
