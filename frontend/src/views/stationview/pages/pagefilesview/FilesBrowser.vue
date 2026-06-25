/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PageFilesSidebar from './PageFilesSidebar.vue'
import ContentArea from './ContentArea.vue'
import type {PageFile, PageFileFolder, PageFileListing, PageFileTag} from '@/api/pageManage'

const search = defineModel<string>('search', {required: true})

const props = defineProps<{
  folderTree: Array<PageFileFolder & {children: PageFileFolder[]}>
  tags: PageFileTag[]
  activeFolder: number | null
  activeTagFilter: number | null
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
  currentPage: number
  totalPages: number
  pageSize: number
  pageSizeOptions: readonly number[]
}>()

const emit = defineEmits<{
  (e: 'update:active-folder', id: number | null): void
  (e: 'update:active-tag-filter', id: number | null): void
  (e: 'new-folder'): void
  (e: 'edit-folder', f: PageFileFolder): void
  (e: 'remove-folder', f: PageFileFolder): void
  (e: 'new-tag'): void
  (e: 'edit-tag', t: PageFileTag): void
  (e: 'remove-tag', t: PageFileTag): void
  (e: 'toggle-multi-select'): void
  (e: 'upload', files: File[]): void
  (e: 'prune'): void
  (e: 'bulk-move'): void
  (e: 'bulk-delete'): void
  (e: 'clear-selection'): void
  (e: 'preview-file', f: PageFile): void
  (e: 'edit-file', f: PageFile): void
  (e: 'delete-file', f: PageFile): void
  (e: 'toggle-select', id: number, value: boolean, shift: boolean, index: number): void
  (e: 'toggle-tag', fileId: number, tagId: number, currentlyAssigned: boolean): void
  (e: 'update:current-page', v: number): void
  (e: 'update:page-size', v: number): void
}>()
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-[260px_1fr] gap-4">
    <PageFilesSidebar
        :folder-tree="props.folderTree"
        :tags="props.tags"
        :active-folder="props.activeFolder"
        :active-tag-filter="props.activeTagFilter"
        @update:active-folder="(id: number | null) => emit('update:active-folder', id)"
        @update:active-tag-filter="(id: number | null) => emit('update:active-tag-filter', id)"
        @new-folder="emit('new-folder')"
        @edit-folder="(f: PageFileFolder) => emit('edit-folder', f)"
        @remove-folder="(f: PageFileFolder) => emit('remove-folder', f)"
        @new-tag="emit('new-tag')"
        @edit-tag="(tag: PageFileTag) => emit('edit-tag', tag)"
        @remove-tag="(tag: PageFileTag) => emit('remove-tag', tag)"
    />

    <ContentArea
        v-model:search="search"
        :loading="props.loading"
        :uploading="props.uploading"
        :upload-error="props.uploadError"
        :pruning="props.pruning"
        :multi-select="props.multiSelect"
        :unused-count="props.unusedCount"
        :selected-ids="props.selectedIds"
        :breadcrumbs="props.breadcrumbs"
        :active-folder="props.activeFolder"
        :visible-folders="props.visibleFolders"
        :filtered="props.filtered"
        :paged-files="props.pagedFiles"
        :tags="props.tags"
        :station-uid="props.stationUid"
        :current-page="props.currentPage"
        :total-pages="props.totalPages"
        :page-size="props.pageSize"
        :page-size-options="props.pageSizeOptions"
        @toggle-multi-select="emit('toggle-multi-select')"
        @upload="(fs: File[]) => emit('upload', fs)"
        @prune="emit('prune')"
        @navigate="(id: number | null) => emit('update:active-folder', id)"
        @bulk-move="emit('bulk-move')"
        @bulk-delete="emit('bulk-delete')"
        @clear-selection="emit('clear-selection')"
        @open-folder="(id: number) => emit('update:active-folder', id)"
        @preview-file="(f: PageFile) => emit('preview-file', f)"
        @edit-file="(f: PageFile) => emit('edit-file', f)"
        @delete-file="(f: PageFile) => emit('delete-file', f)"
        @toggle-select="(id: number, v: boolean, s: boolean, idx: number) => emit('toggle-select', id, v, s, idx)"
        @toggle-tag="(fid: number, tid: number, a: boolean) => emit('toggle-tag', fid, tid, a)"
        @update:current-page="(v: number) => emit('update:current-page', v)"
        @update:page-size="(v: number) => emit('update:page-size', v)"
    />
  </div>
</template>
