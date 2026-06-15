/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {marked} from 'marked'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import CellMarkdownEditor from './CellMarkdownEditor.vue'
import CellImageEditor from './CellImageEditor.vue'
import CellVideoEditor from './CellVideoEditor.vue'
import CellLayoutEditors from './CellLayoutEditors.vue'
import CellLayoutRender from './CellLayoutRender.vue'
import CellActionsMenu from './CellActionsMenu.vue'
import EditorRow, {type RowEditData} from './EditorRow.vue'
import {
    CellContentType,
    pageImageUrl,
    type CellContentTypeName,
    type ImageConfig,
    type VideoConfig,
} from '@/api/pageManage'

import {isLayoutKind, type LayoutKindName} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

const CHOOSER_CATEGORIES = [
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
        {type: 'FEDERATED_EVENT', icon: 'share-nodes', key: 'chooseFederatedEvent'},
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
        {type: 'OFFICERS_ROW', icon: 'users', key: 'chooseOfficers'},
        {type: 'STATS_COUNTER', icon: 'chart-bar', key: 'chooseStats'},
        {type: 'ACHIEVEMENTS', icon: 'trophy', key: 'chooseAchievements'},
    ]},
    {key: 'catEngage', items: [
        {type: 'NEWSLETTER_SIGNUP', icon: 'envelope', key: 'chooseNewsletter'},
        {type: 'POLL_EMBED', icon: 'square-poll-vertical', key: 'choosePoll'},
        {type: 'QUIZ_TEASER', icon: 'graduation-cap', key: 'chooseQuiz'},
        {type: 'APPLICATION_CTA', icon: 'hand-holding', key: 'chooseApplication'},
    ]},
] as const

const NESTED_ROW_PARENT = CellContentType.NESTED_ROWS

export interface CellEditData {
    id: number
    sortOrder: number
    widthPercent: number
    contentType: CellContentTypeName
    content: string
    config: Record<string, unknown>
}

const props = defineProps<{
    cell: CellEditData
    pageId: number
    stationUid: string
    preview: boolean
    canResize?: boolean
    /** Nesting depth (0 at top). Used to soft-warn at deep levels and shrink resize handles. */
    depth?: number
}>()

const depth = computed(() => props.depth ?? 0)
const showDepthWarning = computed(() => depth.value >= 4)

const emit = defineEmits<{
    'update:cell': [cell: CellEditData]
    'update:width': [widthPercent: number]
    delete: []
}>()

const {t} = useI18n()
const {copyCell, cutCell, pasteCell, hasClipboard, clipboardType} = usePageClipboard()

const imageConfig = computed<ImageConfig>(() => (props.cell.config as ImageConfig) ?? {})
const videoConfig = computed<VideoConfig>(() => (props.cell.config as VideoConfig) ?? {})

function updateField<K extends keyof CellEditData>(key: K, value: CellEditData[K]) {
    emit('update:cell', {...props.cell, [key]: value})
}

function onContentTypeChange(type: string) {
    emit('update:cell', {
        ...props.cell,
        contentType: type as CellContentTypeName,
        content: '',
        config: {},
    })
}

function onCopy() {
    copyCell(props.cell)
}

function onCut() {
    cutCell(props.cell, () => emit('delete'))
}

// Inline rendered text + click-to-open full-size markdown editor modal.
const showMarkdownModal = ref(false)
const draftMarkdown = ref('')

function openMarkdownEditor() {
    draftMarkdown.value = props.cell.content
    showMarkdownModal.value = true
}

function applyMarkdown() {
    updateField('content', draftMarkdown.value)
    showMarkdownModal.value = false
}

// Chooser search filter — applied across all categories.
const chooserSearch = ref('')
const filteredCategories = computed(() => {
    const q = chooserSearch.value.trim().toLowerCase()
    if (!q) return CHOOSER_CATEGORIES.map(c => ({...c, items: [...c.items]}))
    return CHOOSER_CATEGORIES
        .map(c => ({
            ...c,
            items: c.items.filter(i => t(`stationPages.editor.${i.key}`).toLowerCase().includes(q)),
        }))
        .filter(c => c.items.length > 0)
})

