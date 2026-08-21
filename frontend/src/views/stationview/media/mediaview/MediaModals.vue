/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MediaFileEditModal from '@/components/media/MediaFileEditModal.vue'
import MediaFolderTagModals from './MediaFolderTagModals.vue'
import MediaFilePreviewModal from './MediaFilePreviewModal.vue'
import MediaBulkModals from './MediaBulkModals.vue'
import type {StationFile, StationFileFolder, StationFileTag} from '@/api/media'

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

const props = defineProps<{
  folders: StationFileFolder[]
  editingFolder: StationFileFolder | null
  editingTag: StationFileTag | null
  selectedCount: number
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'save-edit', id: number, altText: string, description: string): void
  (e: 'save-folder'): void
  (e: 'save-tag'): void
  (e: 'bulk-move'): void
  (e: 'bulk-delete'): void
}>()
</script>

<template>
  <MediaFileEditModal v-model="editing" @save="(id: number, alt: string, desc: string) => emit('save-edit', id, alt, desc)"/>
  <MediaFolderTagModals
      v-model:folder-open="folderOpen"
      v-model:folder-name="folderName"
      v-model:folder-parent="folderParent"
      v-model:tag-open="tagOpen"
      v-model:tag-name="tagName"
      v-model:tag-color="tagColor"
      :folders="props.folders"
      :editing-folder="props.editingFolder"
      :editing-tag="props.editingTag"
      @save-folder="emit('save-folder')"
      @save-tag="emit('save-tag')"
  />
  <MediaFilePreviewModal :file="previewFile" :station-uid="props.stationUid" @close="previewFile = null"/>
  <MediaBulkModals
      v-model:move-open="moveOpen"
      v-model:delete-open="deleteOpen"
      v-model:move-target="moveTarget"
      :selected-count="props.selectedCount"
      :folders="props.folders"
      @move="emit('bulk-move')"
      @delete="emit('bulk-delete')"
  />
</template>
