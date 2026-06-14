/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, watch, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import KbBreadcrumb from './knowledgebaseview/KbBreadcrumb.vue'
import KbCreateMenu from './knowledgebaseview/KbCreateMenu.vue'
import KbItemGrid from './knowledgebaseview/KbItemGrid.vue'
import KbItemList from './knowledgebaseview/KbItemList.vue'
import KbEditFolderModal from './knowledgebaseview/KbEditFolderModal.vue'
import KbEditFileModal from './knowledgebaseview/KbEditFileModal.vue'
import KbCreateModals from './knowledgebaseview/KbCreateModals.vue'
import KbSearchResults from './knowledgebaseview/KbSearchResults.vue'
import KbDeleteModals from './knowledgebaseview/KbDeleteModals.vue'
import KbFiltersBar from './knowledgebaseview/KbFiltersBar.vue'
import {useSession} from '@/composables/useSession'
import {useKbTagFilter} from '@/composables/useKbTagFilter'
import {getItem} from '@/api/storage'
import {knowledgeBase, federation} from '@/api'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canEditKnowledge, loaded, isKbPublic} = useSession()

// View mode
const viewMode = ref<'grid' | 'list'>('grid')

const loading = ref(true)
const error = ref('')
const currentFolder = ref<KbFolder | null>(null)
const folders = ref<KbFolder[]>([])
const files = ref<KbFile[]>([])
const sharedFiles = ref<SharedFileEntry[]>([])
const favourites = ref<KbFile[]>([])

// Breadcrumb
const breadcrumbs = ref<KbFolder[]>([])

// Filters
const showFederated = ref(true)
const filterStationId = ref<string | null>(null)
const filterTag = ref('')
const allKbTags = ref<import('@/api/knowledgeBase').KbTag[]>([])

const {fileMatchesTagFilter: mt, folderHasTaggedDescendant: fht, ensureFileTagsLoaded, ensureTagScopeLoaded} = useKbTagFilter()
const fileMatchesTagFilter = (id: number) => mt(id, filterTag.value)
const folderHasTaggedDescendant = (id: number) => fht(id, filterTag.value)

// Unique station names from shared files
const partnerStations = computed(() => {
    const map = new Map<string, string>()
    for (const s of sharedFiles.value) {
        if (s.sourceStationId) map.set(s.sourceStationId, s.stationName)
    }
    return [...map.entries()].map(([id, name]) => ({id, name}))
})

const filteredSharedFiles = computed(() => {
    if (!showFederated.value) return []
    if (filterStationId.value != null) {
        return sharedFiles.value.filter(s => s.sourceStationId === filterStationId.value)
    }
    return sharedFiles.value
})

const filteredFolders = computed(() => {
    if (filterStationId.value != null) return []
    // When a tag filter is active, keep subfolders whose subtree contains at
    // least one matching file so the user can navigate down to the matches.
    if (filterTag.value) return folders.value.filter(f => folderHasTaggedDescendant(f.id))
    return folders.value
})
const filteredFiles = computed(() => {
    if (filterStationId.value != null) return []
    if (!filterTag.value) return files.value
    return files.value.filter(f => fileMatchesTagFilter(f.id))
})

// Search
const searchQuery = ref('')
const searchResults = ref<import('@/api/knowledgeBase').SearchResult[]>([])
const searching = ref(false)
const isSearching = computed(() => searchQuery.value.trim().length > 0)

const filteredSearchResults = computed(() => {
    let results = searchResults.value
    if (!showFederated.value) {
        results = results.filter(r => !r.stationName)
    } else if (filterStationId.value != null) {
        results = results.filter(r => !r.stationName || r.sourceStationId === filterStationId.value)
    }
    if (filterTag.value) {
        // For local results, use the cached per-file tags. Remote (federated)
        // results can't be tag-checked client-side, but the backend search
        // already received the tag filter, so keep them in the list.
        results = results.filter(r => r.stationName || fileMatchesTagFilter(r.file.id))
    }
    return results
})

// Edit modals
const showEditFolderModal = ref(false)
const editFolderData = ref<KbFolder | null>(null)
const showEditFileModal = ref(false)
const editFileData = ref<KbFile | null>(null)

// Delete modals
const showDeleteFolderModal = ref(false)
const folderToDelete = ref<KbFolder | null>(null)
const showDeleteFileModal = ref(false)
const fileToDelete = ref<KbFile | null>(null)

// Create modals ref
const createModalsRef = ref<InstanceType<typeof KbCreateModals> | null>(null)

const favouriteIds = computed(() => new Set(favourites.value.map(f => f.id)))

const isFavouritesView = computed(() => route.query.folderId === 'favourites')

const currentFolderId = computed(() => {
    const param = route.query.folderId
    if (!param || param === 'favourites') return null
    return Number(param)
})

