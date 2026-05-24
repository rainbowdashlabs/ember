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
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import KbBreadcrumb from './knowledgebaseview/KbBreadcrumb.vue'
import KbCreateMenu from './knowledgebaseview/KbCreateMenu.vue'
import KbItemGrid from './knowledgebaseview/KbItemGrid.vue'
import KbItemList from './knowledgebaseview/KbItemList.vue'
import KbEditFolderModal from './knowledgebaseview/KbEditFolderModal.vue'
import KbEditFileModal from './knowledgebaseview/KbEditFileModal.vue'
import KbCreateModals from './knowledgebaseview/KbCreateModals.vue'
import KbSearchResults from './knowledgebaseview/KbSearchResults.vue'
import {useSession} from '@/composables/useSession'
import {getItem} from '@/api/storage'
import {knowledgeBase, federation} from '@/api'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {KbFolder, KbFile, SharedFileEntry} from '@/api/knowledgeBase'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {canManageKnowledge, loaded, isKbPublic} = useSession()

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
        results = results.filter(r => r.stationName || true)
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
            sharedFiles.value = result.sharedFiles ?? []
            favourites.value = result.favourites ?? []
            await buildBreadcrumbs()
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
            <TextInput
                v-model="searchQuery"
                :placeholder="t('kb.search')"
                @input="onSearchInput"
            />
        </div>

        <!-- Filters -->
        <div class="flex flex-wrap items-center gap-2 mb-4">
            <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated; onSearchInput()">
                <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
                {{ t('federation.shared') }}
            </SelectionToggleButton>
            <SelectInput v-if="showFederated && partnerStations.length > 0" :model-value="filterStationId != null ? String(filterStationId) : ''" class="!w-auto !text-xs !py-1" @update:model-value="(v: string | undefined) => { filterStationId = v || null; onSearchInput() }">
                <option value="">{{ t('kb.allStations') }}</option>
                <option v-for="station in partnerStations" :key="station.id" :value="String(station.id)">
                    {{ station.name }}
                </option>
            </SelectInput>
            <SelectInput v-if="allKbTags.length > 0" v-model="filterTag" class="!w-auto !text-xs !py-1" @change="onSearchInput()">
                <option value="">{{ t('kb.allTags') }}</option>
                <option v-for="tag in allKbTags" :key="tag.id" :value="tag.name">
                    {{ tag.name }}
                </option>
            </SelectInput>
        </div>

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
                <p v-if="currentFolder?.description" class="text-sm text-[var(--text-muted)] mb-4">
                    {{ currentFolder.description }}
                </p>

                <!-- Manager actions -->
                <KbCreateMenu
                    v-if="canManageKnowledge()"
                    @create-folder="createModalsRef?.openCreateFolder()"
                    @create-markdown="createModalsRef?.openCreateFile()"
                    @upload="createModalsRef?.openUpload()"
                    @youtube="createModalsRef?.openYoutube()"
                    @link="createModalsRef?.openLink()"
                    @import-document="createModalsRef?.openImportDocument()"
                />

                <!-- Content: Grid or List -->
                <template v-if="folders.length > 0 || files.length > 0 || filteredSharedFiles.length > 0 || (!currentFolder && !isFavouritesView && favourites.length > 0)">
                    <KbItemGrid
                        v-if="viewMode === 'grid'"
                        :folders="folders"
                        :files="files"
                        :shared-files="filteredSharedFiles"
                        :favourites="favourites"
                        :favourite-ids="favouriteIds"
                        :can-manage="canManageKnowledge()"
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
                        :folders="folders"
                        :files="files"
                        :shared-files="filteredSharedFiles"
                        :favourites="favourites"
                        :favourite-ids="favouriteIds"
                        :can-manage="canManageKnowledge()"
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

        <!-- Delete Folder Confirmation -->
        <Modal v-model="showDeleteFolderModal">
            <SubHeader class="text-lg font-semibold mb-3">{{ t('kb.deleteFolder') }}</SubHeader>
            <p class="mb-4">{{ t('kb.deleteFolderConfirm') }}</p>
            <div class="flex gap-2 justify-end">
                <SecondaryButton @click="showDeleteFolderModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :label="t('common.delete')" @click="handleDeleteFolder">
                    {{ t('common.delete') }}
                </DeleteButton>
            </div>
        </Modal>

        <!-- Delete File Confirmation -->
        <Modal v-model="showDeleteFileModal">
            <SubHeader class="text-lg font-semibold mb-3">{{ t('kb.deleteFile') }}</SubHeader>
            <p class="mb-4">{{ t('kb.deleteFileConfirm') }}</p>
            <div class="flex gap-2 justify-end">
                <SecondaryButton @click="showDeleteFileModal = false">{{ t('common.cancel') }}</SecondaryButton>
                <DeleteButton :label="t('common.delete')" @click="handleDeleteFile">
                    {{ t('common.delete') }}
                </DeleteButton>
            </div>
        </Modal>
    </ViewContent>
</template>
