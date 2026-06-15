/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {PageCell, ImageConfig} from '@/api/pageManage'
import {CellContentType} from '@/api/pageManage'
import {publicPageImageUrl} from '@/api/publicPages'
import CellLayoutRender from '@/views/stationview/pages/pageeditorview/CellLayoutRender.vue'

const LAYOUT_KINDS = ['CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD']
function isLayoutKind(t: string): boolean { return LAYOUT_KINDS.includes(t) }

defineProps<{
    cell: PageCell
    stationUid: string
    pageTitle: string
}>()

function isYouTube(url: string): boolean {
    return /youtube\.com|youtu\.be/.test(url)
}

function extractYoutubeId(url: string): string | null {
    const patterns = [
        /(?:youtube\.com\/watch\?v=)([a-zA-Z0-9_-]{11})/,
        /(?:youtu\.be\/)([a-zA-Z0-9_-]{11})/,
        /(?:youtube\.com\/embed\/)([a-zA-Z0-9_-]{11})/,
    ]
    for (const pattern of patterns) {
        const match = url.match(pattern)
        if (match) return match[1]
    }
    return null
}

function youtubeEmbedUrl(url: string): string | null {
    const id = extractYoutubeId(url)
    return id ? `https://www.youtube-nocookie.com/embed/${id}` : null
}

function imageFitStyle(cell: PageCell): string {
    const config = cell.config as ImageConfig
    return config?.imageFit?.toLowerCase() ?? 'cover'
}

function imageAlt(cell: PageCell): string {
    const config = cell.config as ImageConfig
    return config?.altText ?? ''
}

function imageDescription(cell: PageCell): string {
    const config = cell.config as ImageConfig
    return config?.description ?? ''
}

function imageMaxHeight(cell: PageCell): string | undefined {
    const config = cell.config as ImageConfig
    return config?.maxHeight ? `${config.maxHeight}px` : undefined
}
</script>

<template>
    <!-- MARKDOWN -->
    <div v-if="cell.contentType === CellContentType.MARKDOWN"
         v-html="cell.content"
         class="markdown-content"/>

    <!-- IMAGE -->
    <figure v-else-if="cell.contentType === CellContentType.IMAGE" class="space-y-1">
        <img :src="publicPageImageUrl(stationUid, parseInt(cell.content))"
             :alt="imageAlt(cell)"
             :title="imageAlt(cell)"
             :style="{
                 objectFit: imageFitStyle(cell),
                 maxHeight: imageMaxHeight(cell),
             }"
             loading="lazy"
             class="w-full rounded"/>
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
        :kind="cell.contentType as 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'"
        :content="cell.content"
        :config="cell.config as Record<string, unknown>"
    />
</template>