async function toggleFavourite(file: KbFile, event?: MouseEvent) {
    if (event) event.stopPropagation()
    try {
        if (favouriteIds.value.has(file.id)) {
            await knowledgeBase.removeFavourite(file.id)
            favourites.value = favourites.value.filter(f => f.id !== file.id)
            if (isFavouritesView.value) {
                files.value = files.value.filter(f => f.id !== file.id)
            }
        } else {
            await knowledgeBase.addFavourite(file.id)
            favourites.value = [...favourites.value, file]
        }
    } catch {
        error.value = t('common.error')
    }
}

async function loadData() {
    loading.value = true
    error.value = ''
    try {
        if (isFavouritesView.value) {
            const result = await knowledgeBase.browse(null)
            currentFolder.value = null
            folders.value = []
            files.value = result.favourites ?? []
            sharedFiles.value = []
            favourites.value = result.favourites ?? []
            breadcrumbs.value = []
        } else {
            const result = await knowledgeBase.browse(currentFolderId.value)
            currentFolder.value = result.currentFolder
            folders.value = result.folders
            files.value = result.files
            favourites.value = result.favourites ?? []
            await buildBreadcrumbs()

            // Load federated KB content at root level
            if (currentFolderId.value == null) {
                try {
                    const shared = await federation.browseSharedKb()
                    sharedFiles.value = shared.map(s => ({
                        file: {id: s.remoteId, name: s.title, description: s.description} as any,
                        stationName: s.stationName,
                        sourceStationId: s.stationId,
                    }))
                } catch { sharedFiles.value = [] }
            } else {
                sharedFiles.value = []
            }
        }
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

async function buildBreadcrumbs() {
    const crumbs: KbFolder[] = []
    let folder = currentFolder.value
    while (folder) {
        crumbs.unshift(folder)
        if (folder.parentId) {
            try {
                folder = await knowledgeBase.getFolder(folder.parentId)
            } catch {
                break
            }
        } else {
            break
        }
    }
    breadcrumbs.value = crumbs
}

async function copySharedFile(fileId: number) {
    try {
        await federation.copyKbFile(fileId)
        await loadData()
    } catch { error.value = t('common.error') }
}

function navigateToFolder(folderId: number | null) {
    if (folderId === null) {
        router.push({name: 'kb-browse'})
    } else {
        router.push({name: 'kb-browse', query: {folderId}})
    }
}

function navigateToFile(file: KbFile) {
    router.push({name: 'kb-file', params: {id: file.id}})
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
            searchResults.value = await knowledgeBase.search(searchQuery.value.trim(), {
                tag: filterTag.value || undefined,
                federated: showFederated.value,
            })
        } catch {
            searchResults.value = []
        } finally {
            searching.value = false
        }
    }, 300)
}

async function loadTags() {
    try { allKbTags.value = await knowledgeBase.listTags() }
    catch { /* silent */ }
}

watch(filterTag, (tag) => { ensureTagScopeLoaded(tag) }, {immediate: true})

watch([filterTag, files, searchResults], () => {
    if (!filterTag.value) return
    const ids = new Set<number>()
    for (const f of files.value) ids.add(f.id)
    for (const r of searchResults.value) if (!r.stationName) ids.add(r.file.id)
    if (ids.size > 0) ensureFileTagsLoaded([...ids])
}, {immediate: true})

function openEditFolder(folder: KbFolder) {
    editFolderData.value = folder
    showEditFolderModal.value = true
}

function openEditFile(file: KbFile) {
    editFileData.value = file
    showEditFileModal.value = true
}

function confirmDeleteFolder(folder: KbFolder) {
    folderToDelete.value = folder
    showDeleteFolderModal.value = true
}

function confirmDeleteFile(file: KbFile) {
    fileToDelete.value = file
    showDeleteFileModal.value = true
}

