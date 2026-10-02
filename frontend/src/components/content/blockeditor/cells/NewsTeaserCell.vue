/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {NewsTeaserConfig} from '@/api/generated/schema'
import {useBlockAudience} from '@/composables/useBlockAudience'
import {useNewsRoutes} from '@/composables/useNewsRoutes'
import {apiUrl} from '@/util/apiUrl'
import {formatDate} from '@/util/format'
import {findEmbeddedNews, type FoundNews} from '../embeddedNewsLookup'

/**
 * One news entry, shown live from the entry itself.
 *
 * <p>The block keeps only the entry's public id, so a changed title or text shows here as it is now.
 * On a public page the entry is read from the station's public blog, the same for every reader, since
 * a page is read by anybody; that read happens while the server renders the page, so the page arrives
 * with the entry in it. In a news or wiki article a member of the station also sees internal entries,
 * as long as every member may read them. A draft or an entry kept to part of the station is never
 * drawn.
 *
 * <p>The block calls the entry unavailable only when there is none to name or the lookup says there
 * is no such entry here. While it is being read, or when reading it failed for another reason, it
 * shows nothing rather than claim the entry is gone.
 */
const props = defineProps<{
    config: NewsTeaserConfig
    stationUid?: string
    /** The clock the publication date is written on, the reader's own where none is named. */
    timezone?: string | null
}>()

const {t} = useI18n()
const router = useRouter()
const newsRoutes = useNewsRoutes()
const audience = useBlockAudience()

const apiBase = apiUrl('')

const {data: entry, status} = useAsyncData(
    () => `news-teaser-${audience}-${props.stationUid ?? ''}-${props.config.newsUid ?? ''}`,
    (): Promise<FoundNews | null> => {
        if (!props.stationUid || !props.config.newsUid) return Promise.resolve(null)
        return findEmbeddedNews(apiBase, props.stationUid, props.config.newsUid, audience)
    },
)

const unavailable = computed(() => !props.config.newsUid || (status.value === 'success' && !entry.value))

const href = computed(() => {
    const found = entry.value
    if (!found) return ''
    if (found.source.kind === 'MEMBER') return router.resolve({name: newsRoutes.detail, params: {id: found.id}}).href
    return `/public/station/${found.source.stationUid}/blog/${found.id}`
})
</script>

<template>
    <a v-if="entry" :href="href" data-testid="news-teaser"
       class="block rounded-theme border border-(--border) hover:border-primary hover:bg-primary/5 transition-colors overflow-hidden">
        <div class="p-3 space-y-1">
            <p class="font-semibold">{{ entry.title }}</p>
            <p v-if="entry.publishedAt" class="text-xs text-(--text-muted)">{{ formatDate(entry.publishedAt, props.timezone) }}</p>
            <p v-if="entry.summary" class="text-sm text-(--text-muted)">{{ entry.summary }}</p>
        </div>
    </a>
    <EmptyHint v-else-if="unavailable">{{ t('stationPages.cells.newsUnavailable') }}</EmptyHint>
</template>
