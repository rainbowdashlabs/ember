/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {marked} from 'marked'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import CellMarkdownEditor from './CellMarkdownEditor.vue'
import CellImageEditor from './CellImageEditor.vue'
import CellVideoEditor from './CellVideoEditor.vue'
import CellLayoutEditors from './CellLayoutEditors.vue'
import CellLayoutRender from './CellLayoutRender.vue'
import {
    CellContentType,
    pageImageUrl,
    type CellContentTypeName,
    type ImageConfig,
    type VideoConfig,
} from '@/api/pageManage'

const LAYOUT_KINDS = ['CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD'] as const
type LayoutKind = (typeof LAYOUT_KINDS)[number]
function isLayoutKind(t: string): t is LayoutKind {
    return (LAYOUT_KINDS as readonly string[]).includes(t)
}
import {usePageClipboard} from '@/composables/usePageClipboard'

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
}>()

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
            :kind="cell.contentType as 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'"
            :content="cell.content"
            :config="cell.config as Record<string, unknown>"
        />
    </div>

    <!-- Edit mode -->
    <NeutralContainer v-else class="w-full flex-1 flex flex-col space-y-3">
        <!-- Toolbar -->
        <div class="flex items-center justify-between gap-2">
            <label v-if="canResize" class="flex items-center gap-1 text-xs text-(--text-muted)" :title="t('stationPages.editor.resizeColumn')">
                <input
                    type="number"
                    min="10"
                    max="100"
                    step="1"
                    :value="Math.round(cell.widthPercent)"
                    class="w-14 px-1 py-0.5 rounded border border-(--border) bg-(--bg) text-(--text) text-xs text-right"
                    @change="(e: Event) => { const v = Number((e.target as HTMLInputElement).value); if (v >= 10 && v <= 100) emit('update:width', v) }"
                />
                <span>%</span>
            </label>
            <span v-else/>
            <div class="flex items-center gap-1">
                <IconButton
                    :icon="['fas', 'copy']"
                    :label="t('stationPages.editor.copyCell')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCopy"
                />
                <IconButton
                    :icon="['fas', 'scissors']"
                    :label="t('stationPages.editor.cutCell')"
                    class="text-[var(--text-muted)] hover:text-[var(--text)]"
                    @click="onCut"
                />
                <DeleteButton @click="$emit('delete')"/>
            </div>
        </div>

        <!-- Empty container: content-type chooser. Grid scales 2 → 3 → 4 columns with
             the cell's container width; overflow scrolls vertically if the cell is short. -->
        <div v-if="cell.contentType === CellContentType.EMPTY" class="@container flex-1 flex flex-col gap-3 py-6 px-3 border-2 border-dashed border-[var(--border)] rounded-theme overflow-y-auto">
            <p class="text-sm text-(--text-muted) text-center">{{ t('stationPages.editor.emptyCellHint') }}</p>
            <div class="grid grid-cols-1 @[10rem]:grid-cols-2 @md:grid-cols-3 @xl:grid-cols-4 gap-2">
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.MARKDOWN)"
                >
                    <font-awesome-icon :icon="['fas', 'paragraph']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseMarkdown') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.IMAGE)"
                >
                    <font-awesome-icon :icon="['fas', 'image']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseImage') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.VIDEO)"
                >
                    <font-awesome-icon :icon="['fas', 'play']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseVideo') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.CALLOUT)"
                >
                    <font-awesome-icon :icon="['fas', 'circle-info']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseCallout') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.QUOTE)"
                >
                    <font-awesome-icon :icon="['fas', 'quote-left']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseQuote') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.DIVIDER)"
                >
                    <font-awesome-icon :icon="['fas', 'minus']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseDivider') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.SPACER)"
                >
                    <font-awesome-icon :icon="['fas', 'arrows-up-down']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseSpacer') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.ACCORDION)"
                >
                    <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseAccordion') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.PDF)"
                >
                    <font-awesome-icon :icon="['fas', 'file-pdf']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.choosePdf') }}</span>
                </button>
                <button
                    class="flex flex-col items-center gap-1 rounded-theme border border-[var(--border)] hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4"
                    @click="onContentTypeChange(CellContentType.FILE_DOWNLOAD)"
                >
                    <font-awesome-icon :icon="['fas', 'file']" class="text-lg text-primary"/>
                    <span class="text-xs">{{ t('stationPages.editor.chooseFile') }}</span>
                </button>
                <button
                    v-if="hasClipboard && clipboardType === 'cell'"
                    class="flex flex-col items-center gap-1 rounded-theme border border-primary/40 hover:border-primary hover:bg-primary/5 transition-colors px-3 py-4 text-primary"
                    @click="onPasteHere"
                >
                    <font-awesome-icon :icon="['fas', 'paste']" class="text-lg"/>
                    <span class="text-xs">{{ t('stationPages.editor.pasteCell') }}</span>
                </button>
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

        <!-- Layout types: live preview + config form -->
        <template v-else-if="isLayoutKind(cell.contentType)">
            <div class="rounded-theme border border-dashed border-(--border) p-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/10">
                <CellLayoutRender
                    :kind="cell.contentType as 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'"
                    :content="cell.content"
                    :config="cell.config as Record<string, unknown>"
                />
            </div>
            <CellLayoutEditors
                :kind="cell.contentType as 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'"
                :content="cell.content"
                :config="cell.config as Record<string, unknown>"
                @update:content="updateField('content', $event)"
                @update:config="updateField('config', $event)"
            />
        </template>
    </NeutralContainer>
</template>
