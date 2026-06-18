/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {
    LayoutKindName,
    CalloutConfig, QuoteConfig, DividerConfig, SpacerConfig, AccordionConfig,
    PdfConfig, FileDownloadConfig, CountdownConfig,
    KbArticleConfig, NewsTeaserConfig, PageLinkConfig, MapConfig, AddressCardConfig,
    PartnerStationsConfig,
    StatsCounterConfig, ImageGalleryConfig, HeroBannerConfig,
    TabsConfig, AchievementsConfig, ExternalLinkCardConfig,
    BlogSignupConfig, AudioEmbedConfig, PollEmbedConfig,
    QuizTeaserConfig, FormsCtaConfig, CodeBlockConfig,
} from '@/api/pageManage'
import CellLayoutRenderEvents from './CellLayoutRenderEvents.vue'
import CellPeopleRenders from './CellPeopleRenders.vue'
import CalloutCell from './cells/CalloutCell.vue'
import QuoteCell from './cells/QuoteCell.vue'
import DividerCell from './cells/DividerCell.vue'
import SpacerCell from './cells/SpacerCell.vue'
import AccordionCell from './cells/AccordionCell.vue'
import PdfCell from './cells/PdfCell.vue'
import FileDownloadCell from './cells/FileDownloadCell.vue'
import CountdownCell from './cells/CountdownCell.vue'
import KbArticleCell from './cells/KbArticleCell.vue'
import NewsTeaserCell from './cells/NewsTeaserCell.vue'
import PageLinkCell from './cells/PageLinkCell.vue'
import MapCell from './cells/MapCell.vue'
import AddressCardCell from './cells/AddressCardCell.vue'
import PartnerStationsCell from './cells/PartnerStationsCell.vue'
import StatsCounterCell from './cells/StatsCounterCell.vue'
import ImageGalleryCell from './cells/ImageGalleryCell.vue'
import HeroBannerCell from './cells/HeroBannerCell.vue'
import TabsCell from './cells/TabsCell.vue'
import AchievementsCell from './cells/AchievementsCell.vue'
import ExternalLinkCardCell from './cells/ExternalLinkCardCell.vue'
import BlogSignupCell from './cells/BlogSignupCell.vue'
import AudioEmbedCell from './cells/AudioEmbedCell.vue'
import PollEmbedCell from './cells/PollEmbedCell.vue'
import QuizTeaserCell from './cells/QuizTeaserCell.vue'
import FormsCtaCell from './cells/FormsCtaCell.vue'
import CodeBlockCell from './cells/CodeBlockCell.vue'

/**
 * Top-level dispatcher for the public page renderer. Each cell type lives in its own component
 * under {@code ./cells/} (or in the grouped event renderer {@code CellLayoutRenderEvents} and
 * the member-spotlight renderer {@code CellPeopleRenders}); this file is intentionally just a
 * switch keyed by {@code kind}, with a uniform {@code (config, stationUid, content)} prop
 * shape across every cell.
 */
const props = defineProps<{
    kind: LayoutKindName
    content: string
    config: Record<string, unknown>
    stationUid?: string
}>()

function asCfg<T>(): T { return props.config as T }
</script>

<template>
    <CalloutCell v-if="kind === 'CALLOUT'" :config="asCfg<CalloutConfig>()" :content="content"/>
    <QuoteCell v-else-if="kind === 'QUOTE'" :config="asCfg<QuoteConfig>()" :content="content"/>
    <DividerCell v-else-if="kind === 'DIVIDER'" :config="asCfg<DividerConfig>()"/>
    <SpacerCell v-else-if="kind === 'SPACER'" :config="asCfg<SpacerConfig>()"/>
    <AccordionCell v-else-if="kind === 'ACCORDION'" :config="asCfg<AccordionConfig>()" :content="content"/>
    <PdfCell v-else-if="kind === 'PDF'" :config="asCfg<PdfConfig>()"/>
    <FileDownloadCell v-else-if="kind === 'FILE_DOWNLOAD'" :config="asCfg<FileDownloadConfig>()"/>
    <CountdownCell v-else-if="kind === 'COUNTDOWN'" :config="asCfg<CountdownConfig>()"/>

    <CellLayoutRenderEvents
        v-else-if="kind === 'FEATURED_EVENT' || kind === 'UPCOMING_EVENTS' || kind === 'PAST_EVENT_RECAP'"
        :kind="kind" :config="config" :station-uid="stationUid"
    />

    <KbArticleCell v-else-if="kind === 'KB_ARTICLE'" :config="asCfg<KbArticleConfig>()" :station-uid="stationUid"/>
    <NewsTeaserCell v-else-if="kind === 'NEWS_TEASER'" :config="asCfg<NewsTeaserConfig>()" :station-uid="stationUid"/>
    <PageLinkCell v-else-if="kind === 'PAGE_LINK'" :config="asCfg<PageLinkConfig>()" :station-uid="stationUid"/>
    <MapCell v-else-if="kind === 'MAP'" :config="asCfg<MapConfig>()"/>
    <AddressCardCell v-else-if="kind === 'ADDRESS_CARD'" :config="asCfg<AddressCardConfig>()"/>
    <PartnerStationsCell v-else-if="kind === 'PARTNER_STATIONS'" :config="asCfg<PartnerStationsConfig>()" :station-uid="stationUid"/>

    <CellPeopleRenders
        v-else-if="kind === 'MEMBER_SPOTLIGHT' || kind === 'MEMBER_LIST_SPOTLIGHT'"
        :kind="kind" :config="config"
    />

    <StatsCounterCell v-else-if="kind === 'STATS_COUNTER'" :config="asCfg<StatsCounterConfig>()"/>
    <ImageGalleryCell v-else-if="kind === 'IMAGE_GALLERY'" :config="asCfg<ImageGalleryConfig>()" :station-uid="stationUid"/>
    <HeroBannerCell v-else-if="kind === 'HERO_BANNER'" :config="asCfg<HeroBannerConfig>()" :station-uid="stationUid"/>
    <TabsCell v-else-if="kind === 'TABS'" :config="asCfg<TabsConfig>()"/>
    <AchievementsCell v-else-if="kind === 'ACHIEVEMENTS'" :config="asCfg<AchievementsConfig>()"/>
    <ExternalLinkCardCell v-else-if="kind === 'EXTERNAL_LINK_CARD'" :config="asCfg<ExternalLinkCardConfig>()"/>

    <BlogSignupCell v-else-if="kind === 'BLOG_SIGNUP'" :config="asCfg<BlogSignupConfig>()" :station-uid="stationUid"/>
    <AudioEmbedCell v-else-if="kind === 'AUDIO_EMBED'" :config="asCfg<AudioEmbedConfig>()"/>
    <PollEmbedCell v-else-if="kind === 'POLL_EMBED'" :config="asCfg<PollEmbedConfig>()" :station-uid="stationUid"/>
    <QuizTeaserCell v-else-if="kind === 'QUIZ_TEASER'" :config="asCfg<QuizTeaserConfig>()" :station-uid="stationUid"/>
    <FormsCtaCell v-else-if="kind === 'FORMS_CTA'" :config="asCfg<FormsCtaConfig>()" :station-uid="stationUid"/>
    <CodeBlockCell v-else-if="kind === 'CODE_BLOCK'" :config="asCfg<CodeBlockConfig>()" :content="content"/>
</template>
