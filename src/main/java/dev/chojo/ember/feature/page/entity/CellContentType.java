/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

public enum CellContentType {
    EMPTY(CellConfig.MarkdownConfig.class, new CellConfig.MarkdownConfig()),
    MARKDOWN(CellConfig.MarkdownConfig.class, new CellConfig.MarkdownConfig()),
    IMAGE(CellConfig.ImageConfig.class, new CellConfig.ImageConfig(null, null, null, null)),
    VIDEO(CellConfig.VideoConfig.class, new CellConfig.VideoConfig(null, null)),
    CALLOUT(CellConfig.CalloutConfig.class, new CellConfig.CalloutConfig(null, null)),
    QUOTE(CellConfig.QuoteConfig.class, new CellConfig.QuoteConfig(null, null)),
    DIVIDER(CellConfig.DividerConfig.class, new CellConfig.DividerConfig(null)),
    SPACER(CellConfig.SpacerConfig.class, new CellConfig.SpacerConfig(null)),
    ACCORDION(CellConfig.AccordionConfig.class, new CellConfig.AccordionConfig(null, null)),
    PDF(CellConfig.PdfConfig.class, new CellConfig.PdfConfig(null, null)),
    FILE_DOWNLOAD(CellConfig.FileDownloadConfig.class, new CellConfig.FileDownloadConfig(null, null, null)),
    COUNTDOWN(CellConfig.CountdownConfig.class, new CellConfig.CountdownConfig(null, null, null)),
    FEATURED_EVENT(
            CellConfig.FeaturedEventConfig.class,
            new CellConfig.FeaturedEventConfig(null, null, null, null, null, null)),
    UPCOMING_EVENTS(CellConfig.UpcomingEventsConfig.class, new CellConfig.UpcomingEventsConfig(null, null)),
    KB_ARTICLE(CellConfig.KbArticleConfig.class, new CellConfig.KbArticleConfig(null, null)),
    NEWS_TEASER(CellConfig.NewsTeaserConfig.class, new CellConfig.NewsTeaserConfig(null, null, null, null, null)),
    PAGE_LINK(CellConfig.PageLinkConfig.class, new CellConfig.PageLinkConfig(null, null)),
    MAP(CellConfig.MapConfig.class, new CellConfig.MapConfig(null, null, null, null, null)),
    ADDRESS_CARD(
            CellConfig.AddressCardConfig.class, new CellConfig.AddressCardConfig(null, null, null, null, null, null)),
    PARTNER_STATIONS(CellConfig.PartnerStationsConfig.class, new CellConfig.PartnerStationsConfig(null, null)),
    FEDERATED_EVENT(
            CellConfig.FederatedEventConfig.class, new CellConfig.FederatedEventConfig(null, null, null, null, null)),
    MEMBER_SPOTLIGHT(
            CellConfig.MemberSpotlightConfig.class, new CellConfig.MemberSpotlightConfig(null, null, null, null)),
    OFFICERS_ROW(CellConfig.OfficersRowConfig.class, new CellConfig.OfficersRowConfig(null, null)),
    STATS_COUNTER(CellConfig.StatsCounterConfig.class, new CellConfig.StatsCounterConfig(null)),
    IMAGE_GALLERY(CellConfig.ImageGalleryConfig.class, new CellConfig.ImageGalleryConfig(null, null)),
    HERO_BANNER(CellConfig.HeroBannerConfig.class, new CellConfig.HeroBannerConfig(null, null, null, null, null)),
    PAST_EVENT_RECAP(
            CellConfig.PastEventRecapConfig.class, new CellConfig.PastEventRecapConfig(null, null, null, null)),
    TABS(CellConfig.TabsConfig.class, new CellConfig.TabsConfig(null)),
    ACHIEVEMENTS(CellConfig.AchievementsConfig.class, new CellConfig.AchievementsConfig(null, null)),
    EXTERNAL_LINK_CARD(
            CellConfig.ExternalLinkCardConfig.class, new CellConfig.ExternalLinkCardConfig(null, null, null, null)),
    NEWSLETTER_SIGNUP(CellConfig.NewsletterSignupConfig.class, new CellConfig.NewsletterSignupConfig(null, null, null)),
    AUDIO_EMBED(CellConfig.AudioEmbedConfig.class, new CellConfig.AudioEmbedConfig(null, null)),
    POLL_EMBED(CellConfig.PollEmbedConfig.class, new CellConfig.PollEmbedConfig(null, null, null)),
    QUIZ_TEASER(CellConfig.QuizTeaserConfig.class, new CellConfig.QuizTeaserConfig(null, null, null, null)),
    APPLICATION_CTA(CellConfig.ApplicationCtaConfig.class, new CellConfig.ApplicationCtaConfig(null, null, null, null)),
    CODE_BLOCK(CellConfig.CodeBlockConfig.class, new CellConfig.CodeBlockConfig(null));

    private final Class<? extends CellConfig> configClass;
    private final CellConfig emptyConfig;

    CellContentType(Class<? extends CellConfig> configClass, CellConfig emptyConfig) {
        this.configClass = configClass;
        this.emptyConfig = emptyConfig;
    }

    public Class<? extends CellConfig> configClass() {
        return configClass;
    }

    public CellConfig emptyConfig() {
        return emptyConfig;
    }
}
