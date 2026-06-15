/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {marked} from 'marked'
import {
    CalloutVariant,
    type CalloutConfig,
    type QuoteConfig,
    type DividerConfig,
    type SpacerConfig,
    type AccordionConfig,
    type PdfConfig,
    type FileDownloadConfig,
} from '@/api/pageManage'

const props = defineProps<{
    kind: 'CALLOUT' | 'QUOTE' | 'DIVIDER' | 'SPACER' | 'ACCORDION' | 'PDF' | 'FILE_DOWNLOAD'
    content: string
    config: Record<string, unknown>
}>()

const callout = computed(() => props.config as CalloutConfig)
const quote = computed(() => props.config as QuoteConfig)
const divider = computed(() => props.config as DividerConfig)
const spacer = computed(() => props.config as SpacerConfig)
const accordion = computed(() => props.config as AccordionConfig)
const pdf = computed(() => props.config as PdfConfig)
const file = computed(() => props.config as FileDownloadConfig)

const calloutStyle = computed(() => {
    switch (callout.value.variant ?? CalloutVariant.INFO) {
        case CalloutVariant.WARNING: return {bg: 'bg-warning/10 dark:bg-warning/15', border: 'border-warning/40', text: 'text-warning', icon: ['fas', 'triangle-exclamation']}
        case CalloutVariant.SUCCESS: return {bg: 'bg-success/10 dark:bg-success/15', border: 'border-success/40', text: 'text-success', icon: ['fas', 'circle-check']}
        case CalloutVariant.TIP: return {bg: 'bg-primary/10 dark:bg-primary/15', border: 'border-primary/40', text: 'text-primary', icon: ['fas', 'lightbulb']}
        case CalloutVariant.INFO:
        default: return {bg: 'bg-info/10 dark:bg-info/15', border: 'border-info/40', text: 'text-info', icon: ['fas', 'circle-info']}
    }
})

const renderedContent = computed(() => {
    if (!props.content) return ''
    try { return marked.parse(props.content) as string } catch { return props.content }
})

const accordionOpen = ref<boolean>(!!accordion.value.openByDefault)
</script>

<template>
    <!-- CALLOUT -->
    <div
        v-if="kind === 'CALLOUT'"
        class="border-l-4 rounded-r px-4 py-3 flex gap-3"
        :class="[calloutStyle.bg, calloutStyle.border]"
    >
        <font-awesome-icon :icon="calloutStyle.icon" class="mt-0.5" :class="calloutStyle.text"/>
        <div class="flex-1 min-w-0">
            <p v-if="callout.title" class="font-semibold" :class="calloutStyle.text">{{ callout.title }}</p>
            <div v-if="content" class="markdown-content text-sm" v-html="renderedContent"/>
        </div>
    </div>

    <!-- QUOTE -->
    <blockquote v-else-if="kind === 'QUOTE'" class="border-l-4 border-primary/60 pl-4 py-2 italic">
        <p class="text-base whitespace-pre-line">{{ content || '…' }}</p>
        <footer v-if="quote.author" class="mt-2 text-xs not-italic text-(--text-muted)">
            —
            <a v-if="quote.attributionUrl" :href="quote.attributionUrl" class="hover:underline" target="_blank" rel="noopener noreferrer">{{ quote.author }}</a>
            <span v-else>{{ quote.author }}</span>
        </footer>
    </blockquote>

    <!-- DIVIDER -->
    <div v-else-if="kind === 'DIVIDER'" class="flex items-center gap-3 py-2">
        <div class="flex-1 h-px bg-(--border)"/>
        <span v-if="divider.label" class="text-xs uppercase tracking-wider text-(--text-muted)">{{ divider.label }}</span>
        <div v-if="divider.label" class="flex-1 h-px bg-(--border)"/>
    </div>

    <!-- SPACER -->
    <div v-else-if="kind === 'SPACER'" :style="{height: `${spacer.heightPx ?? 32}px`}"/>

    <!-- ACCORDION -->
    <details
        v-else-if="kind === 'ACCORDION'"
        class="rounded-theme border border-(--border) overflow-hidden"
        :open="accordionOpen"
        @toggle="(e: Event) => { accordionOpen = (e.target as HTMLDetailsElement).open }"
    >
        <summary class="cursor-pointer px-3 py-2 font-medium bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 select-none">
            {{ accordion.title || '…' }}
        </summary>
        <div v-if="content" class="markdown-content px-3 py-2" v-html="renderedContent"/>
    </details>

    <!-- PDF -->
    <div v-else-if="kind === 'PDF' && pdf.url" class="rounded-theme overflow-hidden border border-(--border)">
        <iframe
            :src="pdf.url"
            :style="{height: `${pdf.heightPx ?? 600}px`}"
            class="w-full block"
            loading="lazy"
        />
    </div>
    <p v-else-if="kind === 'PDF'" class="text-sm text-(--text-muted) italic">PDF-URL fehlt</p>

    <!-- FILE_DOWNLOAD -->
    <a
        v-else-if="kind === 'FILE_DOWNLOAD' && file.url"
        :href="file.url"
        target="_blank"
        rel="noopener noreferrer"
        class="flex items-center gap-3 rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors px-4 py-3"
        download
    >
        <font-awesome-icon :icon="['fas', 'file']" class="text-2xl text-primary shrink-0"/>
        <div class="flex-1 min-w-0">
            <p class="font-medium truncate">{{ file.label || file.url.split('/').pop() || 'Datei' }}</p>
            <p v-if="file.description" class="text-xs text-(--text-muted) truncate">{{ file.description }}</p>
        </div>
        <font-awesome-icon :icon="['fas', 'download']" class="text-(--text-muted)"/>
    </a>
    <p v-else-if="kind === 'FILE_DOWNLOAD'" class="text-sm text-(--text-muted) italic">Datei-URL fehlt</p>
</template>
