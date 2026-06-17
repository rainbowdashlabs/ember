/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {BlogSignupConfig} from '@/api/pageManage'

defineProps<{
    config: BlogSignupConfig
    stationUid?: string | null
}>()
</script>

<template>
    <div class="rounded-theme border border-(--border) bg-primary/5 p-4 space-y-2 text-center">
        <font-awesome-icon :icon="['fas', 'rss']" class="text-3xl text-primary"/>
        <p class="font-semibold">{{ config.title || 'Blog abonnieren' }}</p>
        <p v-if="config.description" class="text-sm text-(--text-muted)">{{ config.description }}</p>
        <div v-if="stationUid" class="flex justify-center gap-2 pt-1">
            <a :href="`/api/v1/public/station/${stationUid}/blog.atom`" target="_blank" rel="alternate noopener"
               class="inline-block px-3 py-1.5 rounded-theme bg-primary text-primary-text! text-sm font-medium hover:bg-primary-accent">
                Atom abonnieren
            </a>
            <a :href="`/api/v1/public/station/${stationUid}/blog.rss`" target="_blank" rel="alternate noopener"
               class="inline-block px-3 py-1.5 rounded-theme border border-(--border) text-sm hover:bg-primary/10">
                RSS
            </a>
        </div>
    </div>
</template>
