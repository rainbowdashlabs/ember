/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import type {TabsConfig} from '@/api/generated/schema'
import TabBar from '@/components/navigation/TabBar.vue'
import {renderMarkdown} from '@/util/markdown'

const props = defineProps<{
    config: TabsConfig
}>()

const activeKey = ref('0')

const tabs = computed(() => (props.config.items ?? []).map((tab, i) => ({
    key: String(i),
    label: tab.title || `Tab ${i + 1}`,
})))

const activeBody = computed(() => props.config.items?.[Number(activeKey.value)]?.body)

const activeLabel = computed(() => tabs.value[Number(activeKey.value)]?.label)
</script>

<template>
    <div class="rounded-theme border border-(--border) overflow-hidden">
        <TabBar v-model="activeKey" :tabs="tabs" class="bg-bg-light-accent/30 dark:bg-bg-dark-accent/30"/>
        <div :aria-label="activeLabel" class="p-3 markdown-content" role="tabpanel" tabindex="0" v-html="renderMarkdown(activeBody)"/>
    </div>
</template>