// "Split into N columns" turns the current cell into a NESTED_ROWS cell. The original cell becomes
// the first child of a one-row nested layout; N-1 empty cells fill the rest.
function splitCell(columns: number) {
    const widthPercent = 100 / columns
    const firstChild: CellEditData = {
        id: 0,
        sortOrder: 0,
        widthPercent,
        contentType: props.cell.contentType,
        content: props.cell.content,
        config: props.cell.config,
    }
    const emptyChildren: CellEditData[] = []
    for (let i = 1; i < columns; i++) {
        emptyChildren.push({
            id: 0,
            sortOrder: i,
            widthPercent,
            contentType: CellContentType.EMPTY,
            content: '',
            config: {},
        })
    }
    const newRow: RowEditData = {id: 0, sortOrder: 0, cells: [firstChild, ...emptyChildren]}
    emit('update:cell', {
        ...props.cell,
        contentType: NESTED_ROW_PARENT,
        content: '',
        config: {rows: [newRow]},
    })
}

// Nested rows wiring: read/write the rows list out of cell.config.
const nestedRows = computed<RowEditData[]>(() => {
    const raw = (props.cell.config as {rows?: RowEditData[]}).rows
    return Array.isArray(raw) ? raw : []
})
function updateNestedRows(rows: RowEditData[]) {
    updateField('config', {...props.cell.config, rows} as Record<string, unknown>)
}
function updateNestedRow(index: number, row: RowEditData) {
    const next = [...nestedRows.value]
    next[index] = row
    updateNestedRows(next)
}
function deleteNestedRow(index: number) {
    updateNestedRows(nestedRows.value.filter((_, i) => i !== index))
}
function moveNestedRow(index: number, delta: number) {
    const next = [...nestedRows.value]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    const moved = next.splice(index, 1)[0]
    if (!moved) return
    next.splice(target, 0, moved)
    updateNestedRows(next)
}
function addNestedRow() {
    const widthPercent = 100
    const row: RowEditData = {
        id: 0,
        sortOrder: nestedRows.value.length,
        cells: [{id: 0, sortOrder: 0, widthPercent, contentType: CellContentType.EMPTY, content: '', config: {}}],
    }
    updateNestedRows([...nestedRows.value, row])
}

function onPasteHere() {
    const data = pasteCell() as CellEditData | null
    if (!data) return
    // Keep this slot's identity (id, sortOrder, widthPercent) and adopt the pasted content/type.
    emit('update:cell', {
        ...props.cell,
        contentType: data.contentType,
        content: data.content,
        config: data.config,
    })
}

const imageUrl = computed(() => {
    if (props.cell.contentType !== CellContentType.IMAGE || !props.cell.content) return ''
    return pageImageUrl(props.stationUid, Number(props.cell.content))
})

const renderedHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN || !props.cell.content) return ''
    return marked.parse(props.cell.content) as string
})

const isYouTube = computed(() => {
    if (props.cell.contentType !== CellContentType.VIDEO) return false
    return /youtube\.com|youtu\.be/.test(props.cell.content)
})

const youtubeEmbedUrl = computed(() => {
    const url = props.cell.content
    const match = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/|youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/)
    if (match) return `https://www.youtube-nocookie.com/embed/${match[1]}`
    return url
})

</script>

