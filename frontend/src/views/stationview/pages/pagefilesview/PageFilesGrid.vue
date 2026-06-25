/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import BaseButton from '@/components/button/BaseButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {pageImageUrl, type PageFile, type PageFileFolder, type PageFileListing, type PageFileTag} from '@/api/pageManage'

const props = defineProps<{
    folders: PageFileFolder[]
    files: PageFileListing[]
    tags: PageFileTag[]
    selectedIds: number[]
    stationUid: string
    multiSelect: boolean
}>()

const emit = defineEmits<{
    'open-folder': [folderId: number]
    'preview-file': [file: PageFile]
    'edit-file': [file: PageFile]
    'delete-file': [file: PageFile]
    'toggle-select': [fileId: number, value: boolean, shift: boolean, index: number]
    'toggle-tag': [fileId: number, tagId: number, currentlyAssigned: boolean]
}>()

const {t} = useI18n()

const openMenuFor = ref<number | null>(null)

function urlFor(f: PageFile): string {
    return f.contentHash && props.stationUid ? pageImageUrl(props.stationUid, f.contentHash) : ''
}

function isImage(f: PageFile): boolean {
    return (f.mimeType ?? '').startsWith('image/')
}

function tagsOf(e: PageFileListing): PageFileTag[] {
    return props.tags.filter(t => e.tagIds.includes(t.id))
}

function isSelected(id: number): boolean {
    return props.selectedIds.includes(id)
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function onCheckboxClick(e: MouseEvent, fileId: number, index: number) {
    const next = !isSelected(fileId)
    emit('toggle-select', fileId, next, e.shiftKey, index)
}

function toggleMenu(fileId: number) {
    openMenuFor.value = openMenuFor.value === fileId ? null : fileId
}

function closeMenu() {
    openMenuFor.value = null
}

function pickEdit(file: PageFile) {
    closeMenu()
    emit('edit-file', file)
}

function pickDelete(file: PageFile) {
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

        <div v-for="(e, idx) in files" :key="'e-' + e.file.id"
             class="relative flex flex-col rounded-theme border overflow-hidden cursor-pointer hover:border-primary transition-colors"
             :class="e.inUse ? 'border-(--border)' : 'border-error/50 bg-error/5'"
             @click="emit('preview-file', e.file)">
            <div v-if="multiSelect"
                 class="absolute top-1 left-1 z-10 flex items-center bg-(--bg)/90 backdrop-blur-sm rounded p-1 cursor-pointer select-none"
                 @click.stop.prevent="onCheckboxClick($event, e.file.id, idx)">
                <CheckboxInput :model-value="isSelected(e.file.id)" class="pointer-events-none"/>
            </div>
            <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center overflow-hidden">
                <img v-if="isImage(e.file)" :src="urlFor(e.file)"
                     :alt="e.file.defaultAltText ?? e.file.fileName"
                     loading="lazy" class="w-full h-full object-cover"/>
                <font-awesome-icon v-else :icon="['fas', 'file']" class="text-3xl text-(--text-muted)"/>
            </div>
            <div class="p-2 text-xs space-y-1 min-w-0 flex-1">
                <p class="truncate font-medium" :title="e.file.fileName">{{ e.file.fileName }}</p>
                <p class="text-(--text-muted)">{{ formatSize(e.file.fileSize) }}</p>
                <div v-if="tagsOf(e).length" class="flex flex-wrap gap-1">
                    <span v-for="tag in tagsOf(e)" :key="tag.id"
                          class="rounded-full px-1.5 py-0.5 text-[10px] text-white"
                          :style="{background: tag.color ?? '#888'}">
                        {{ tag.name }}
                    </span>
                </div>
                <div class="flex flex-wrap gap-1 pt-1" @click.stop>
                    <BaseButton v-for="tag in tags" :key="tag.id" compact
                                class="!rounded-full !border !px-1.5 !py-0.5 !text-[10px] !font-normal"
                                :class="e.tagIds.includes(tag.id) ? '!text-white' : '!text-(--text-muted)'"
                                :style="e.tagIds.includes(tag.id) ? {background: tag.color ?? '#888', borderColor: tag.color ?? '#888'} : {borderColor: 'var(--border)'}"
                                @click="emit('toggle-tag', e.file.id, tag.id, e.tagIds.includes(tag.id))">
                        {{ tag.name }}
                    </BaseButton>
                </div>
            </div>
            <div class="absolute top-1 right-1 flex items-center gap-1" data-file-menu @click.stop>
                <span v-if="!e.inUse"
                      class="text-[10px] uppercase tracking-wider bg-error text-white rounded px-1.5 py-0.5">
                    {{ t('stationPages.editor.unusedBadge') }}
                </span>
                <div class="relative">
                    <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('stationPages.editor.fileActions')"
                                class="bg-(--bg)/90 backdrop-blur-sm rounded-full !p-1 text-xs"
                                @click="toggleMenu(e.file.id)"/>
                    <div v-if="openMenuFor === e.file.id"
                         class="absolute right-0 top-full mt-1 w-40 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-20">
                        <DropdownMenuItem :icon="['fas', 'pen']" @click="pickEdit(e.file)">
                            {{ t('stationPages.editor.edit') }}
                        </DropdownMenuItem>
                        <DropdownMenuItem :icon="['fas', 'trash']" icon-class="w-4 text-(--error)"
                                          @click="pickDelete(e.file)">
                            {{ t('stationPages.editor.deleteFile') }}
                        </DropdownMenuItem>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
