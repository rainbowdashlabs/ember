/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import type {TabsConfig} from '@/api/pageManage'

defineProps<{
    config: TabsConfig
}>()

const activeTab = ref(0)
</script>

<template>
    <div class="rounded-theme border border-(--border) overflow-hidden">
        <div class="flex border-b border-(--border) bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
            <a v-for="(tab, i) in config.items ?? []" :key="i" role="tab" tabindex="0"
               :class="['px-3 py-2 text-sm font-medium cursor-pointer select-none', activeTab === i ? 'border-b-2 border-primary text-primary' : 'text-(--text-muted) hover:text-(--text)']"
               @click="activeTab = i" @keydown.enter="activeTab = i">{{ tab.title || `Tab ${i + 1}` }}</a>
        </div>
        <div class="p-3 markdown-content whitespace-pre-line">{{ config.items?.[activeTab]?.body ?? '' }}</div>
    </div>
</template>
