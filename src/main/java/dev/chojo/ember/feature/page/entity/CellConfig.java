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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

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

    record MarkdownConfig() implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageConfig(ImageFit imageFit, String altText, Integer maxHeight, String description)
            implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VideoConfig(Boolean autoplay, Boolean loop) implements CellConfig {}

    /** Callout box. The body text lives in cell.content (markdown). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CalloutConfig(CalloutVariant variant, String title) implements CellConfig {}

    /** Quote block. The quote text lives in cell.content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuoteConfig(String author, String attributionUrl) implements CellConfig {}

    /** Horizontal divider with optional centred label. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DividerConfig(String label) implements CellConfig {}

    /** Vertical spacer. Height in CSS pixels. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SpacerConfig(Integer heightPx) implements CellConfig {}

    /** Collapsible accordion. The body markdown lives in cell.content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AccordionConfig(String title, Boolean openByDefault) implements CellConfig {}

    /** Embedded PDF viewer. url is required; height is in CSS pixels. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PdfConfig(String url, Integer heightPx) implements CellConfig {}

    /** Download card pointing at any file URL. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileDownloadConfig(String url, String label, String description) implements CellConfig {}

    /** Countdown to a target date. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CountdownConfig(String targetDate, String label, String sublabel) implements CellConfig {}

    /** Featured event card. Admin-curated; all fields are content-driven. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FeaturedEventConfig(
            String title, String date, String location, String description, String ctaText, String ctaUrl)
            implements CellConfig {}

    /** Curated list of upcoming events. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpcomingEventsConfig(String title, java.util.List<EventItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EventItem(String title, String date, String location, String url) {}

    /** Link card pointing at a public KB article. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record KbArticleConfig(Integer articleId, String fallbackTitle) implements CellConfig {}

    /** Static news teaser. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NewsTeaserConfig(String title, String date, String summary, String url, String imageUrl)
            implements CellConfig {}

    /** Link card pointing at another public StationPage. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PageLinkConfig(Integer pageId, String fallbackTitle) implements CellConfig {}

    /** OpenStreetMap embed via configurable coordinates. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MapConfig(Double latitude, Double longitude, Integer zoom, Integer heightPx, String label)
            implements CellConfig {}

    /** Address card with formatted postal address. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddressCardConfig(
            String addressLine, String postalCode, String city, String country, String mapUrl, String label)
            implements CellConfig {}

    /** Manually curated list of partner stations. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PartnerStationsConfig(String title, java.util.List<PartnerStationItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PartnerStationItem(String name, String url, Double distanceKm) {}

    /** Federated event reference (manually curated). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FederatedEventConfig(String title, String date, String partnerName, String url, String description)
            implements CellConfig {}

    /** Member spotlight (manually curated). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MemberSpotlightConfig(String name, String role, String imageUrl, String blurb) implements CellConfig {}

    /** Officers row — list of named roles. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OfficersRowConfig(String title, java.util.List<OfficerItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OfficerItem(String name, String role, String imageUrl) {}

    /** Big-number stats counter row. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatsCounterConfig(java.util.List<StatItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StatItem(String label, String value, String suffix) {}

    /** Image gallery using uploaded page images. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageGalleryConfig(java.util.List<Integer> imageIds, Integer columns) implements CellConfig {}

    /** Hero banner — full-width image with overlay text. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HeroBannerConfig(Integer imageId, String headline, String subtitle, String ctaText, String ctaUrl)
            implements CellConfig {}

    /** Past event recap. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PastEventRecapConfig(String title, String date, Integer imageId, String summary) implements CellConfig {}

    /** Tabbed sections. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabsConfig(java.util.List<TabItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TabItem(String title, String body) {}

    /** Achievements / badges showcase. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementsConfig(String title, java.util.List<AchievementItem> items) implements CellConfig {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AchievementItem(String title, String description, String year) {}

    /** External link card with OG-style preview metadata supplied by the admin. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ExternalLinkCardConfig(String url, String title, String description, String imageUrl)
            implements CellConfig {}

    /** Newsletter / feed signup card. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record NewsletterSignupConfig(String title, String description, String feedUrl) implements CellConfig {}

    /** Embedded audio player. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AudioEmbedConfig(String url, String title) implements CellConfig {}

    /** Poll embed teaser. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PollEmbedConfig(String title, String description, String url) implements CellConfig {}

    /** Quiz teaser. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuizTeaserConfig(String title, String description, String ctaText, String url) implements CellConfig {}

    /** Application / "join us" call-to-action. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ApplicationCtaConfig(String headline, String body, String ctaText, String ctaUrl) implements CellConfig {}

    /** Syntax-highlighted code block. Code lives in cell.content. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CodeBlockConfig(String language) implements CellConfig {}

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
