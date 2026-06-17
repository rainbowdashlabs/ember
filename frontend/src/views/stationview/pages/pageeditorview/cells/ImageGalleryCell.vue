/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {pageImageUrl, type ImageGalleryConfig} from '@/api/pageManage'

const props = defineProps<{
    config: ImageGalleryConfig
    stationUid?: string
}>()

const columns = computed(() => Math.max(1, Math.min(6, props.config.columns ?? 3)))
</script>

<template>
    <div v-if="config.aspectMode === 'PRESERVE'" class="flex flex-wrap justify-center items-end gap-2">
        <figure v-for="(item, gi) in config.items ?? []" :key="item.imageHash + '-' + gi"
                class="inline-flex flex-col items-center gap-1 shrink-0">
            <img :src="stationUid ? pageImageUrl(stationUid, item.imageHash) : ''"
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
            <img :src="stationUid ? pageImageUrl(stationUid, item.imageHash) : ''"
                 :alt="item.altText ?? ''" :title="item.altText ?? ''"
                 class="w-full aspect-square object-cover rounded" loading="lazy"/>
            <figcaption v-if="item.subtext" class="text-xs text-(--text-muted) text-center">
                {{ item.subtext }}
            </figcaption>
        </figure>
    </div>
</template>
