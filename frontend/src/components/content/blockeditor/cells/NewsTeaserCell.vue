/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {getPublicNewsTeaser, type PublicNewsTeaser} from '@/api/news'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import type {NewsTeaserConfig} from '@/api/pageManage'
import {apiUrl} from '@/util/apiUrl'
import {formatDate} from '@/util/format'

/**
 * One news entry, shown live from the entry itself.
 *
 * <p>The block keeps only the entry's public id, so a changed title or text shows here as it is now.
 * The entry is read by that id from the station's public blog, the same for every reader, since a
 * page is read by anybody: a draft or an entry kept to part of the station is never drawn. It is
 * read while the server renders the page, so the page arrives with the entry in it.
 *
 * <p>The block calls the entry unavailable only when there is none to name or the blog says there is
 * no such entry. While it is being read, or when reading it failed for another reason, it shows
 * nothing rather than claim the entry is gone.
 */
const props = defineProps<{
    config: NewsTeaserConfig
    stationUid?: string
    /** The clock the publication date is written on, the reader's own where none is named. */
    timezone?: string | null
}>()

const {t} = useI18n()

const apiBase = apiUrl('')

const {data: entry, error} = useAsyncData(
    () => `news-teaser-${props.stationUid ?? ''}-${props.config.newsUid ?? ''}`,
    (): Promise<PublicNewsTeaser | null> => {
        if (!props.stationUid || !props.config.newsUid) return Promise.resolve(null)
        return getPublicNewsTeaser(apiBase, props.stationUid, props.config.newsUid)
    },
)

const unavailable = computed(() => !props.config.newsUid || error.value?.statusCode === 404)

const href = computed(() => (entry.value ? `/public/station/${props.stationUid}/blog/${entry.value.id}` : ''))
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