async function handleDeleteFolder() {
    if (!folderToDelete.value) return
    try {
        await knowledgeBase.deleteFolder(folderToDelete.value.id)
        showDeleteFolderModal.value = false
        folderToDelete.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

async function handleDeleteFile() {
    if (!fileToDelete.value) return
    try {
        await knowledgeBase.deleteFile(fileToDelete.value.id)
        showDeleteFileModal.value = false
        fileToDelete.value = null
        await loadData()
    } catch {
        error.value = t('common.error')
    }
}

watch(() => route.query.folderId, () => {
    loadData()
})

watch(loaded, (isLoaded) => {
    if (isLoaded) { loadData(); loadTags() }
}, {immediate: true})

onMounted(() => {
    if (loaded.value) { loadData(); loadTags() }
})

const shareCopied = ref(false)
function copyShareLink() {
    const stationUid = getItem('station_id') ?? ''
    const folderId = currentFolder.value?.id
    const url = folderId
        ? `${window.location.origin}/public/kb/${stationUid}?folderId=${folderId}`
        : `${window.location.origin}/public/kb/${stationUid}`
    navigator.clipboard.writeText(url).then(() => {
        shareCopied.value = true
        setTimeout(() => { shareCopied.value = false }, 2000)
    })
}

function navigateToFavourites() {
    router.push({name: 'kb-browse', query: {folderId: 'favourites'}})
}
</script>

<template>
    <ViewContent>
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

        <!-- Search -->
        <div class="mb-4">
            <SearchInput
                v-model="searchQuery"
                :placeholder="t('kb.search')"
                @input="onSearchInput"
            />
        </div>

        <KbFiltersBar
            v-model:show-federated="showFederated"
            v-model:filter-station-id="filterStationId"
            v-model:filter-tag="filterTag"
            :partner-stations="partnerStations"
            :all-kb-tags="allKbTags"
            @refresh="onSearchInput"
        />

        <!-- Breadcrumbs + View Toggle -->
        <KbBreadcrumb
            v-if="!isSearching"
            :current-folder="currentFolder"
            :breadcrumbs="breadcrumbs"
            :is-favourites-view="isFavouritesView"
            :is-kb-public="isKbPublic()"
            :share-copied="shareCopied"
            :view-mode="viewMode"
            @navigate="navigateToFolder"
            @toggle-view-mode="viewMode = viewMode === 'grid' ? 'list' : 'grid'"
            @copy-share-link="copyShareLink"
        />

        <!-- Search Results -->
        <KbSearchResults
            v-if="isSearching"
            :results="filteredSearchResults"
            :searching="searching"
            :total-count="searchResults.length"
            @navigate-file="navigateToFile"
            @copy-shared-file="copySharedFile"
        />

        <!-- Browse -->
        <div v-else>
            <Spinner v-if="loading"/>
            <template v-else>
                <!-- Folder description -->
                <MutedText tag="p" size="sm" v-if="currentFolder?.description">
                    {{ currentFolder.description }}
                </MutedText>

                <!-- Manager actions -->
                <KbCreateMenu
                    v-if="canEditKnowledge()"
                    @create-folder="createModalsRef?.openCreateFolder()"
                    @create-markdown="createModalsRef?.openCreateFile()"
                    @upload="createModalsRef?.openUpload()"
                    @youtube="createModalsRef?.openYoutube()"
                    @link="createModalsRef?.openLink()"
                    @import-document="createModalsRef?.openImportDocument()"
                />

                <!-- Content: Grid or List -->
                <template v-if="filteredFolders.length > 0 || filteredFiles.length > 0 || filteredSharedFiles.length > 0 || (!currentFolder && !isFavouritesView && favourites.length > 0)">
                    <KbItemGrid
                        v-if="viewMode === 'grid'"
                        :folders="filteredFolders"
                        :files="filteredFiles"
                        :shared-files="filteredSharedFiles"
                        :favourites="favourites"
                        :favourite-ids="favouriteIds"
                        :can-manage="canEditKnowledge()"
                        :is-favourites-view="isFavouritesView"
                        :current-folder="currentFolder"
                        @navigate-folder="navigateToFolder"
                        @navigate-file="navigateToFile"
                        @edit-folder="openEditFolder"
                        @delete-folder="confirmDeleteFolder"
                        @edit-file="openEditFile"
                        @delete-file="confirmDeleteFile"
                        @copy-shared-file="copySharedFile"
                        @toggle-favourite="toggleFavourite"
                        @navigate-to-favourites="navigateToFavourites"
                    />
                    <KbItemList
                        v-else
                        :folders="filteredFolders"
                        :files="filteredFiles"
                        :shared-files="filteredSharedFiles"
                        :favourites="favourites"
                        :favourite-ids="favouriteIds"
                        :can-manage="canEditKnowledge()"
                        :is-favourites-view="isFavouritesView"
                        :current-folder="currentFolder"
                        @navigate-folder="navigateToFolder"
                        @navigate-file="navigateToFile"
                        @edit-folder="openEditFolder"
                        @delete-folder="confirmDeleteFolder"
                        @edit-file="openEditFile"
                        @delete-file="confirmDeleteFile"
                        @copy-shared-file="copySharedFile"
                        @navigate-to-favourites="navigateToFavourites"
                    />
                </template>

                <p v-else class="text-[var(--text-muted)] text-center py-8">
                    {{ t('kb.emptyFolder') }}
                </p>
            </template>
        </div>

        <!-- Create Modals -->
        <KbCreateModals
            ref="createModalsRef"
            :current-folder-id="currentFolderId"
            @created="loadData()"
            @error="(msg) => error = msg"
        />

        <!-- Edit Folder Modal -->
        <KbEditFolderModal
            :show="showEditFolderModal"
            :folder="editFolderData"
            @update:show="showEditFolderModal = $event"
            @saved="loadData()"
        />

        <!-- Edit File Modal -->
        <KbEditFileModal
            :show="showEditFileModal"
            :file="editFileData"
            @update:show="showEditFileModal = $event"
            @saved="loadData()"
        />

        <KbDeleteModals
            :show-folder="showDeleteFolderModal"
            :show-file="showDeleteFileModal"
            @update:show-folder="showDeleteFolderModal = $event"
            @update:show-file="showDeleteFileModal = $event"
            @confirm-folder="handleDeleteFolder"
            @confirm-file="handleDeleteFile"
        />
    </ViewContent>
</template>
