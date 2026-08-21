/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import FilesView from './mediaview/FilesView.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useSession} from '@/composables/useSession'
import {useMediaLibrary} from '@/composables/useMediaLibrary'
import {useMediaFolderTree} from './mediaview/useMediaFolderTree'
import {useMediaFolderForm} from './mediaview/useMediaFolderForm'
import {useMediaTagForm} from './mediaview/useMediaTagForm'
import {useMediaSelection} from './mediaview/useMediaSelection'
import {useMediaUpload} from './mediaview/useMediaUpload'
import {useMediaEditing} from './mediaview/useMediaEditing'
import {useMediaBulkActions} from './mediaview/useMediaBulkActions'
import {PAGE_SIZE_OPTIONS, useMediaPaging} from './mediaview/useMediaPaging'
import type {StationFile} from '@/api/media'

const {t} = useI18n()
const {sessionInfo} = useSession()

const search = ref('')
const activeFolder = ref<number | null>(null)
const activeTagFilter = ref<number | null>(null)
const previewFile = ref<StationFile | null>(null)

const {entries, folders, tags, loading, load, reloadFolders, reloadTags} = useMediaLibrary()
const {folderTree, visibleFolders, breadcrumbs} = useMediaFolderTree(folders, activeFolder)

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

const filtered = computed(() => {
    const q = search.value.trim().toLowerCase()
    return entries.value.filter(e => {
        if ((e.file.folderId ?? null) !== activeFolder.value) return false
        if (activeTagFilter.value !== null && !e.tagIds.includes(activeTagFilter.value)) return false
        if (!q) return true
        return e.file.fileName.toLowerCase().includes(q)
            || (e.file.mimeType ?? '').toLowerCase().includes(q)
            || (e.file.defaultAltText ?? '').toLowerCase().includes(q)
            || (e.file.defaultDescription ?? '').toLowerCase().includes(q)
    })
})

const unusedCount = computed(() => entries.value.filter(e => !e.inUse).length)

const {pageSize, currentPage, totalPages, pagedFiles} = useMediaPaging(filtered, [search, activeFolder, activeTagFilter])
const {selectedIds, multiSelect, toggleSelected, clearSelection, toggleMultiSelect} = useMediaSelection(pagedFiles, activeFolder)
const {uploading, uploadError, uploadMany} = useMediaUpload(entries, activeFolder)
const {
    folderModalOpen,
    folderName,
    folderParent,
    editingFolder,
    openFolderModal,
    openFolderEdit,
    saveFolder,
    removeFolder,
    showDeleteFolder,
    deleteFolderTarget,
    confirmRemoveFolder,
} = useMediaFolderForm(activeFolder, reloadFolders, load)
const {
    tagModalOpen,
    tagName,
    tagColor,
    editingTag,
    openTagModal,
    openTagEdit,
    saveTag,
    removeTag,
    showDeleteTag,
    deleteTagTarget,
    confirmRemoveTag,
} = useMediaTagForm(activeTagFilter, reloadTags, load)
const {
    editing,
    startEdit,
    saveEdit,
    toggleFileTag,
    showDeleteFileModal,
    deleteFileTarget,
    requestDeleteFile,
    confirmDeleteFile,
} = useMediaEditing(entries, selectedIds)
const {
    bulkMoveOpen,
    bulkMoveTarget,
    bulkDeleteOpen,
    openBulkMove,
    runBulkMove,
    runBulkDelete,
    showPruneConfirm,
    pruning,
    runPrune,
    confirmPrune,
} = useMediaBulkActions({entries, selectedIds, activeFolder, clearSelection, reload: load})

function openPreview(f: StationFile) {
    previewFile.value = f
}

onMounted(load)
</script>

<template>
    <ViewContent :title="t('pages.station-media.title')" :subtitle="t('pages.station-media.subtitle')">
        <FilesView
            v-model:search="search"
            v-model:active-folder="activeFolder"
            v-model:active-tag-filter="activeTagFilter"
            v-model:editing="editing"
            v-model:folder-open="folderModalOpen"
            v-model:folder-name="folderName"
            v-model:folder-parent="folderParent"
            v-model:tag-open="tagModalOpen"
            v-model:tag-name="tagName"
            v-model:tag-color="tagColor"
            v-model:move-open="bulkMoveOpen"
            v-model:delete-open="bulkDeleteOpen"
            v-model:move-target="bulkMoveTarget"
            v-model:preview-file="previewFile"
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :folder-tree="folderTree"
            :tags="tags"
            :loading="loading"
            :uploading="uploading"
            :upload-error="uploadError"
            :pruning="pruning"
            :multi-select="multiSelect"
            :unused-count="unusedCount"
            :selected-ids="selectedIds"
            :breadcrumbs="breadcrumbs"
            :visible-folders="visibleFolders"
            :filtered="filtered"
            :paged-files="pagedFiles"
            :station-uid="stationUid"
            :total-pages="totalPages"
            :page-size-options="PAGE_SIZE_OPTIONS"
            :folders="folders"
            :editing-folder="editingFolder"
            :editing-tag="editingTag"
            @new-folder="openFolderModal(null)"
            @edit-folder="openFolderEdit"
            @remove-folder="removeFolder"
            @new-tag="openTagModal"
            @edit-tag="openTagEdit"
            @remove-tag="removeTag"
            @toggle-multi-select="toggleMultiSelect"
            @upload="uploadMany"
            @prune="runPrune"
            @open-bulk-move="openBulkMove"
            @clear-selection="clearSelection"
            @preview-file="openPreview"
            @edit-file="startEdit"
            @delete-file="requestDeleteFile"
            @toggle-select="toggleSelected"
            @toggle-tag="toggleFileTag"
            @save-edit="saveEdit"
            @save-folder="saveFolder"
            @save-tag="saveTag"
            @bulk-move="runBulkMove"
            @bulk-delete="runBulkDelete"
        />
        <ConfirmDeleteModal
            v-model="showPruneConfirm"
            :message="t('stationPages.editor.prunePrompt', {count: unusedCount})"
            @confirm="confirmPrune"
        />
        <ConfirmDeleteModal
            v-model="showDeleteFileModal"
            :message="t('stationPages.editor.deleteFilePrompt', {name: deleteFileTarget?.fileName ?? ''})"
            @confirm="confirmDeleteFile"
        />
        <ConfirmDeleteModal
            v-model="showDeleteFolder"
            :message="t('stationPages.editor.folderDeletePrompt', {name: deleteFolderTarget?.name ?? ''})"
            @confirm="confirmRemoveFolder"
        />
        <ConfirmDeleteModal
            v-model="showDeleteTag"
            :message="t('stationPages.editor.tagDeletePrompt', {name: deleteTagTarget?.name ?? ''})"
            @confirm="confirmRemoveTag"
        />
    </ViewContent>
</template>
