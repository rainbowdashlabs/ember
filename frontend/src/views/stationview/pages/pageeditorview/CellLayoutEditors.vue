/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {LayoutKindName} from '@/api/pageManage'
import CellEventEditors from './CellEventEditors.vue'
import CellQuizEditor from './CellQuizEditor.vue'
import CellMemberListEditor from './CellMemberListEditor.vue'
import CalloutEditor from './editors/CalloutEditor.vue'
import QuoteEditor from './editors/QuoteEditor.vue'
import DividerEditor from './editors/DividerEditor.vue'
import SpacerEditor from './editors/SpacerEditor.vue'
import AccordionEditor from './editors/AccordionEditor.vue'
import PdfEditor from './editors/PdfEditor.vue'
import FileDownloadEditor from './editors/FileDownloadEditor.vue'
import CountdownEditor from './editors/CountdownEditor.vue'
import PartnerStationsEditor from './editors/PartnerStationsEditor.vue'
import StatsCounterEditor from './editors/StatsCounterEditor.vue'
import TabsEditor from './editors/TabsEditor.vue'
import AchievementsEditor from './editors/AchievementsEditor.vue'
import ImageGalleryEditor from './editors/ImageGalleryEditor.vue'
import KbArticleEditor from './editors/KbArticleEditor.vue'
import NewsTeaserEditor from './editors/NewsTeaserEditor.vue'
import PageLinkEditor from './editors/PageLinkEditor.vue'
import MapEditor from './editors/MapEditor.vue'
import AddressCardEditor from './editors/AddressCardEditor.vue'
import MemberSpotlightEditor from './editors/MemberSpotlightEditor.vue'
import HeroBannerEditor from './editors/HeroBannerEditor.vue'
import ExternalLinkCardEditor from './editors/ExternalLinkCardEditor.vue'
import BlogSignupEditor from './editors/BlogSignupEditor.vue'
import AudioEmbedEditor from './editors/AudioEmbedEditor.vue'
import PollEmbedEditor from './editors/PollEmbedEditor.vue'
import FormsCtaEditor from './editors/FormsCtaEditor.vue'
import CodeBlockEditor from './editors/CodeBlockEditor.vue'

/**
 * Top-level dispatcher for the page-editor configuration panels. Each cell type lives in its own
 * component under {@code ./editors/} (or the grouped event / member-list / quiz editors next to
 * this file); this file is intentionally just a switch keyed by {@code kind}, with a uniform
 * {@code (config, [content], [stationUid], [pageId])} prop shape and the two emits
 * {@code update:config} / {@code update:content} across every editor.
 */
const content = defineModel<string>('content', {required: true})
const config = defineModel<Record<string, unknown>>('config', {required: true})

defineProps<{
    kind: LayoutKindName
    pageId: number
    stationUid: string
}>()
</script>

<template>
    <div class="space-y-3">
        <CalloutEditor v-if="kind === 'CALLOUT'" :config="config" :content="content"
                       @update:config="config = $event" @update:content="content = $event"/>
        <QuoteEditor v-else-if="kind === 'QUOTE'" :config="config" :content="content" :station-uid="stationUid"
                     @update:config="config = $event" @update:content="content = $event"/>
        <DividerEditor v-else-if="kind === 'DIVIDER'" :config="config" @update:config="config = $event"/>
        <SpacerEditor v-else-if="kind === 'SPACER'" :config="config" @update:config="config = $event"/>
        <AccordionEditor v-else-if="kind === 'ACCORDION'" :config="config" :content="content"
                         @update:config="config = $event" @update:content="content = $event"/>
        <PdfEditor v-else-if="kind === 'PDF'" :config="config" :station-uid="stationUid"
                   @update:config="config = $event"/>
        <FileDownloadEditor v-else-if="kind === 'FILE_DOWNLOAD'" :config="config" :station-uid="stationUid"
                            @update:config="config = $event"/>
        <CountdownEditor v-else-if="kind === 'COUNTDOWN'" :config="config" @update:config="config = $event"/>

        <CellEventEditors
            v-else-if="kind === 'FEATURED_EVENT' || kind === 'UPCOMING_EVENTS' || kind === 'PAST_EVENT_RECAP'"
            :kind="kind" :config="config" :station-uid="stationUid"
            @update:config="config = $event"
        />

        <PartnerStationsEditor v-else-if="kind === 'PARTNER_STATIONS'" :config="config" :station-uid="stationUid"
                               @update:config="config = $event"/>

        <CellMemberListEditor v-else-if="kind === 'MEMBER_LIST_SPOTLIGHT'" :config="config"
                              @update:config="config = $event"/>

        <StatsCounterEditor v-else-if="kind === 'STATS_COUNTER'" :config="config" @update:config="config = $event"/>
        <TabsEditor v-else-if="kind === 'TABS'" :config="config" @update:config="config = $event"/>
        <AchievementsEditor v-else-if="kind === 'ACHIEVEMENTS'" :config="config" @update:config="config = $event"/>
        <ImageGalleryEditor v-else-if="kind === 'IMAGE_GALLERY'" :config="config" :page-id="pageId" :station-uid="stationUid"
                            @update:config="config = $event"/>
        <KbArticleEditor v-else-if="kind === 'KB_ARTICLE'" :config="config" :station-uid="stationUid"
                         @update:config="config = $event"/>
        <NewsTeaserEditor v-else-if="kind === 'NEWS_TEASER'" :config="config" :station-uid="stationUid"
                          @update:config="config = $event"/>
        <PageLinkEditor v-else-if="kind === 'PAGE_LINK'" :config="config" :station-uid="stationUid"
                        @update:config="config = $event"/>
        <MapEditor v-else-if="kind === 'MAP'" :config="config" @update:config="config = $event"/>
        <AddressCardEditor v-else-if="kind === 'ADDRESS_CARD'" :config="config" @update:config="config = $event"/>
        <MemberSpotlightEditor v-else-if="kind === 'MEMBER_SPOTLIGHT'" :config="config" @update:config="config = $event"/>
        <HeroBannerEditor v-else-if="kind === 'HERO_BANNER'" :config="config" :page-id="pageId" :station-uid="stationUid"
                          @update:config="config = $event"/>
        <ExternalLinkCardEditor v-else-if="kind === 'EXTERNAL_LINK_CARD'" :config="config" :station-uid="stationUid"
                                @update:config="config = $event"/>
        <BlogSignupEditor v-else-if="kind === 'BLOG_SIGNUP'" :config="config" @update:config="config = $event"/>
        <AudioEmbedEditor v-else-if="kind === 'AUDIO_EMBED'" :config="config" :station-uid="stationUid"
                          @update:config="config = $event"/>
        <PollEmbedEditor v-else-if="kind === 'POLL_EMBED'" :config="config" @update:config="config = $event"/>
        <CellQuizEditor v-else-if="kind === 'QUIZ_TEASER'" :config="config" :station-uid="stationUid"
                        @update:config="config = $event"/>
        <FormsCtaEditor v-else-if="kind === 'FORMS_CTA'" :config="config" @update:config="config = $event"/>
        <CodeBlockEditor v-else-if="kind === 'CODE_BLOCK'" :config="config" :content="content"
                         @update:config="config = $event" @update:content="content = $event"/>
    </div>
</template>
