/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import type {StationFile} from '@/api/media'

defineProps<{
    file: StationFile
    inUse: boolean
    openMenu: boolean
}>()

const emit = defineEmits<{
    'toggle-menu': [fileId: number]
    'edit-file': [file: StationFile]
    'delete-file': [file: StationFile]
}>()

const {t} = useI18n()

function pickEdit(file: StationFile) {
    emit('edit-file', file)
}

function pickDelete(file: StationFile) {
    emit('delete-file', file)
}
</script>

<template>
    <div class="absolute top-1 right-1 flex items-center gap-1" data-file-menu @click.stop>
        <span v-if="!inUse"
              class="text-[10px] uppercase tracking-wider bg-error text-white rounded px-1.5 py-0.5">
            {{ t('stationPages.editor.unusedBadge') }}
        </span>
        <div class="relative">
            <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('stationPages.editor.fileActions')"
                        class="bg-(--bg)/90 backdrop-blur-sm rounded-full !p-1 text-xs"
                        @click="emit('toggle-menu', file.id)"/>
            <div v-if="openMenu"
                 class="absolute right-0 top-full mt-1 w-40 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-20">
                <DropdownMenuItem :icon="['fas', 'pen']" @click="pickEdit(file)">
                    {{ t('stationPages.editor.edit') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fas', 'trash']" icon-class="w-4 text-(--error)"
                                  @click="pickDelete(file)">
                    {{ t('stationPages.editor.deleteFile') }}
                </DropdownMenuItem>
            </div>
        </div>
    </div>
</template>
