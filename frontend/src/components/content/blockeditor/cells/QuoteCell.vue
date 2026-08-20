/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {QuoteConfig} from '@/api/pageManage'

defineProps<{
    config: QuoteConfig
    content: string
}>()
</script>

<template>
    <blockquote class="border-l-4 border-primary/60 pl-4 py-2 italic">
        <p class="text-base whitespace-pre-line">{{ content || '…' }}</p>
        <footer v-if="config.author" class="mt-2 text-xs not-italic text-(--text-muted)">
            -
            <a v-if="config.attributionUrl" :href="config.attributionUrl" class="hover:underline" target="_blank" rel="noopener noreferrer">{{ config.author }}</a>
            <span v-else>{{ config.author }}</span>
        </footer>
    </blockquote>
</template>
