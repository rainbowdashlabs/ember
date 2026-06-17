/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {pageImageUrl, type HeroBannerConfig} from '@/api/pageManage'

defineProps<{
    config: HeroBannerConfig
    stationUid?: string
}>()
</script>

<template>
    <div class="relative w-full rounded-theme overflow-hidden">
        <img v-if="config.imageHash && stationUid" :src="pageImageUrl(stationUid, config.imageHash)" alt="" class="w-full h-64 object-cover"/>
        <div v-else class="w-full h-64 bg-gradient-to-br from-primary/40 to-primary-accent/40"/>
        <div class="absolute inset-0 bg-black/40 flex flex-col items-center justify-center text-center text-white p-6">
            <div v-if="config.headline" class="text-3xl sm:text-4xl font-bold">{{ config.headline }}</div>
            <p v-if="config.subtitle" class="mt-2 text-lg">{{ config.subtitle }}</p>
            <a v-if="config.ctaUrl" :href="config.ctaUrl"
               class="mt-4 inline-block px-4 py-2 rounded-theme bg-primary text-primary-text! font-medium hover:bg-primary-accent">
                {{ config.ctaText || 'Mehr erfahren' }}
            </a>
        </div>
    </div>
</template>
