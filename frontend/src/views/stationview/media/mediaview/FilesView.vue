/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FilesBrowser from './FilesBrowser.vue'
import MediaModals from './MediaModals.vue'
import type {FileListing, StationFile, StationFileFolder, StationFileTag} from '@/api/generated/schema'
import type {FolderTreeNode} from './useMediaFolderTree'

const search = defineModel<string>('search', {required: true})
const activeFolder = defineModel<number | null>('activeFolder', {required: true})
const activeTagFilter = defineModel<number | null>('activeTagFilter', {required: true})
const editing = defineModel<StationFile | null>('editing', {required: true})
const folderOpen = defineModel<boolean>('folderOpen', {required: true})
const folderName = defineModel<string>('folderName', {required: true})
const folderParent = defineModel<number | null>('folderParent', {required: true})
const tagOpen = defineModel<boolean>('tagOpen', {required: true})
const tagName = defineModel<string>('tagName', {required: true})
const tagColor = defineModel<string>('tagColor', {required: true})
const moveOpen = defineModel<boolean>('moveOpen', {required: true})
const deleteOpen = defineModel<boolean>('deleteOpen', {required: true})
const moveTarget = defineModel<number | null>('moveTarget', {required: true})
const previewFile = defineModel<StationFile | null>('previewFile', {required: true})
const currentPage = defineModel<number>('currentPage', {required: true})
const pageSize = defineModel<number>('pageSize', {required: true})

const props = defineProps<{
  folderTree: FolderTreeNode[]
  tags: StationFileTag[]
  loading: boolean
  uploading: boolean
  uploadError: string | null
  pruning: boolean
  multiSelect: boolean
  unusedCount: number
  selectedIds: number[]
  breadcrumbs: StationFileFolder[]
  visibleFolders: StationFileFolder[]
  filtered: FileListing[]
  pagedFiles: FileListing[]
  stationUid: string
  totalPages: number
  pageSizeOptions: readonly number[]
  folders: StationFileFolder[]
  editingFolder: StationFileFolder | null
  editingTag: StationFileTag | null
}>()

const emit = defineEmits<{
  'new-folder': []
  'edit-folder': [f: StationFileFolder]
  'remove-folder': [f: StationFileFolder]
  'new-tag': []
  'edit-tag': [t: StationFileTag]
  'remove-tag': [t: StationFileTag]
  'toggle-multi-select': []
  upload: [files: File[]]
  prune: []
  'open-bulk-move': []
  'clear-selection': []
  'preview-file': [f: StationFile]
  'edit-file': [f: StationFile]
  'delete-file': [f: StationFile]
  'toggle-select': [id: number, value: boolean, shift: boolean, index: number]
  'toggle-tag': [fileId: number, tagId: number, currentlyAssigned: boolean]
  'save-edit': [id: number, altText: string, description: string]
  'save-folder': []
  'save-tag': []
  'bulk-move': []
  'bulk-delete': []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('stationPages.editor.filesTitle') }}</SubHeader>

    <FilesBrowser
        v-model:search="search"
        :folder-tree="props.folderTree"
        :tags="props.tags"
        :active-folder="activeFolder"
        :active-tag-filter="activeTagFilter"
        :loading="props.loading"
        :uploading="props.uploading"
        :upload-error="props.uploadError"
        :pruning="props.pruning"
        :multi-select="props.multiSelect"
        :unused-count="props.unusedCount"
        :selected-ids="props.selectedIds"
        :breadcrumbs="props.breadcrumbs"
        :visible-folders="props.visibleFolders"
        :filtered="props.filtered"
        :paged-files="props.pagedFiles"
        :station-uid="props.stationUid"
        :current-page="currentPage"
        :total-pages="props.totalPages"
        :page-size="pageSize"
        :page-size-options="props.pageSizeOptions"
        @update:active-folder="(id: number | null) => activeFolder = id"
        @update:active-tag-filter="(id: number | null) => activeTagFilter = id"
        @new-folder="emit('new-folder')"
        @edit-folder="(f: StationFileFolder) => emit('edit-folder', f)"
        @remove-folder="(f: StationFileFolder) => emit('remove-folder', f)"
        @new-tag="emit('new-tag')"
        @edit-tag="(tg: StationFileTag) => emit('edit-tag', tg)"
        @remove-tag="(tg: StationFileTag) => emit('remove-tag', tg)"
        @toggle-multi-select="emit('toggle-multi-select')"
        @upload="(fs: File[]) => emit('upload', fs)"
        @prune="emit('prune')"
        @bulk-move="emit('open-bulk-move')"
        @bulk-delete="deleteOpen = true"
        @clear-selection="emit('clear-selection')"
        @preview-file="(f: StationFile) => emit('preview-file', f)"
        @edit-file="(f: StationFile) => emit('edit-file', f)"
        @delete-file="(f: StationFile) => emit('delete-file', f)"
        @toggle-select="(id: number, v: boolean, s: boolean, idx: number) => emit('toggle-select', id, v, s, idx)"
        @toggle-tag="(fid: number, tid: number, a: boolean) => emit('toggle-tag', fid, tid, a)"
        @update:current-page="(v: number) => currentPage = v"
        @update:page-size="(v: number) => pageSize = v"
    />

    <MediaModals
        v-model:editing="editing"
        v-model:folder-open="folderOpen"
        v-model:folder-name="folderName"
        v-model:folder-parent="folderParent"
        v-model:tag-open="tagOpen"
        v-model:tag-name="tagName"
        v-model:tag-color="tagColor"
        v-model:move-open="moveOpen"
        v-model:delete-open="deleteOpen"
        v-model:move-target="moveTarget"
        v-model:preview-file="previewFile"
        :folders="props.folders"
        :editing-folder="props.editingFolder"
        :editing-tag="props.editingTag"
        :selected-count="props.selectedIds.length"
        :station-uid="props.stationUid"
        @save-edit="(id: number, alt: string, desc: string) => emit('save-edit', id, alt, desc)"
        @save-folder="emit('save-folder')"
        @save-tag="emit('save-tag')"
        @bulk-move="emit('bulk-move')"
        @bulk-delete="emit('bulk-delete')"
    />
  </div>
</template>
