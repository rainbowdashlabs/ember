/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import MediaGrid from './MediaGrid.vue'
import MediaPagination from './MediaPagination.vue'
import Toolbar from './Toolbar.vue'
import Breadcrumbs from './Breadcrumbs.vue'
import BulkActionsBar from './BulkActionsBar.vue'
import type {StationFile, StationFileFolder, StationFileListing, StationFileTag} from '@/api/media'

const search = defineModel<string>('search', {required: true})

const props = defineProps<{
  loading: boolean
  uploading: boolean
  uploadError: string | null
  pruning: boolean
  multiSelect: boolean
  unusedCount: number
  selectedIds: number[]
  breadcrumbs: StationFileFolder[]
  activeFolder: number | null
  visibleFolders: StationFileFolder[]
  filtered: StationFileListing[]
  pagedFiles: StationFileListing[]
  tags: StationFileTag[]
  stationUid: string
  currentPage: number
  totalPages: number
  pageSize: number
  pageSizeOptions: readonly number[]
}>()

const emit = defineEmits<{
  (e: 'toggle-multi-select'): void
  (e: 'upload', files: File[]): void
  (e: 'prune'): void
  (e: 'navigate', id: number | null): void
  (e: 'bulk-move'): void
  (e: 'bulk-delete'): void
  (e: 'clear-selection'): void
  (e: 'open-folder', id: number): void
  (e: 'preview-file', file: StationFile): void
  (e: 'edit-file', file: StationFile): void
  (e: 'delete-file', file: StationFile): void
  (e: 'toggle-select', id: number, value: boolean, shift: boolean, index: number): void
  (e: 'toggle-tag', fileId: number, tagId: number, currentlyAssigned: boolean): void
  (e: 'update:current-page', v: number): void
  (e: 'update:page-size', v: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <Toolbar
        v-model:search="search"
        :multi-select="props.multiSelect"
        :uploading="props.uploading"
        :pruning="props.pruning"
        :unused-count="props.unusedCount"
        @toggle-multi-select="emit('toggle-multi-select')"
        @upload="(fs: File[]) => emit('upload', fs)"
        @prune="emit('prune')"
    />

    <Breadcrumbs
        :breadcrumbs="props.breadcrumbs"
        :active-folder="props.activeFolder"
        @navigate="(id: number | null) => emit('navigate', id)"
    />

    <BulkActionsBar
        :selected-count="props.selectedIds.length"
        @move="emit('bulk-move')"
        @delete="emit('bulk-delete')"
        @clear="emit('clear-selection')"
    />

    <Alert v-if="props.uploadError" variant="error">{{ props.uploadError }}</Alert>

    <AsyncSection
        :empty="props.visibleFolders.length === 0 && props.filtered.length === 0"
        :empty-message="t('stationPages.editor.browseFilesEmpty')"
        :loading="props.loading"
    >
      <MediaGrid
          :folders="props.visibleFolders"
          :files="props.pagedFiles"
          :tags="props.tags"
          :selected-ids="props.selectedIds"
          :station-uid="props.stationUid"
          :multi-select="props.multiSelect"
          @open-folder="(id: number) => emit('open-folder', id)"
          @preview-file="(f: StationFile) => emit('preview-file', f)"
          @edit-file="(f: StationFile) => emit('edit-file', f)"
          @delete-file="(f: StationFile) => emit('delete-file', f)"
          @toggle-select="(id: number, v: boolean, s: boolean, idx: number) => emit('toggle-select', id, v, s, idx)"
          @toggle-tag="(fid: number, tid: number, a: boolean) => emit('toggle-tag', fid, tid, a)"
      />

      <MediaPagination
          :current-page="props.currentPage"
          :total-pages="props.totalPages"
          :page-size="props.pageSize"
          :page-size-options="props.pageSizeOptions"
          @update:current-page="(v: number) => emit('update:current-page', v)"
          @update:page-size="(v: number) => emit('update:page-size', v)"
      />
    </AsyncSection>
  </div>
</template>
