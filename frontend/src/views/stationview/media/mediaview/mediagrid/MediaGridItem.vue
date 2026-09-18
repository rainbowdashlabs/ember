/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from '@/components/button/BaseButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {mediaPictureUrl, type StationFile, type StationFileListing, type StationFileTag} from '@/api/media'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import MediaGridItemMenu from './MediaGridItemMenu.vue'
import {formatSize} from '@/util/format'

const props = defineProps<{
    entry: StationFileListing
    index: number
    tags: StationFileTag[]
    stationUid: string
    multiSelect: boolean
    selected: boolean
    openMenu: boolean
}>()

const emit = defineEmits<{
    'preview-file': [file: StationFile]
    'edit-file': [file: StationFile]
    'delete-file': [file: StationFile]
    'toggle-select': [fileId: number, value: boolean, shift: boolean, index: number]
    'toggle-tag': [fileId: number, tagId: number, currentlyAssigned: boolean]
    'toggle-menu': [fileId: number]
}>()

function tagsOf(e: StationFileListing): StationFileTag[] {
    return props.tags.filter(t => e.tagIds.includes(t.id))
}

function onCheckboxClick(e: MouseEvent) {
    const next = !props.selected
    emit('toggle-select', props.entry.file.id, next, e.shiftKey, props.index)
}
</script>

<template>
    <div data-testid="media-file"
         class="relative flex flex-col rounded-theme border overflow-hidden cursor-pointer hover:border-primary transition-colors"
         :class="entry.inUse ? 'border-(--border)' : 'border-error/50 bg-error/5'"
         @click="emit('preview-file', entry.file)">
        <div v-if="multiSelect"
             class="absolute top-1 left-1 z-10 flex items-center bg-(--bg)/90 backdrop-blur-sm rounded p-1 cursor-pointer select-none"
             @click.stop.prevent="onCheckboxClick($event)">
            <CheckboxInput :model-value="selected" class="pointer-events-none"/>
        </div>
        <FileThumbnail
            :url="entry.file.contentHash ? mediaPictureUrl(entry.file.contentHash, 320) : ''"
            :mime-type="entry.file.mimeType"
            :alt="entry.file.defaultAltText ?? entry.file.fileName"
            size="aspect-square w-full"
        />
        <div class="p-2 text-xs space-y-1 min-w-0 flex-1">
            <p class="truncate font-medium" :title="entry.file.fileName">{{ entry.file.fileName }}</p>
            <p class="text-(--text-muted)">{{ formatSize(entry.file.fileSize) }}</p>
            <div v-if="tagsOf(entry).length" class="flex flex-wrap gap-1">
                <span v-for="tag in tagsOf(entry)" :key="tag.id"
                      class="rounded-full px-1.5 py-0.5 text-[10px] text-white"
                      :style="{background: tag.color ?? '#888'}">
                    {{ tag.name }}
                </span>
            </div>
            <div class="flex flex-wrap gap-1 pt-1" @click.stop>
                <BaseButton v-for="tag in tags" :key="tag.id" compact
                            class="!rounded-full !border !px-1.5 !py-0.5 !text-[10px] !font-normal"
                            :class="entry.tagIds.includes(tag.id) ? '!text-white' : '!text-(--text-muted)'"
                            :style="entry.tagIds.includes(tag.id) ? {background: tag.color ?? '#888', borderColor: tag.color ?? '#888'} : {borderColor: 'var(--border)'}"
                            @click="emit('toggle-tag', entry.file.id, tag.id, entry.tagIds.includes(tag.id))">
                    {{ tag.name }}
                </BaseButton>
            </div>
        </div>
        <MediaGridItemMenu :file="entry.file" :in-use="entry.inUse" :open-menu="openMenu"
                               @toggle-menu="emit('toggle-menu', $event)"
                               @edit-file="emit('edit-file', $event)"
                               @delete-file="emit('delete-file', $event)"/>
    </div>
</template>
