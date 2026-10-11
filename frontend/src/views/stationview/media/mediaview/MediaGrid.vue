/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import type {FileListing, StationFile, StationFileFolder, StationFileTag} from '@/api/generated/schema'
import MediaGridItem from './mediagrid/MediaGridItem.vue'
import MediaFolderTile from './mediagrid/MediaFolderTile.vue'

const props = defineProps<{
    folders: StationFileFolder[]
    files: FileListing[]
    tags: StationFileTag[]
    selectedIds: number[]
    stationUid: string
    multiSelect: boolean
}>()

const emit = defineEmits<{
    'open-folder': [folderId: number]
    'preview-file': [file: StationFile]
    'edit-file': [file: StationFile]
    'delete-file': [file: StationFile]
    'toggle-select': [fileId: number, value: boolean, shift: boolean, index: number]
    'toggle-tag': [fileId: number, tagId: number, currentlyAssigned: boolean]
}>()

const openMenuFor = ref<number | null>(null)

function isSelected(id: number): boolean {
    return props.selectedIds.includes(id)
}

function toggleMenu(fileId: number) {
    openMenuFor.value = openMenuFor.value === fileId ? null : fileId
}

function closeMenu() {
    openMenuFor.value = null
}

function onEdit(file: StationFile) {
    closeMenu()
    emit('edit-file', file)
}

function onDelete(file: StationFile) {
    closeMenu()
    emit('delete-file', file)
}

function onDocClick(e: MouseEvent) {
    const target = e.target as Element | null
    if (target?.closest('[data-file-menu]')) return
    closeMenu()
}

onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
    <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
        <MediaFolderTile v-for="f in folders" :key="'f-' + f.id" :folder="f" @open="emit('open-folder', $event)"/>

        <MediaGridItem v-for="(e, idx) in files" :key="'e-' + e.file.id"
                           :entry="e" :index="idx" :tags="tags" :station-uid="stationUid"
                           :multi-select="multiSelect" :selected="isSelected(e.file.id)"
                           :open-menu="openMenuFor === e.file.id"
                           @preview-file="emit('preview-file', $event)"
                           @edit-file="onEdit($event)"
                           @delete-file="onDelete($event)"
                           @toggle-select="(fileId, value, shift, index) => emit('toggle-select', fileId, value, shift, index)"
                           @toggle-tag="(fileId, tagId, assigned) => emit('toggle-tag', fileId, tagId, assigned)"
                           @toggle-menu="toggleMenu($event)"/>
    </div>
</template>
