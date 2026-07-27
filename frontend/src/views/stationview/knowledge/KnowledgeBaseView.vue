/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import KbBreadcrumb from './knowledgebaseview/KbBreadcrumb.vue'
import KbBrowseSection from './knowledgebaseview/KbBrowseSection.vue'
import KbEditFolderModal from './knowledgebaseview/KbEditFolderModal.vue'
import KbEditFileModal from './knowledgebaseview/KbEditFileModal.vue'
import KbCreateModals from './knowledgebaseview/KbCreateModals.vue'
import KbSearchResults from './knowledgebaseview/KbSearchResults.vue'
import KbDeleteModals from './knowledgebaseview/KbDeleteModals.vue'
import KbFiltersBar from './knowledgebaseview/KbFiltersBar.vue'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useKbTagFilter} from '@/composables/useKbTagFilter'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {getItem} from '@/api/storage'
import {knowledgeBase, federation} from '@/api'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canEditKnowledge, loaded, isKbPublic} = useSession()

// View mode
const viewMode = ref<'grid' | 'list'>('grid')

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

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
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
}, {autoLoad: false})

const {
    show: showDeleteFolderModal,
    request: confirmDeleteFolder,
    confirm: handleDeleteFolder,
} = useConfirmAction<KbFolder>({
    onConfirm: f => knowledgeBase.deleteFolder(f.id),
    onSuccess: () => loadData(),
    error,
})

const {
    show: showDeleteFileModal,
    request: confirmDeleteFile,
    confirm: handleDeleteFile,
} = useConfirmAction<KbFile>({
    onConfirm: f => knowledgeBase.deleteFile(f.id),
    onSuccess: () => loadData(),
    error,
})

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
    try {
        allKbTags.value = await knowledgeBase.listTags()
    } catch {
        return
    }
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

watch(() => route.query.folderId, () => {
    loadData()
})

watch(loaded, (isLoaded) => {
    if (isLoaded) { loadData(); loadTags() }
}, {immediate: true})

const {message: shareCopiedMessage, flash: flashShareCopied} = useFlashMessage(2000)
const shareCopied = computed(() => shareCopiedMessage.value !== '')
function copyShareLink() {
    const stationUid = getItem('station_id') ?? ''
    const folderId = currentFolder.value?.id
    const url = folderId
        ? `${window.location.origin}/public/kb/${stationUid}?folderId=${folderId}`
        : `${window.location.origin}/public/kb/${stationUid}`
    navigator.clipboard.writeText(url).then(() => flashShareCopied(url))
}

function navigateToFavourites() {
    router.push({name: 'kb-browse', query: {folderId: 'favourites'}})
}
</script>

<template>
    <ViewContent
        :title="t('pages.kb-browse.title')"
        :subtitle="t('pages.kb-browse.subtitle')"
    >
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

        <KbBrowseSection
            v-else
            :loading="loading"
            :current-folder="currentFolder"
            :is-favourites-view="isFavouritesView"
            :favourites="favourites"
            :view-mode="viewMode"
            :filtered-folders="filteredFolders"
            :filtered-files="filteredFiles"
            :filtered-shared-files="filteredSharedFiles"
            :favourite-ids="favouriteIds"
            :can-manage="canEditKnowledge()"
            @create-folder="createModalsRef?.openCreateFolder()"
            @create-markdown="createModalsRef?.openCreateFile()"
            @upload="createModalsRef?.openUpload()"
            @youtube="createModalsRef?.openYoutube()"
            @link="createModalsRef?.openLink()"
            @import-document="createModalsRef?.openImportDocument()"
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

        <!-- Create Modals -->
        <KbCreateModals
            ref="createModalsRef"
            :current-folder-id="currentFolderId"
            @created="loadData()"
            @error="(msg) => error = msg"
        />

        <!-- Edit Folder Modal -->
        <KbEditFolderModal
            v-model:show="showEditFolderModal"
            :folder="editFolderData"
            @saved="loadData()"
        />

        <!-- Edit File Modal -->
        <KbEditFileModal
            v-model:show="showEditFileModal"
            :file="editFileData"
            @saved="loadData()"
        />

        <KbDeleteModals
            v-model:show-folder="showDeleteFolderModal"
            v-model:show-file="showDeleteFileModal"
            @confirm-folder="handleDeleteFolder"
            @confirm-file="handleDeleteFile"
        />
    </ViewContent>
</template>
