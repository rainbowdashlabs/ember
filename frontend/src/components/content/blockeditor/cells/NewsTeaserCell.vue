/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {listPublicBlog} from '@/api/news'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {NewsTeaserConfig} from '@/api/pageManage'
import {formatDate} from '@/util/format'

const props = defineProps<{
    config: NewsTeaserConfig
    stationUid?: string
}>()

interface ResolvedNews {
    title: string
    summary: string
    href: string
    publishedAt: string | null
}

const resolved = ref<ResolvedNews | null>(null)

async function resolve() {
    if (!props.stationUid || !props.config.newsUid) return
    try {
        const entries = await listPublicBlog(props.stationUid, 0, 50)
        const match = entries.find(e => e.publicUid === props.config.newsUid)
        if (match) {
            resolved.value = {
                title: match.title,
                summary: stripHtml(match.contentHtml).slice(0, 200),
                href: `/public/station/${props.stationUid}/blog/${match.id}`,
                publishedAt: match.publishedAt ?? null,
            }
        }
    } catch { void 0 }
}

/**
 * Strip HTML for the news-teaser summary. Block boundaries (paragraphs, line breaks, list
 * items, headings, divs) are replaced with a single space first so words across blocks don't
 * collide into each other ({@code <p>foo</p><p>bar</p>} → {@code "foo bar"}, not {@code "foobar"}).
 */
function stripHtml(html: string): string {
    return html
        .replace(/<\s*br\s*\/?\s*>/gi, ' ')
        .replace(/<\/(p|div|li|h[1-6]|blockquote|tr)\s*>/gi, ' ')
        .replace(/<[^>]+>/g, '')
        .replace(/\s+/g, ' ')
        .trim()
}

onMounted(resolve)
watch(() => [props.stationUid, props.config.newsUid], resolve, {immediate: false})
</script>

<template>
    <a v-if="resolved" :href="resolved.href"
       class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ resolved.title }}</p>
            <p v-if="resolved.publishedAt" class="text-xs text-(--text-muted)">{{ formatDate(resolved.publishedAt) }}</p>
            <p v-if="resolved.summary" class="text-sm text-(--text-muted)">{{ resolved.summary }}</p>
        </div>
    </a>
    <EmptyHint v-else>Nicht mehr verfügbar</EmptyHint>
</template>
