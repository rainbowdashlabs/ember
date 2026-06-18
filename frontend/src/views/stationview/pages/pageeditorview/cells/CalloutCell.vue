/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {marked} from 'marked'
import {CalloutVariant, type CalloutConfig} from '@/api/pageManage'

const props = defineProps<{
    config: CalloutConfig
    content: string
}>()

const calloutStyle = computed(() => {
    switch (props.config.variant ?? CalloutVariant.INFO) {
        case CalloutVariant.WARNING:
            return {bg: 'bg-warning/10 dark:bg-warning/15', border: 'border-warning/40', text: 'text-warning', icon: ['fas', 'triangle-exclamation']}
        case CalloutVariant.SUCCESS:
            return {bg: 'bg-success/10 dark:bg-success/15', border: 'border-success/40', text: 'text-success', icon: ['fas', 'circle-check']}
        case CalloutVariant.TIP:
            return {bg: 'bg-primary/10 dark:bg-primary/15', border: 'border-primary/40', text: 'text-primary', icon: ['fas', 'lightbulb']}
        case CalloutVariant.INFO:
        default:
            return {bg: 'bg-info/10 dark:bg-info/15', border: 'border-info/40', text: 'text-info', icon: ['fas', 'circle-info']}
    }
})

const renderedContent = computed(() => {
    if (!props.content) return ''
    try { return marked.parse(props.content) as string } catch { return props.content }
})
</script>

<template>
    <div class="border-l-4 rounded-r px-4 py-3 flex gap-3" :class="[calloutStyle.bg, calloutStyle.border]">
        <font-awesome-icon :icon="calloutStyle.icon" class="mt-0.5" :class="calloutStyle.text"/>
        <div class="flex-1 min-w-0">
            <p v-if="config.title" class="font-semibold" :class="calloutStyle.text">{{ config.title }}</p>
            <div v-if="content" class="markdown-content text-sm" v-html="renderedContent"/>
        </div>
    </div>
</template>
