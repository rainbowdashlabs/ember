/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import BaseButton from '@/components/button/BaseButton.vue'
import type {StationFile, StationFileFolder, StationFileListing, StationFileTag} from '@/api/media'
import MediaGridItem from './mediagrid/MediaGridItem.vue'

const props = defineProps<{
    folders: StationFileFolder[]
    files: StationFileListing[]
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
        <BaseButton v-for="f in folders" :key="'f-' + f.id"
                    class="!p-0 !flex-col !rounded-theme !border !border-(--border) overflow-hidden hover:!border-primary hover:!bg-primary/5"
                    @click="emit('open-folder', f.id)">
            <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center">
                <font-awesome-icon :icon="['fas', 'folder']" class="text-4xl text-primary"/>
            </div>
            <div class="p-2 text-xs min-w-0 w-full text-left">
                <p class="truncate font-medium" :title="f.name">{{ f.name }}</p>
            </div>
        </BaseButton>

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
