/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import type {AccordionConfig} from '@/api/pageManage'
import {renderPageMarkdown} from '@/util/markdown'

const props = defineProps<{
    config: AccordionConfig
    content: string
}>()

const open = ref<boolean>(!!props.config.openByDefault)

const renderedContent = computed(() => renderPageMarkdown(props.content))
</script>

<template>
    <details class="rounded-theme border border-(--border) overflow-hidden"
             :open="open"
             @toggle="(e: Event) => { open = (e.target as HTMLDetailsElement).open }">
        <summary class="cursor-pointer px-3 py-2 font-medium bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 select-none">
            {{ config.title || '…' }}
        </summary>
        <div v-if="content" class="markdown-content px-3 py-2" v-html="renderedContent"/>
    </details>
</template>
