/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import IconButton from '@/components/button/IconButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import * as publicKb from '@/api/publicKb'
import type {PublicStationInfo, PublicBrowseResponse, PublicSearchResult} from '@/api/publicKb'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()

const stationUid = computed(() => route.params.stationUid as string)
const currentFolderId = computed(() => {
    const param = route.query.folderId
    return param ? Number(param) : null
})

const loading = ref(true)
const error = ref('')
const stationInfo = ref<PublicStationInfo | null>(null)
const currentFolder = ref<KbFolder | null>(null)
const folders = ref<KbFolder[]>([])
const files = ref<KbFile[]>([])
const breadcrumbs = ref<KbFolder[]>([])

// Search
const searchQuery = ref('')
const searchResults = ref<PublicSearchResult[]>([])
const searching = ref(false)
const isSearching = computed(() => searchQuery.value.trim().length > 0)

function fileIcon(file: KbFile): string[] {
    switch (file.fileType) {
        case KbFileType.MARKDOWN:
        case KbFileType.TEXT:
            return ['fas', 'file-lines']
        case KbFileType.PDF:
            return ['fas', 'file-pdf']
        case KbFileType.IMAGE:
            return ['fas', 'image']
        case KbFileType.YOUTUBE:
            return ['fab', 'youtube']
        case KbFileType.LINK:
            return ['fas', 'link']
        default:
            return ['fas', 'file']
    }
}

async function loadStationInfo() {
    try {
        stationInfo.value = await publicKb.getStationInfo(stationUid.value)
    } catch {
        error.value = t('common.error')
    }
}

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        const result: PublicBrowseResponse = await publicKb.browse(stationUid.value, currentFolderId.value)
        currentFolder.value = result.currentFolder
        folders.value = result.folders
        files.value = result.files
        buildBreadcrumbs(result.currentFolder)
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

function buildBreadcrumbs(folder: KbFolder | null) {
    // For public view we only show the current folder name in breadcrumbs
    // since we don't have a getFolder endpoint for parents in the public API.
    // We rebuild breadcrumbs by navigating — each level adds itself.
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

watch(() => route.query.folderId, () => {
    loadData()
})

onMounted(() => {
    loadStationInfo()
    loadData()
})
</script>

<template>
    <div class="py-8">
        <div class="max-w-5xl mx-auto px-4 py-8">
            <!-- Station name header -->
            <PageHeader v-if="stationInfo" class="text-2xl font-bold mb-6">
                {{ stationInfo.stationName }}
            </PageHeader>

            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

            <!-- Search -->
            <div class="mb-4">
                <TextInput
                    v-model="searchQuery"
                    :placeholder="t('publicKb.search')"
                    @input="onSearchInput"
                />
            </div>

            <!-- Breadcrumbs -->
            <nav v-if="!isSearching" class="flex items-center gap-1 text-sm flex-wrap mb-4">
                <IconButton
                    v-if="currentFolder"
                    :icon="['fas', 'chevron-up']"
                    :label="t('publicKb.backToBrowse')"
                    class="mr-1"
                    @click="navigateToFolder(currentFolder.parentId)"
                />
                <IconButton
                    :icon="['fas', 'house']"
                    :label="t('publicKb.backToBrowse')"
                    :class="{'!text-[var(--primary)]': !currentFolder}"
                    @click="navigateToFolder(null)"
                />
                <template v-if="currentFolder">
                    <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
                    <span class="font-semibold text-[var(--primary)]">
                        {{ currentFolder.name }}
                    </span>
                </template>
            </nav>

            <!-- Search Results -->
            <div v-if="isSearching">
                <SectionHeader class="text-lg font-semibold mb-3">{{ t('publicKb.searchResults') }}</SectionHeader>
                <Spinner v-if="searching"/>
                <p v-else-if="searchResults.length === 0" class="text-[var(--text-muted)]">
                    {{ t('publicKb.noResults') }}
                </p>
                <div v-else class="flex flex-col gap-2">
                    <NeutralContainer
                        v-for="result in searchResults"
                        :key="result.file.id"
                        class="cursor-pointer hover:border-[var(--primary)] transition-colors"
                        @click="navigateToFile(result.file)"
                    >
                        <div class="flex items-start gap-3 p-2">
                            <font-awesome-icon :icon="fileIcon(result.file)" class="text-xl text-[var(--primary)] mt-0.5"/>
                            <div class="flex-1 min-w-0">
                                <span class="text-sm font-medium">{{ result.file.name }}</span>
                                <p v-if="result.snippet" class="text-xs text-[var(--text-muted)] mt-1 line-clamp-2" v-html="result.snippet"/>
                                <p v-else-if="result.file.description" class="text-xs text-[var(--text-muted)] mt-1 truncate">
                                    {{ result.file.description }}
                                </p>
                            </div>
                        </div>
                    </NeutralContainer>
                </div>
            </div>

            <!-- Browse -->
            <div v-else>
                <Spinner v-if="loading"/>
                <template v-else>
                    <!-- Folder description -->
                    <MutedText tag="p" size="sm" v-if="currentFolder?.description">
                        {{ currentFolder.description }}
                    </MutedText>

                    <!-- Content grid -->
                    <template v-if="folders.length > 0 || files.length > 0">
                        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
                            <!-- Folders -->
                            <NeutralContainer
                                v-for="folder in folders"
                                :key="'folder-' + folder.id"
                                class="cursor-pointer hover:border-[var(--primary)] transition-colors"
                                @click="navigateToFolder(folder.id)"
                            >
                                <div class="flex flex-col items-center gap-2 p-2 text-center">
                                    <img
                                        v-if="folder.iconUrl"
                                        :src="publicKb.folderIconUrl(stationUid, folder.id)"
                                        :alt="folder.name"
                                        class="w-8 h-8 rounded object-cover"
                                        @error="($event.target as HTMLImageElement).style.display = 'none'"
                                    />
                                    <font-awesome-icon v-else :icon="['fas', 'folder']"
                                                       class="text-2xl text-[var(--accent)]"/>
                                    <span class="text-sm font-medium truncate w-full">{{ folder.name }}</span>
                                    <span v-if="folder.description"
                                          class="text-xs text-[var(--text-muted)] truncate w-full">
                                        {{ folder.description }}
                                    </span>
                                </div>
                            </NeutralContainer>

                            <!-- Files -->
                            <NeutralContainer
                                v-for="file in files"
                                :key="'file-' + file.id"
                                class="cursor-pointer hover:border-[var(--primary)] transition-colors"
                                @click="navigateToFile(file)"
                            >
                                <div class="flex flex-col items-center gap-2 p-2 text-center">
                                    <font-awesome-icon :icon="fileIcon(file)" class="text-2xl text-[var(--primary)]"/>
                                    <span class="text-sm font-medium truncate w-full">{{ file.name }}</span>
                                    <span v-if="file.description"
                                          class="text-xs text-[var(--text-muted)] truncate w-full">
                                        {{ file.description }}
                                    </span>
                                </div>
                            </NeutralContainer>
                        </div>
                    </template>

                    <p v-else class="text-[var(--text-muted)] text-center py-8">
                        {{ t('publicKb.empty') }}
                    </p>
                </template>
            </div>
        </div>
    </div>
</template>
