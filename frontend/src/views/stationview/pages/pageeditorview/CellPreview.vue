/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {marked} from 'marked'
import CellImagePreview from './CellImagePreview.vue'
import CellLayoutRender from './CellLayoutRender.vue'
import EditorRow, {type RowEditData} from './EditorRow.vue'
import type {CellEditData} from './EditorCell.vue'
import {
    CellContentType,
    isLayoutKind,
    pageImageUrl,
    type ImageConfig,
    type LayoutKindName,
    type VideoConfig,
} from '@/api/pageManage'
import {enhancePageMarkdownImages} from '@/util/pageMarkdownImages'
import {isYoutubeUrl, youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'

/**
 * Read-only render of a cell exactly as it appears on the public page. Mirrors the renderer for
 * every supported content type so the page editor can offer an accurate preview without round-
 * tripping through the public render endpoint.
 */
const props = defineProps<{
    cell: CellEditData
    pageId: number
    stationUid: string
    depth: number
}>()

const imageConfig = computed<ImageConfig>(() => (props.cell.config as ImageConfig) ?? {})
const videoConfig = computed<VideoConfig>(() => (props.cell.config as VideoConfig) ?? {})

const imageUrl = computed(() => {
    if (props.cell.contentType !== CellContentType.IMAGE || !props.cell.content) return ''
    return pageImageUrl(props.stationUid, props.cell.content)
})

const renderedHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN || !props.cell.content) return ''
    return enhancePageMarkdownImages(marked.parse(props.cell.content) as string)
})

const isYouTube = computed(() => {
    if (props.cell.contentType !== CellContentType.VIDEO) return false
    return isYoutubeUrl(props.cell.content)
})

const youtubeEmbedUrl = computed(() => {
    const url = props.cell.content
    return toYoutubeEmbedUrl(url) ?? url
})

const nestedRows = computed<RowEditData[]>(() => {
    const raw = (props.cell.config as {rows?: RowEditData[]}).rows
    return Array.isArray(raw) ? raw : []
})
</script>

<template>
    <div class="w-full">
        <div v-if="cell.contentType === CellContentType.MARKDOWN && cell.content" class="markdown-content">
            <div v-html="renderedHtml"/>
        </div>

        <div v-else-if="cell.contentType === CellContentType.IMAGE && imageUrl" class="w-full space-y-1">
            <CellImagePreview
                :src="imageUrl"
                :alt="(imageConfig.altText as string) ?? ''"
                :config="imageConfig"
                :station-uid="stationUid"
                :content-hash="cell.content"
                :width-hint="1024"
            />
            <p v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
                {{ imageConfig.description }}
            </p>
        </div>

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

        <CellLayoutRender
            v-else-if="isLayoutKind(cell.contentType)"
            :kind="cell.contentType as LayoutKindName"
            :content="cell.content"
            :config="cell.config"
        />

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
</template>
