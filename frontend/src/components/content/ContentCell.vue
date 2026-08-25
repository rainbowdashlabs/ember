/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {CellContentType, isLayoutKind, type ImageConfig, type LayoutKindName, type PageCell} from '@/api/pageManage'
import CellLayoutRender from '@/components/content/blockeditor/CellLayoutRender.vue'
import CellImagePreview from '@/components/content/blockeditor/CellImagePreview.vue'
import {renderPageMarkdown} from '@/util/markdown'
import {isYoutubeUrl, youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'
import type {ContentRenderContext} from '@/util/contentContext'

/**
 * One block, rendered.
 *
 * Every surface that shows authored blocks comes through here: the public page, the editor's
 * preview, and an article inside the station. They differ only in the context they hand in, which
 * is what keeps the preview honest - it shows what the reader sees because it is the same
 * component, not because two files were kept in step by hand.
 */
const props = defineProps<{
    cell: PageCell
    context: ContentRenderContext
}>()

const markdownHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN) return ''
    return renderPageMarkdown(props.cell.content)
})

interface NestedRow { cells: PageCell[] }

const nestedRows = computed<NestedRow[]>(() => {
    if (props.cell.contentType !== CellContentType.NESTED_ROWS) return []
    const raw = (props.cell.config as {rows?: NestedRow[]})?.rows
    return Array.isArray(raw) ? raw : []
})

const imageConfig = computed<ImageConfig>(() => (props.cell.config as ImageConfig) ?? {})

const imageUrl = computed(() => props.cell.content ? props.context.fileUrl(props.cell.content) : '')

function isYouTube(url: string): boolean {
    return isYoutubeUrl(url)
}

function youtubeEmbedUrl(url: string): string | null {
    return toYoutubeEmbedUrl(url)
}
</script>

<template>
    <div v-if="cell.contentType === CellContentType.MARKDOWN"
         v-html="markdownHtml"
         class="markdown-content"/>

    <figure v-else-if="cell.contentType === CellContentType.IMAGE && imageUrl" class="space-y-1">
        <CellImagePreview
            :src="imageUrl"
            :alt="imageConfig.altText ?? ''"
            :config="imageConfig"
            :station-uid="context.stationUid"
            :content-hash="cell.content"
            :width-hint="context.widthHint"
        />
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
        :config="cell.config as Record<string, unknown>"
        :station-uid="context.stationUid"
    />

    <!-- Nested rows carry their cells inside the config, so the render recurses into them. -->
    <div v-else-if="cell.contentType === CellContentType.NESTED_ROWS" class="space-y-3">
        <div
            v-for="(row, ri) in nestedRows" :key="ri"
            class="flex flex-wrap gap-2"
        >
            <div
                v-for="(child, ci) in (row.cells ?? [])" :key="ci"
                :style="{flex: `0 0 calc(${child.widthPercent}% - 0.5rem)`}"
                class="min-w-0"
            >
                <ContentCell :cell="child as PageCell" :context="context"/>
            </div>
        </div>
    </div>
</template>
