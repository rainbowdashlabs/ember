/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo, PublicBrowseResponse, PublicSearchResult} from '@/api/publicKb'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import KnowledgeBaseBreadcrumbs from './publicknowledgebaseview/KnowledgeBaseBreadcrumbs.vue'
import KbSearchResults from '@/views/stationview/knowledge/knowledgebaseview/KbSearchResults.vue'
import KnowledgeBaseBrowse from './publicknowledgebaseview/KnowledgeBaseBrowse.vue'
import {usePublicKbItems} from './publicknowledgebaseview/usePublicKbItems'
import {useDebouncedSearch} from '@/composables/useDebouncedSearch'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, titleWithStation, useAbsoluteUrl} from '@/util/socialMeta'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()

const {station, stationUid, stationAddress, basePath} = usePublicStationAddress()
const currentFolderId = computed(() => {
    const param = route.query.folderId
    return param ? Number(param) : null
})

const absoluteUrl = useAbsoluteUrl()

const {
    query: searchQuery,
    results: searchResults,
    searching,
    isSearching,
    onInput: onSearchInput,
} = useDebouncedSearch<PublicSearchResult>(query => publicKb.search(stationUid.value, query))

/** Tiles or one entry to a line, the same choice the station's own members have in their wiki. */
const viewMode = ref<'grid' | 'list'>('grid')

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * Fetched while the server renders rather than after mounting, folder by folder.
 *
 * <p>The station's name is what this page is called after, and the folder standing open is what the
 * rest of it says. Both arrived once the browser had taken over, so a crawler read a wiki with no
 * name and a link to a folder unfurled as the station's own card. The folder in the address is
 * watched, which is what turns the page when a reader walks into one.
 */
const {data: wiki, error: loadError, status} = await useAsyncData(
    () => `public-kb-${stationUid.value}-${currentFolderId.value ?? 'root'}`,
    async () => {
        const folderQuery = currentFolderId.value == null ? '' : `?folderId=${currentFolderId.value}`
        const [info, browsed] = await Promise.all([
            $fetch<PublicStationInfo>(`${apiBase}/public/kb/${stationUid.value}/info`),
            $fetch<PublicBrowseResponse>(`${apiBase}/public/kb/${stationUid.value}/browse${folderQuery}`),
        ])
        return {info, browsed}
    },
    {watch: [stationUid, currentFolderId]},
)

const stationInfo = computed<PublicStationInfo | null>(() => wiki.value?.info ?? null)
const currentFolder = computed<KbFolder | null>(() => wiki.value?.browsed.currentFolder ?? null)
const folders = computed<KbFolder[]>(() => wiki.value?.browsed.folders ?? [])
const files = computed<KbFile[]>(() => wiki.value?.browsed.files ?? [])
const loading = computed(() => status.value === 'pending')
const error = computed(() => (loadError.value ? t('common.error') : ''))

const {items, toSearchItems} = usePublicKbItems({stationUid, stationAddress, folders, files})
const searchItems = computed(() => toSearchItems(searchResults.value))

function navigateToFolder(folderId: number | null) {
    if (folderId === null) {
        router.push({name: 'public-kb', params: {stationUid: stationAddress.value}})
    } else {
        router.push({name: 'public-kb', params: {stationUid: stationAddress.value}, query: {folderId}})
    }
}

useHead(computed(() => {
    if (!stationInfo.value) return {}
    const stationName = stationInfo.value.stationName
    const folderName = currentFolder.value?.name
    const wikiTitle = titleWithStation(t('publicStation.knowledgeBase'), stationName)
    const title = folderName ? `${folderName} - ${wikiTitle}` : wikiTitle
    const desc = currentFolder.value?.description
        || (folderName
            ? t('publicStation.meta.knowledgeBaseFolder', {folder: folderName, station: stationName})
            : t('publicStation.meta.knowledgeBase', {station: stationName}))
    const breadcrumbs: { '@type': string; position: number; name: string; item?: string }[] = [
        {'@type': 'ListItem', position: 1, name: stationName, item: absoluteUrl(`${basePath.value}/knowledge`)},
    ]
    if (folderName) {
        breadcrumbs.push({'@type': 'ListItem', position: 2, name: folderName})
    }
    return {
        title,
        meta: socialMeta({title, description: desc, imageUrl: absoluteUrl(stationLogoImage(station.value))}),
        script: [
            {
                type: 'application/ld+json',
                innerHTML: JSON.stringify({
                    '@context': 'https://schema.org',
                    '@type': 'BreadcrumbList',
                    itemListElement: breadcrumbs,
                }),
            },
        ],
    }
}))
</script>

<template>
    <ViewContent :title="t('pages.public-knowledge-base.title')" :subtitle="t('pages.public-knowledge-base.subtitle')">
        <div class="space-y-6">
            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

            <div class="mb-4">
                <SearchInput
                    v-model="searchQuery"
                    :placeholder="t('publicKb.search')"
                    @input="onSearchInput"
                />
            </div>

            <KnowledgeBaseBreadcrumbs
                v-if="!isSearching"
                :current-folder="currentFolder"
                :view-mode="viewMode"
                @navigate="navigateToFolder"
                @toggle-view-mode="viewMode = viewMode === 'grid' ? 'list' : 'grid'"
            />

            <KbSearchResults
                v-if="isSearching"
                :items="searchItems"
                :searching="searching"
                :total-count="searchItems.length"
            />

            <KnowledgeBaseBrowse
                v-else
                :loading="loading"
                :current-folder="currentFolder"
                :items="items"
                :view-mode="viewMode"
            />
        </div>
    </ViewContent>
</template>
