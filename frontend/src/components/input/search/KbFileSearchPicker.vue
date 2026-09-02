/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {knowledgeBase} from '@/api'
import type {SearchResult} from '@/api/knowledgeBase'

/**
 * Finds an article anywhere in this station's wiki.
 *
 * Fed from the wiki search rather than from one folder listing, so it reaches the title, the
 * description and the body of every article, in the station's language, at any depth. The folder an
 * article sits in is shown under its name: two articles called "Checkliste" in different folders are
 * otherwise the same row twice. With nothing typed it offers the articles changed most recently, so
 * the list opens with something in it.
 *
 * This picker keeps no selection of its own. It reports what was picked and nothing else, because
 * the places that use it act on the pick straight away rather than holding it in a field. The
 * search route it reads decides what may be named here, so an article the reader cannot open is
 * not offered.
 */
const emit = defineEmits<{
    pick: [item: SearchResult]
}>()

const props = defineProps<{
    /** Ids already spoken for, which are dropped from the results rather than offered again. */
    excludeIds?: number[]
    placeholder?: string
}>()

const {t} = useI18n()

async function searchFn(query: string): Promise<SearchResult[]> {
    const results = query.trim()
        ? await knowledgeBase.search(query, {federated: false})
        : await knowledgeBase.listRecentFiles(10)
    return results
        .filter(result => !result.sourceStationUid)
        .filter(result => !props.excludeIds?.includes(result.file.id))
        .slice(0, 10)
}

const displayFn = (item: SearchResult) => item.file.name
const subtitleFn = (item: SearchResult) => item.folderPath
const keyFn = (item: SearchResult) => item.file.id
const iconFn = (): string[] => ['fas', 'file-lines']
</script>

<template>
    <EntitySearchPicker
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :placeholder="placeholder ?? t('kb.searchRelated')"
        :empty-label="t('kb.noFilesFound')"
        @pick="(item: SearchResult) => emit('pick', item)"
    />
</template>
