/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch, onMounted, inject} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import type {Ref} from 'vue'
import type {PublicStationInfo as StationInfo} from '@/api/discovery'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo, PublicBrowseResponse, PublicSearchResult} from '@/api/publicKb'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import KnowledgeBaseBreadcrumbs from './publicknowledgebaseview/KnowledgeBaseBreadcrumbs.vue'
import KnowledgeBaseSearchResults from './publicknowledgebaseview/KnowledgeBaseSearchResults.vue'
import KnowledgeBaseBrowse from './publicknowledgebaseview/KnowledgeBaseBrowse.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()

const publicStation = inject<Ref<StationInfo | null>>('publicStation')
const stationUid = computed(() => publicStation?.value?.stationUid ?? route.params.stationUid as string)
const currentFolderId = computed(() => {
    const param = route.query.folderId
    return param ? Number(param) : null
})

const stationInfo = ref<PublicStationInfo | null>(null)
const currentFolder = ref<KbFolder | null>(null)
const folders = ref<KbFolder[]>([])
const files = ref<KbFile[]>([])
const breadcrumbs = ref<KbFolder[]>([])

const searchQuery = ref('')
const searchResults = ref<PublicSearchResult[]>([])
const searching = ref(false)
const isSearching = computed(() => searchQuery.value.trim().length > 0)

async function loadStationInfo() {
    try {
        stationInfo.value = await publicKb.getStationInfo(stationUid.value)
    } catch {
        error.value = t('common.error')
    }
}

const {loading, error, reload} = useAsyncLoader(async () => {
    const result: PublicBrowseResponse = await publicKb.browse(stationUid.value, currentFolderId.value)
    currentFolder.value = result.currentFolder
    folders.value = result.folders
    files.value = result.files
    buildBreadcrumbs(result.currentFolder)
})

function buildBreadcrumbs(folder: KbFolder | null) {
    const crumbs: KbFolder[] = []
    if (folder) {
        crumbs.push(folder)
    }
    breadcrumbs.value = crumbs
}

function navigateToFolder(folderId: number | null) {
    if (folderId === null) {
        router.push({name: 'public-kb', params: {stationUid: stationUid.value}})
    } else {
        router.push({name: 'public-kb', params: {stationUid: stationUid.value}, query: {folderId}})
    }
}

function navigateToFile(file: KbFile) {
    router.push({name: 'public-kb-file', params: {stationUid: stationUid.value, id: file.id}})
}

let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearchInput() {
    if (searchTimeout) clearTimeout(searchTimeout)
    if (!searchQuery.value.trim()) {
        searchResults.value = []
        return
    }
    searchTimeout = setTimeout(async () => {
        searching.value = true
        try {
            searchResults.value = await publicKb.search(stationUid.value, searchQuery.value.trim())
        } catch {
            searchResults.value = []
        } finally {
            searching.value = false
        }
    }, 300)
}

useHead(computed(() => {
    if (!stationInfo.value) return {}
    const folderName = currentFolder.value?.name
    const title = folderName
        ? `${folderName} — Wiki — ${stationInfo.value.stationName}`
        : `Wiki — ${stationInfo.value.stationName}`
    const desc = currentFolder.value?.description || `Öffentliches Wiki von ${stationInfo.value.stationName}`
    const breadcrumbs: { '@type': string; position: number; name: string; item?: string }[] = [
        {'@type': 'ListItem', position: 1, name: stationInfo.value.stationName, item: `/public/station/${stationUid.value}/knowledge`},
    ]
    if (folderName) {
        breadcrumbs.push({'@type': 'ListItem', position: 2, name: folderName})
    }
    return {
        title,
        meta: [{name: 'description', content: desc}],
        script: [
            {
                type: 'application/ld+json',
                children: JSON.stringify({
                    '@context': 'https://schema.org',
                    '@type': 'BreadcrumbList',
                    itemListElement: breadcrumbs,
                }),
            },
        ],
    }
}))

watch(() => route.query.folderId, () => {
    reload()
})

onMounted(() => {
    loadStationInfo()
})
</script>

<template>
    <ViewContent>
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
                @navigate="navigateToFolder"
            />

            <KnowledgeBaseSearchResults
                v-if="isSearching"
                :searching="searching"
                :search-results="searchResults"
                @open="navigateToFile"
            />

            <KnowledgeBaseBrowse
                v-else
                :loading="loading"
                :current-folder="currentFolder"
                :folders="folders"
                :files="files"
                :station-uid="stationUid"
                @open-folder="navigateToFolder"
                @open-file="navigateToFile"
            />
        </div>
    </ViewContent>
</template>
