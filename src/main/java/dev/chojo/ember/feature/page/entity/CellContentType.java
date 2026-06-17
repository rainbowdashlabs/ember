/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import dev.chojo.ember.feature.page.entity.CellConfig.BlogSignupConfig;

import static dev.chojo.ember.feature.page.entity.CellConfig.AccordionConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.AchievementsConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.AddressCardConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.AudioEmbedConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.CalloutConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.CodeBlockConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.CountdownConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.DividerConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.ExternalLinkCardConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.FeaturedEventConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.FileDownloadConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.FormsCtaConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.HeroBannerConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.ImageConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.ImageGalleryConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.KbArticleConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.MapConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.MarkdownConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.MemberListConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.MemberSpotlightConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.NestedRowsConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.NewsTeaserConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.PageLinkConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.PartnerStationsConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.PastEventRecapConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.PdfConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.PollEmbedConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.QuizTeaserConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.QuoteConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.SpacerConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.StatsCounterConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.TabsConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.UpcomingEventsConfig;
import static dev.chojo.ember.feature.page.entity.CellConfig.VideoConfig;

public enum CellContentType {
    EMPTY(MarkdownConfig.class, new MarkdownConfig()),
    MARKDOWN(MarkdownConfig.class, new MarkdownConfig()),
    IMAGE(ImageConfig.class, new ImageConfig(null, null, null, null, null, null, null, null, null, null, null)),
    VIDEO(VideoConfig.class, new VideoConfig(null, null)),
    CALLOUT(CalloutConfig.class, new CalloutConfig(null, null)),
    QUOTE(QuoteConfig.class, new QuoteConfig(null, null)),
    DIVIDER(DividerConfig.class, new DividerConfig(null)),
    SPACER(SpacerConfig.class, new SpacerConfig(null)),
    ACCORDION(AccordionConfig.class, new AccordionConfig(null, null)),
    PDF(PdfConfig.class, new PdfConfig(null, null)),
    FILE_DOWNLOAD(FileDownloadConfig.class, new FileDownloadConfig(null, null, null)),
    COUNTDOWN(CountdownConfig.class, new CountdownConfig(null, null, null)),
    FEATURED_EVENT(FeaturedEventConfig.class, new FeaturedEventConfig(null, null, null, null, null, null)),
    UPCOMING_EVENTS(UpcomingEventsConfig.class, new UpcomingEventsConfig(null, null)),
    KB_ARTICLE(KbArticleConfig.class, new KbArticleConfig(null, null)),
    NEWS_TEASER(NewsTeaserConfig.class, new NewsTeaserConfig(null, null, null, null, null)),
    PAGE_LINK(PageLinkConfig.class, new PageLinkConfig(null, null)),
    MAP(MapConfig.class, new MapConfig(null, null, null, null, null)),
    ADDRESS_CARD(AddressCardConfig.class, new AddressCardConfig(null, null, null, null, null, null)),
    PARTNER_STATIONS(PartnerStationsConfig.class, new PartnerStationsConfig(null, null, null)),
    MEMBER_SPOTLIGHT(MemberSpotlightConfig.class, new MemberSpotlightConfig(null, null, null, null)),
    MEMBER_LIST_SPOTLIGHT(MemberListConfig.class, new MemberListConfig(null, null, null, null, null, null, null, null)),
    STATS_COUNTER(StatsCounterConfig.class, new StatsCounterConfig(null)),
    IMAGE_GALLERY(ImageGalleryConfig.class, new ImageGalleryConfig(null, null, null, null)),
    HERO_BANNER(HeroBannerConfig.class, new HeroBannerConfig(null, null, null, null, null)),
    PAST_EVENT_RECAP(PastEventRecapConfig.class, new PastEventRecapConfig(null, null, null, null)),
    TABS(TabsConfig.class, new TabsConfig(null)),
    ACHIEVEMENTS(AchievementsConfig.class, new AchievementsConfig(null, null)),
    EXTERNAL_LINK_CARD(ExternalLinkCardConfig.class, new ExternalLinkCardConfig(null, null, null, null, null)),
    BLOG_SIGNUP(BlogSignupConfig.class, new BlogSignupConfig(null, null)),
    AUDIO_EMBED(AudioEmbedConfig.class, new AudioEmbedConfig(null, null)),
    POLL_EMBED(PollEmbedConfig.class, new PollEmbedConfig(null, null)),
    QUIZ_TEASER(QuizTeaserConfig.class, new QuizTeaserConfig(null, null, null)),
    FORMS_CTA(FormsCtaConfig.class, new FormsCtaConfig(null, null, null)),
    CODE_BLOCK(CodeBlockConfig.class, new CodeBlockConfig(null)),
    NESTED_ROWS(NestedRowsConfig.class, new NestedRowsConfig(null));

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
