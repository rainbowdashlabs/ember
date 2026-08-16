/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import KbBreadcrumb from './knowledgebaseview/KbBreadcrumb.vue'
import KbBrowseSection from './knowledgebaseview/KbBrowseSection.vue'
import KbCreateModals from './knowledgebaseview/KbCreateModals.vue'
import KbDeleteModals from './knowledgebaseview/KbDeleteModals.vue'
import KbEditModals from './knowledgebaseview/KbEditModals.vue'
import KbFiltersBar from './knowledgebaseview/KbFiltersBar.vue'
import KbSearchResults from './knowledgebaseview/KbSearchResults.vue'
import {useKbBrowse} from './knowledgebaseview/useKbBrowse'
import {useKbFilters} from './knowledgebaseview/useKbFilters'
import {useKbNavigation} from './knowledgebaseview/useKbNavigation'
import {useKbItems} from './knowledgebaseview/useKbItems'
import {useKbSearch} from './knowledgebaseview/useKbSearch'
import {useKbShareLink} from './knowledgebaseview/useKbShareLink'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {knowledgeBase} from '@/api'
import {downloadAuthed} from '@/util/downloadAuthed'
import type {KbFolder, KbFile} from '@/api/knowledgeBase'

const {t} = useI18n()
const {canEditKnowledge, loaded, isKbPublic} = useSession()

const viewMode = ref<'grid' | 'list'>('grid')

const navigation = useKbNavigation()
const browse = useKbBrowse(navigation)
const filters = useKbFilters(browse)
const search = useKbSearch(filters)

const {
    folderParam, isFavouritesView, currentFolderId,
    navigateToFolder, navigateToFile, navigateToFederatedFile, navigateToFavourites,
} = navigation
const {currentFolder, breadcrumbs, favourites, favouriteIds, loading, error, loadData, toggleFavourite, copySharedFile} = browse
const {showFederated, filterStationId, filterTag, allKbTags, partnerStations, filteredFolders, filteredFiles, filteredSharedFiles, loadTags} = filters
const {searchQuery, searchResults, searching, isSearching, filteredSearchResults, onSearchInput} = search
const {shareCopied, copyShareLink} = useKbShareLink(currentFolder)

const createModalsRef = ref<InstanceType<typeof KbCreateModals> | null>(null)
const editModalsRef = ref<InstanceType<typeof KbEditModals> | null>(null)

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

async function exportFilePdf(file: KbFile) {
    try {
        await downloadAuthed(knowledgeBase.pdfExportUrl(file.id), `${file.name}.pdf`)
    } catch {
        error.value = t('common.error')
    }
}

const {items, toSearchItems} = useKbItems(
    {
        folders: filteredFolders,
        files: filteredFiles,
        sharedFiles: filteredSharedFiles,
        favourites,
        favouriteIds,
        currentFolder,
        isFavouritesView,
        canManage: computed(() => canEditKnowledge()),
    },
    {
        openFolder: navigateToFolder,
        openFile: navigateToFile,
        openFederatedFile: navigateToFederatedFile,
        openFavourites: navigateToFavourites,
        editFolder: folder => editModalsRef.value?.openFolder(folder),
        deleteFolder: confirmDeleteFolder,
        editFile: file => editModalsRef.value?.openFile(file),
        deleteFile: confirmDeleteFile,
        exportFilePdf,
        copySharedFile,
        removeFavourite: toggleFavourite,
    },
)

const searchItems = computed(() => toSearchItems(filteredSearchResults.value))

watch(folderParam, () => {
    loadData()
})

watch(loaded, (isLoaded) => {
    if (isLoaded) { loadData(); loadTags() }
}, {immediate: true})
</script>

<template>
    <ViewContent
        :title="t('pages.kb-browse.title')"
        :subtitle="t('pages.kb-browse.subtitle')"
    >
        <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

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

        <KbSearchResults
            v-if="isSearching"
            :items="searchItems"
            :searching="searching"
            :total-count="searchResults.length"
        />

        <KbBrowseSection
            v-else
            :loading="loading"
            :current-folder="currentFolder"
            :view-mode="viewMode"
            :items="items"
            :can-manage="canEditKnowledge()"
            @create-folder="createModalsRef?.openCreateFolder()"
            @create-markdown="createModalsRef?.openCreateFile()"
            @upload="createModalsRef?.openUpload()"
            @youtube="createModalsRef?.openYoutube()"
            @link="createModalsRef?.openLink()"
            @import-document="createModalsRef?.openImportDocument()"
        />

        <KbCreateModals
            ref="createModalsRef"
            :current-folder-id="currentFolderId"
            @created="loadData()"
            @error="(msg) => error = msg"
        />

        <KbEditModals
            ref="editModalsRef"
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
