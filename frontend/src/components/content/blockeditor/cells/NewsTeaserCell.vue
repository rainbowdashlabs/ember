/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {listPublicBlog} from '@/api/news'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {NewsTeaserConfig} from '@/api/pageManage'
import {formatDate} from '@/util/format'

/**
 * One news entry, shown live from the entry itself.
 *
 * <p>The block keeps only the entry's public id, so a changed title or text shows here as it is now.
 * Only entries on the station's public blog are drawn, for every reader alike: that is all the
 * picker offers, and a page is read by anybody. Nothing is said about the entry until it has been
 * looked for, so a page drawn on the server does not call every entry gone.
 */
const props = defineProps<{
    config: NewsTeaserConfig
    stationUid?: string
    /** The clock the publication date is written on, the reader's own where none is named. */
    timezone?: string | null
}>()

interface ResolvedNews {
    title: string
    summary: string
    href: string
    publishedAt: string | null
}

const {t} = useI18n()

const resolved = ref<ResolvedNews | null>(null)
const looked = ref(false)

async function resolve() {
    resolved.value = null
    looked.value = false
    await lookUp()
    looked.value = true
}

async function lookUp() {
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
            <p v-if="resolved.publishedAt" class="text-xs text-(--text-muted)">{{ formatDate(resolved.publishedAt, props.timezone) }}</p>
            <p v-if="resolved.summary" class="text-sm text-(--text-muted)">{{ resolved.summary }}</p>
        </div>
    </a>
    <EmptyHint v-else-if="looked">{{ t('stationPages.cells.newsUnavailable') }}</EmptyHint>
</template>
