/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of block a container can hold, each with the record its settings are read into.
 *
 * <p>The settings sit beside the kind in their own column, so the record is chosen here rather than
 * by a type name inside the settings. A kind's empty settings are whatever its record reads an empty
 * object as.
 */
public enum CellContentType {
    EMPTY(CellConfig.MarkdownConfig.class),
    MARKDOWN(CellConfig.MarkdownConfig.class),
    IMAGE(CellConfig.ImageConfig.class),
    VIDEO(CellConfig.VideoConfig.class),
    CALLOUT(CellConfig.CalloutConfig.class),
    QUOTE(CellConfig.QuoteConfig.class),
    DIVIDER(CellConfig.DividerConfig.class),
    SPACER(CellConfig.SpacerConfig.class),
    ACCORDION(CellConfig.AccordionConfig.class),
    PDF(CellConfig.PdfConfig.class),
    FILE_DOWNLOAD(CellConfig.FileDownloadConfig.class),
    COUNTDOWN(CellConfig.CountdownConfig.class),
    FEATURED_EVENT(CellConfig.FeaturedEventConfig.class),
    UPCOMING_EVENTS(CellConfig.UpcomingEventsConfig.class),
    KB_ARTICLE(CellConfig.KbArticleConfig.class),
    NEWS_TEASER(CellConfig.NewsTeaserConfig.class),
    PAGE_LINK(CellConfig.PageLinkConfig.class),
    MAP(CellConfig.MapConfig.class),
    ADDRESS_CARD(CellConfig.AddressCardConfig.class),
    PARTNER_STATIONS(CellConfig.PartnerStationsConfig.class),
    MEMBER_SPOTLIGHT(CellConfig.MemberSpotlightConfig.class),
    MEMBER_LIST_SPOTLIGHT(CellConfig.MemberListConfig.class),
    STATS_COUNTER(CellConfig.StatsCounterConfig.class),
    IMAGE_GALLERY(CellConfig.ImageGalleryConfig.class),
    HERO_BANNER(CellConfig.HeroBannerConfig.class),
    PAST_EVENT_RECAP(CellConfig.PastEventRecapConfig.class),
    TABS(CellConfig.TabsConfig.class),
    ACHIEVEMENTS(CellConfig.AchievementsConfig.class),
    EXTERNAL_LINK_CARD(CellConfig.ExternalLinkCardConfig.class),
    BLOG_SIGNUP(CellConfig.BlogSignupConfig.class),
    AUDIO_EMBED(CellConfig.AudioEmbedConfig.class),
    POLL_EMBED(CellConfig.PollEmbedConfig.class),
    QUIZ_TEASER(CellConfig.QuizTeaserConfig.class),
    FORMS_CTA(CellConfig.FormsCtaConfig.class),
    CODE_BLOCK(CellConfig.CodeBlockConfig.class),
    NESTED_ROWS(CellConfig.NestedRowsConfig.class);

    /**
     * The blocks a page may have and an article may not.
     *
     * <p>They are withheld because a blog signup box inside an internal training document, or a
     * member spotlight inside a knowledge-base article, is not something an author is missing.
     * The chooser filters on this as a convenience; the save path enforces it, which is what makes
     * it a rule.
     */
    private static final Set<CellContentType> PAGE_ONLY = EnumSet.of(
            MEMBER_SPOTLIGHT,
            MEMBER_LIST_SPOTLIGHT,
            ACHIEVEMENTS,
            PARTNER_STATIONS,
            BLOG_SIGNUP,
            POLL_EMBED,
            QUIZ_TEASER,
            FORMS_CTA,
            UPCOMING_EVENTS,
            PAST_EVENT_RECAP);

    private final Class<? extends CellConfig> configClass;
    private final CellConfig emptyConfig;

    CellContentType(Class<? extends CellConfig> configClass) {
        this.configClass = configClass;
        this.emptyConfig = CellConfig.emptyOf(configClass);
    }

    public Class<? extends CellConfig> configClass() {
        return configClass;
    }

    public CellConfig emptyConfig() {
        return emptyConfig;
    }

    /**
     * Whether a news entry or a knowledge-base article may be built with this block.
     */
    public boolean availableInArticles() {
        return !PAGE_ONLY.contains(this);
    }
}
