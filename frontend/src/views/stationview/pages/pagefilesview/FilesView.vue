/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FilesBrowser from './FilesBrowser.vue'
import PageFilesModals from './PageFilesModals.vue'
import type {PageFile, PageFileFolder, PageFileListing, PageFileTag} from '@/api/pageManage'

const search = defineModel<string>('search', {required: true})
const activeFolder = defineModel<number | null>('activeFolder', {required: true})
const activeTagFilter = defineModel<number | null>('activeTagFilter', {required: true})
const editing = defineModel<PageFile | null>('editing', {required: true})
const folderOpen = defineModel<boolean>('folderOpen', {required: true})
const folderName = defineModel<string>('folderName', {required: true})
const folderParent = defineModel<number | null>('folderParent', {required: true})
const tagOpen = defineModel<boolean>('tagOpen', {required: true})
const tagName = defineModel<string>('tagName', {required: true})
const tagColor = defineModel<string>('tagColor', {required: true})
const moveOpen = defineModel<boolean>('moveOpen', {required: true})
const deleteOpen = defineModel<boolean>('deleteOpen', {required: true})
const moveTarget = defineModel<number | null>('moveTarget', {required: true})
const previewFile = defineModel<PageFile | null>('previewFile', {required: true})
const currentPage = defineModel<number>('currentPage', {required: true})
const pageSize = defineModel<number>('pageSize', {required: true})

const props = defineProps<{
  folderTree: Array<PageFileFolder & {children: PageFileFolder[]}>
  tags: PageFileTag[]
  loading: boolean
  uploading: boolean
  uploadError: string | null
  pruning: boolean
  multiSelect: boolean
  unusedCount: number
  selectedIds: number[]
  breadcrumbs: PageFileFolder[]
  visibleFolders: PageFileFolder[]
  filtered: PageFileListing[]
  pagedFiles: PageFileListing[]
  stationUid: string
  totalPages: number
  pageSizeOptions: readonly number[]
  folders: PageFileFolder[]
  editingFolder: PageFileFolder | null
  editingTag: PageFileTag | null
}>()

const emit = defineEmits<{
  (e: 'new-folder'): void
  (e: 'edit-folder', f: PageFileFolder): void
  (e: 'remove-folder', f: PageFileFolder): void
  (e: 'new-tag'): void
  (e: 'edit-tag', t: PageFileTag): void
  (e: 'remove-tag', t: PageFileTag): void
  (e: 'toggle-multi-select'): void
  (e: 'upload', files: File[]): void
  (e: 'prune'): void
  (e: 'open-bulk-move'): void
  (e: 'clear-selection'): void
  (e: 'preview-file', f: PageFile): void
  (e: 'edit-file', f: PageFile): void
  (e: 'delete-file', f: PageFile): void
  (e: 'toggle-select', id: number, value: boolean, shift: boolean, index: number): void
  (e: 'toggle-tag', fileId: number, tagId: number, currentlyAssigned: boolean): void
  (e: 'save-edit', id: number, altText: string, description: string): void
  (e: 'save-folder'): void
  (e: 'save-tag'): void
  (e: 'bulk-move'): void
  (e: 'bulk-delete'): void
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
        @edit-folder="(f: PageFileFolder) => emit('edit-folder', f)"
        @remove-folder="(f: PageFileFolder) => emit('remove-folder', f)"
        @new-tag="emit('new-tag')"
        @edit-tag="(tg: PageFileTag) => emit('edit-tag', tg)"
        @remove-tag="(tg: PageFileTag) => emit('remove-tag', tg)"
        @toggle-multi-select="emit('toggle-multi-select')"
        @upload="(fs: File[]) => emit('upload', fs)"
        @prune="emit('prune')"
        @bulk-move="emit('open-bulk-move')"
        @bulk-delete="deleteOpen = true"
        @clear-selection="emit('clear-selection')"
        @preview-file="(f: PageFile) => emit('preview-file', f)"
        @edit-file="(f: PageFile) => emit('edit-file', f)"
        @delete-file="(f: PageFile) => emit('delete-file', f)"
        @toggle-select="(id: number, v: boolean, s: boolean, idx: number) => emit('toggle-select', id, v, s, idx)"
        @toggle-tag="(fid: number, tid: number, a: boolean) => emit('toggle-tag', fid, tid, a)"
        @update:current-page="(v: number) => currentPage = v"
        @update:page-size="(v: number) => pageSize = v"
    />

    <PageFilesModals
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
