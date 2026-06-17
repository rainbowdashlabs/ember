/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export const CHOOSER_CATEGORIES = [
    {key: 'catBasic', items: [
        {type: 'MARKDOWN', icon: 'paragraph', key: 'chooseMarkdown'},
        {type: 'IMAGE', icon: 'image', key: 'chooseImage'},
        {type: 'VIDEO', icon: 'play', key: 'chooseVideo'},
        {type: 'AUDIO_EMBED', icon: 'paper-plane', key: 'chooseAudio'},
        {type: 'CODE_BLOCK', icon: 'code', key: 'chooseCode'},
    ]},
    {key: 'catLayout', items: [
        {type: 'CALLOUT', icon: 'circle-info', key: 'chooseCallout'},
        {type: 'QUOTE', icon: 'quote-left', key: 'chooseQuote'},
        {type: 'DIVIDER', icon: 'minus', key: 'chooseDivider'},
        {type: 'SPACER', icon: 'arrows-up-down', key: 'chooseSpacer'},
        {type: 'ACCORDION', icon: 'chevron-down', key: 'chooseAccordion'},
        {type: 'TABS', icon: 'table-columns', key: 'chooseTabs'},
        {type: 'HERO_BANNER', icon: 'rocket', key: 'chooseHero'},
    ]},
    {key: 'catFiles', items: [
        {type: 'PDF', icon: 'file-pdf', key: 'choosePdf'},
        {type: 'FILE_DOWNLOAD', icon: 'file', key: 'chooseFile'},
        {type: 'IMAGE_GALLERY', icon: 'image', key: 'chooseGallery'},
    ]},
    {key: 'catEvents', items: [
        {type: 'COUNTDOWN', icon: 'hourglass-half', key: 'chooseCountdown'},
        {type: 'FEATURED_EVENT', icon: 'calendar-days', key: 'chooseFeaturedEvent'},
        {type: 'UPCOMING_EVENTS', icon: 'calendar', key: 'chooseUpcomingEvents'},
        {type: 'PAST_EVENT_RECAP', icon: 'clock-rotate-left', key: 'choosePastEvent'},
    ]},
    {key: 'catLinks', items: [
        {type: 'KB_ARTICLE', icon: 'book', key: 'chooseKbArticle'},
        {type: 'NEWS_TEASER', icon: 'newspaper', key: 'chooseNewsTeaser'},
        {type: 'PAGE_LINK', icon: 'file-lines', key: 'choosePageLink'},
        {type: 'EXTERNAL_LINK_CARD', icon: 'link', key: 'chooseExternalLink'},
    ]},
    {key: 'catGeo', items: [
        {type: 'MAP', icon: 'map-location-dot', key: 'chooseMap'},
        {type: 'ADDRESS_CARD', icon: 'location-dot', key: 'chooseAddress'},
        {type: 'PARTNER_STATIONS', icon: 'handshake', key: 'choosePartners'},
    ]},
    {key: 'catPeople', items: [
        {type: 'MEMBER_SPOTLIGHT', icon: 'user', key: 'chooseMemberSpotlight'},
        {type: 'MEMBER_LIST_SPOTLIGHT', icon: 'users', key: 'chooseMemberList'},
        {type: 'STATS_COUNTER', icon: 'chart-bar', key: 'chooseStats'},
        {type: 'ACHIEVEMENTS', icon: 'trophy', key: 'chooseAchievements'},
    ]},
    {key: 'catEngage', items: [
        {type: 'BLOG_SIGNUP', icon: 'rss', key: 'chooseBlogSignup'},
        {type: 'POLL_EMBED', icon: 'square-poll-vertical', key: 'choosePoll'},
        {type: 'QUIZ_TEASER', icon: 'graduation-cap', key: 'chooseQuiz'},
        {type: 'FORMS_CTA', icon: 'clipboard-list', key: 'chooseFormsCta'},
    ]},
] as const
