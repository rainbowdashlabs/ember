/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {marked} from 'marked'
import type {PageCell, ImageConfig, LayoutKindName} from '@/api/pageManage'
import {CellContentType, isLayoutKind} from '@/api/pageManage'
import {publicPageImageUrl} from '@/api/publicPages'
import CellLayoutRender from '@/views/stationview/pages/pageeditorview/CellLayoutRender.vue'
import CellImagePreview from '@/views/stationview/pages/pageeditorview/CellImagePreview.vue'
import {enhancePageMarkdownImages} from '@/util/pageMarkdownImages'
import {isYoutubeUrl, youtubeEmbedUrl as toYoutubeEmbedUrl} from '@/util/youtube'

const props = defineProps<{
    cell: PageCell
    stationUid: string
    pageTitle: string
}>()

const markdownHtml = computed(() => {
    if (props.cell.contentType !== CellContentType.MARKDOWN || !props.cell.content) return ''
    return enhancePageMarkdownImages(marked.parse(props.cell.content) as string)
})

interface NestedRow { cells: PageCell[] }
const nestedRows = computed<NestedRow[]>(() => {
    if (props.cell.contentType !== CellContentType.NESTED_ROWS) return []
    const raw = (props.cell.config as {rows?: NestedRow[]})?.rows
    return Array.isArray(raw) ? raw : []
})

function isYouTube(url: string): boolean {
    return isYoutubeUrl(url)
}

function youtubeEmbedUrl(url: string): string | null {
    return toYoutubeEmbedUrl(url)
}

function imageConfig(cell: PageCell): ImageConfig {
    return (cell.config as ImageConfig) ?? {}
}

function imageAlt(cell: PageCell): string {
    return imageConfig(cell).altText ?? ''
}

function imageDescription(cell: PageCell): string {
    return imageConfig(cell).description ?? ''
}
</script>

<template>
    <!-- MARKDOWN -->
    <div v-if="cell.contentType === CellContentType.MARKDOWN"
         v-html="markdownHtml"
         class="markdown-content"/>

    <figure v-else-if="cell.contentType === CellContentType.IMAGE" class="space-y-1">
        <CellImagePreview
            :src="publicPageImageUrl(stationUid, cell.content)"
            :alt="imageAlt(cell)"
            :config="imageConfig(cell)"
            :station-uid="stationUid"
            :content-hash="cell.content"
            :width-hint="2048"
        />
        <figcaption v-if="imageDescription(cell)" class="text-xs text-(--text-muted) italic text-center">
            {{ imageDescription(cell) }}
        </figcaption>
    </figure>

    <!-- VIDEO -->
    <template v-else-if="cell.contentType === CellContentType.VIDEO">
        <!-- YouTube embed -->
        <div v-if="isYouTube(cell.content)" class="relative pb-[56.25%] h-0">
            <iframe
                :src="youtubeEmbedUrl(cell.content) ?? undefined"
                class="absolute top-0 left-0 w-full h-full rounded"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen
                :title="pageTitle"
            />
        </div>
        <!-- Direct video -->
        <video v-else controls class="w-full rounded">
            <source :src="cell.content"/>
        </video>
    </template>

    <!-- Layout primitives -->
    <CellLayoutRender
        v-else-if="isLayoutKind(cell.contentType)"
        :kind="cell.contentType as LayoutKindName"
        :content="cell.content"
        :config="cell.config as Record<string, unknown>"
        :station-uid="stationUid"
    />

    <!-- Nested rows: recursively render each nested row as its own row of public cells. -->
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
                <PublicPageCell :cell="child as PageCell" :station-uid="stationUid" :page-title="pageTitle"/>
            </div>
        </div>
    </div>
</template>
