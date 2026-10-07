/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {configOf, isLayoutKind, type LayoutKindName} from '@/api/pageManage'
import {CellContentType, type ContentCell as CellData, type ImageConfig} from '@/api/generated/schema'
import CellLayoutRender from '@/components/content/blockeditor/CellLayoutRender.vue'
import CellImagePreview from '@/components/content/blockeditor/CellImagePreview.vue'
import EnlargeableImage from '@/components/button/EnlargeableImage.vue'
import {renderPageMarkdown} from '@/util/markdown'
import {isYoutubeUrl, youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'
import {ENLARGED_WIDTH, type ContentRenderContext} from '@/util/contentContext'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import {blockImageHash, blockImageUrl} from '@/components/content/blockeditor/blockImage'

/**
 * One block, rendered.
 *
 * Every surface that shows authored blocks comes through here: the public page, the editor's
 * preview, and an article inside the station. They differ only in the context they hand in, which
 * is what keeps the preview honest - it shows what the reader sees because it is the same
 * component, not because two files were kept in step by hand.
 */
const props = defineProps<{
    cell: CellData
    context: ContentRenderContext
}>()

const options = useBlockEditorOptions()

/** The text, with the tokens the editor names, such as a letter's placeholders, drawn as chips. */
const markdownHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN) return ''
    return renderPageMarkdown(options.value.tokens?.prepare(props.cell.content) ?? props.cell.content)
})

/** A row inside a nested-rows block. The server keeps these as written, cells and all. */
interface NestedRow { cells: CellData[], columnLines?: boolean }

const nestedRows = computed<NestedRow[]>(() => {
    const raw = configOf(props.cell, CellContentType.NESTED_ROWS)?.rows
    return Array.isArray(raw) ? raw as NestedRow[] : []
})

const imageConfig = computed<ImageConfig>(() => configOf(props.cell, CellContentType.IMAGE) ?? {})

const imageUrl = computed(() => blockImageUrl(props.context.stationUid, props.cell.content, props.context.fileUrl))
const imageHash = computed(() => blockImageHash(props.cell.content))
const enlargedUrl = computed(() => {
    const hash = imageHash.value
    return hash ? props.context.imageUrl(hash, ENLARGED_WIDTH) : imageUrl.value
})

function isYouTube(url: string): boolean {
    return isYoutubeUrl(url)
}

function youtubeEmbedUrl(url: string): string | null {
    return toYoutubeEmbedUrl(url)
}
</script>

<template>
    <div v-if="cell.contentType === CellContentType.MARKDOWN"
         class="markdown-content"
         v-html="markdownHtml"/>

    <figure v-else-if="cell.contentType === CellContentType.IMAGE && imageUrl" class="space-y-1">
        <EnlargeableImage :src="enlargedUrl"
                          :alt="imageConfig.altText" :caption="imageConfig.description">
            <CellImagePreview
                :src="imageUrl"
                :alt="imageConfig.altText ?? ''"
                :config="imageConfig"
                :station-uid="context.stationUid"
                :content-hash="imageHash"
                :width-hint="context.widthHint"
            />
        </EnlargeableImage>
        <figcaption v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
            {{ imageConfig.description }}
        </figcaption>
    </figure>

    <template v-else-if="cell.contentType === CellContentType.VIDEO && cell.content">
        <div v-if="isYouTube(cell.content)" class="relative pb-[56.25%] h-0">
            <iframe
                :src="youtubeEmbedUrl(cell.content) ?? undefined"
                class="absolute top-0 left-0 w-full h-full rounded"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen
                :title="context.title"
            />
        </div>
        <video v-else controls class="w-full rounded">
            <source :src="cell.content"/>
        </video>
    </template>

    <CellLayoutRender
        v-else-if="isLayoutKind(cell.contentType)"
        :kind="cell.contentType as LayoutKindName"
        :content="cell.content"
        :config="cell.config"
        :station-uid="context.stationUid"
        :timezone="context.timezone"
    />

    <div v-else-if="cell.contentType === CellContentType.NESTED_ROWS" class="space-y-3">
        <div
            v-for="(row, ri) in nestedRows" :key="ri"
            class="flex flex-wrap gap-2"
        >
            <div
                v-for="(child, ci) in (row.cells ?? [])" :key="ci"
                :style="{flex: `0 0 calc(${child.widthPercent}% - 0.5rem)`}"
                class="row-cell min-w-0"
                :class="row.columnLines && ci > 0 ? 'border-l border-(--border) pl-2' : ''"
            >
                <ContentCell :cell="child" :context="context"/>
            </div>
        </div>
    </div>
</template>