<template>
    <!-- Preview mode -->
    <div v-if="preview" class="w-full">
        <!-- Markdown preview -->
        <div
            v-if="cell.contentType === CellContentType.MARKDOWN && cell.content"
            class="markdown-content"
        >
            <div v-html="renderedHtml"/>
        </div>

        <!-- Image preview -->
        <div v-else-if="cell.contentType === CellContentType.IMAGE && imageUrl" class="w-full space-y-1">
            <img
                :src="imageUrl"
                :alt="(imageConfig.altText as string) ?? ''"
                :title="(imageConfig.altText as string) ?? ''"
                :style="{
                    objectFit: (imageConfig.imageFit as string)?.toLowerCase() ?? 'contain',
                    maxHeight: imageConfig.maxHeight ? `${imageConfig.maxHeight}px` : undefined,
                }"
                class="w-full rounded-theme"
            />
            <p v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
                {{ imageConfig.description }}
            </p>
        </div>

        <!-- Video preview -->
        <div v-else-if="cell.contentType === CellContentType.VIDEO && cell.content" class="w-full">
            <div v-if="isYouTube" class="relative w-full" style="padding-bottom: 56.25%">
                <iframe
                    :src="youtubeEmbedUrl"
                    class="absolute inset-0 w-full h-full rounded-theme"
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen
                />
            </div>
            <video
                v-else
                :src="cell.content"
                :autoplay="!!videoConfig.autoplay"
                :loop="!!videoConfig.loop"
                controls
                class="w-full rounded-theme"
            />
        </div>

        <!-- Layout types -->
        <CellLayoutRender
            v-else-if="isLayoutKind(cell.contentType)"
            :kind="cell.contentType as LayoutKindName"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
        />

        <!-- Nested rows in preview -->
        <div v-else-if="cell.contentType === CellContentType.NESTED_ROWS" class="space-y-2">
            <EditorRow
                v-for="(row, ri) in nestedRows" :key="row.id + '-' + ri"
                :row="row"
                :page-id="pageId"
                :station-uid="stationUid"
                :preview="true"
                :is-first="ri === 0"
                :is-last="ri === nestedRows.length - 1"
                :depth="depth + 1"
            />
        </div>
    </div>

    <!-- Edit mode -->
    <NeutralContainer v-else class="group relative w-full flex-1 flex flex-col">
        <CellActionsMenu
            :label="cell.contentType === 'EMPTY' ? undefined : t(`stationPages.contentType.${cell.contentType.toLowerCase()}`)"
            :width-percent="cell.widthPercent"
            :can-resize="canResize"
            @copy="onCopy"
            @cut="onCut"
            @delete="$emit('delete')"
            @split="splitCell"
            @update:width-percent="emit('update:width', $event)"
        />

        <p v-if="showDepthWarning" class="text-[10px] text-error italic mb-2">
            <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="mr-1"/>
            {{ t('stationPages.editor.depthWarning') }}
        </p>

        <!-- Empty container: search + categorized content-type chooser. -->
        <div v-if="cell.contentType === CellContentType.EMPTY" class="@container flex flex-col gap-3 py-4 px-3 border-2 border-dashed border-(--border) rounded-theme">
            <p class="text-sm text-(--text-muted) text-center">{{ t('stationPages.editor.emptyCellHint') }}</p>
            <TextInput
                v-model="chooserSearch"
                :placeholder="t('stationPages.editor.chooserSearch')"
                class="w-full"
            />
            <div class="max-h-96 overflow-y-auto -mx-1 px-1 space-y-3">
                <button
                    v-if="hasClipboard && clipboardType === 'cell'"
                    class="w-full flex items-center justify-center gap-2 rounded-theme border border-primary/40 hover:border-primary hover:bg-primary/5 transition-colors px-3 py-2 text-primary text-sm"
                    @click="onPasteHere"
                >
                    <font-awesome-icon :icon="['fas', 'paste']"/>
                    {{ t('stationPages.editor.pasteCell') }}
                </button>
                <div v-for="cat in filteredCategories" :key="cat.key" class="space-y-1">
                    <p class="text-[10px] uppercase tracking-wider text-(--text-muted) font-semibold">{{ t(`stationPages.editor.${cat.key}`) }}</p>
                    <div class="grid grid-cols-1 @[10rem]:grid-cols-2 @md:grid-cols-3 @xl:grid-cols-4 gap-2">
                        <button
                            v-for="entry in cat.items" :key="entry.type"
                            class="flex flex-col items-center gap-1 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-3 py-3"
                            @click="onContentTypeChange(entry.type)"
                        >
                            <font-awesome-icon :icon="['fas', entry.icon]" class="text-lg text-primary"/>
                            <span class="text-xs text-center">{{ t(`stationPages.editor.${entry.key}`) }}</span>
                        </button>
                    </div>
                </div>
            </div>
        </div>

        <!-- Markdown: rendered preview that opens the full-size editor on click. -->
        <div
            v-if="cell.contentType === CellContentType.MARKDOWN"
            class="flex-1 cursor-pointer rounded-theme border border-dashed border-transparent hover:border-(--border) p-2 transition-colors group"
            :title="t('stationPages.editor.editMarkdown')"
            @click="openMarkdownEditor"
        >
            <div v-if="cell.content" class="markdown-content" v-html="renderedHtml"/>
            <p v-else class="text-sm text-(--text-muted) italic">{{ t('stationPages.editor.markdownEmpty') }}</p>
        </div>

        <Modal v-if="cell.contentType === CellContentType.MARKDOWN" v-model="showMarkdownModal" size="xl">
            <div class="space-y-3 flex flex-col h-[80vh]">
                <SectionHeader>{{ t('stationPages.editor.editMarkdown') }}</SectionHeader>
                <CellMarkdownEditor
                    class="flex-1 flex flex-col min-h-0"
                    :content="draftMarkdown"
                    @update:content="draftMarkdown = $event"
                />
                <div class="flex justify-end">
                    <PrimaryButton @click="applyMarkdown">{{ t('common.save') }}</PrimaryButton>
                </div>
            </div>
        </Modal>

        <!-- Image editor -->
        <CellImageEditor
            v-else-if="cell.contentType === CellContentType.IMAGE"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
            :page-id="pageId"
            :station-uid="stationUid"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />

        <!-- Video editor -->
        <CellVideoEditor
            v-else-if="cell.contentType === CellContentType.VIDEO"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
            @update:content="updateField('content', $event)"
            @update:config="updateField('config', $event)"
        />

        <!-- NESTED_ROWS: render each nested row recursively. -->
        <div v-else-if="cell.contentType === CellContentType.NESTED_ROWS" class="space-y-2 border-l-2 border-primary/30 pl-2">
            <EditorRow
                v-for="(row, ri) in nestedRows" :key="row.id + '-' + ri"
                :row="row"
                :page-id="pageId"
                :station-uid="stationUid"
                :preview="false"
                :is-first="ri === 0"
                :is-last="ri === nestedRows.length - 1"
                :depth="depth + 1"
                @update:row="updateNestedRow(ri, $event)"
                @delete="deleteNestedRow(ri)"
                @move-up="moveNestedRow(ri, -1)"
                @move-down="moveNestedRow(ri, 1)"
            />
            <div class="flex items-center justify-center gap-2 py-1">
                <div class="flex-1 h-px bg-(--border)"/>
                <SecondaryButton compact @click="addNestedRow">
                    <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
                    {{ t('stationPages.editor.addRow') }}
                </SecondaryButton>
                <div class="flex-1 h-px bg-(--border)"/>
            </div>
        </div>

        <!-- Layout types: live preview + config form -->
        <template v-else-if="isLayoutKind(cell.contentType)">
            <div class="rounded-theme border border-dashed border-(--border) p-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/10">
                <CellLayoutRender
                    :kind="cell.contentType as LayoutKindName"
                    :content="cell.content"
                    :config="cell.config as Record<string, unknown>"
                    :station-uid="stationUid"
                />
            </div>
            <CellLayoutEditors
                :kind="cell.contentType as LayoutKindName"
                :content="cell.content"
                :config="cell.config as Record<string, unknown>"
                :page-id="pageId"
                :station-uid="stationUid"
                @update:content="updateField('content', $event)"
                @update:config="updateField('config', $event)"
            />
        </template>
    </NeutralContainer>
</template>
