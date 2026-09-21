/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {type ImageGalleryConfig} from '@/api/pageManage'
import {mediaImageSrcset, mediaImageUrlAt} from '@/api/media'
import {PAGE_WIDTH} from '@/util/contentContext'

const props = defineProps<{
    config: ImageGalleryConfig
    stationUid?: string
}>()

const columns = computed(() => Math.max(1, Math.min(6, props.config.columns ?? 3)))

/** The narrowest picture worth asking for, below which a variant saves nothing worth having. */
const SMALLEST_WIDTH = 256

/**
 * How wide one picture of the gallery is drawn.
 *
 * <p>A column of the grid, or, where every picture keeps its own shape, twice its height, which is
 * what a wide photograph standing that tall comes to. The browser asks for twice this again on a
 * dense screen, so the number is the one a reader sees and not the one their screen could hold.
 */
const itemWidth = computed(() => {
    const drawn = props.config.aspectMode === 'PRESERVE'
        ? (props.config.maxItemHeightPx ?? 300) * 2
        : PAGE_WIDTH / columns.value
    return Math.max(SMALLEST_WIDTH, Math.round(drawn))
})
</script>

<template>
    <div v-if="config.aspectMode === 'PRESERVE'" class="flex flex-wrap justify-center items-end gap-2">
        <figure v-for="(item, gi) in config.items ?? []" :key="item.imageHash + '-' + gi"
                class="inline-flex flex-col items-center gap-1 shrink-0">
            <img :src="stationUid ? mediaImageUrlAt(stationUid, item.imageHash, itemWidth) : ''"
                 :srcset="stationUid ? mediaImageSrcset(stationUid, item.imageHash, itemWidth) : undefined"
                 :alt="item.altText ?? ''" :title="item.altText ?? ''"
                 :style="{height: `${config.maxItemHeightPx ?? 300}px`, width: 'auto'}"
                 class="rounded block" loading="lazy"/>
            <figcaption v-if="item.subtext"
                        class="text-xs text-(--text-muted) text-center break-words"
                        style="width: 0; min-width: 100%;">
                {{ item.subtext }}
            </figcaption>
        </figure>
    </div>
    <div v-else :class="`grid grid-cols-${columns} gap-2`">
        <figure v-for="(item, gi) in config.items ?? []" :key="item.imageHash + '-' + gi" class="space-y-1">
            <img :src="stationUid ? mediaImageUrlAt(stationUid, item.imageHash, itemWidth) : ''"
                 :srcset="stationUid ? mediaImageSrcset(stationUid, item.imageHash, itemWidth) : undefined"
                 :alt="item.altText ?? ''" :title="item.altText ?? ''"
                 class="w-full aspect-square object-cover rounded" loading="lazy"/>
            <figcaption v-if="item.subtext" class="text-xs text-(--text-muted) text-center">
                {{ item.subtext }}
            </figcaption>
        </figure>
    </div>
</template>
