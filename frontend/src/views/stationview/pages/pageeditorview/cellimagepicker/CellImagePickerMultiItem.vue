/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PageFileBrowseButton from '../PageFileBrowseButton.vue'
import {pageImageUrl, type GalleryItem, type PageFile} from '@/api/pageManage'

defineProps<{
    item: GalleryItem
    index: number
    isFirst: boolean
    isLast: boolean
    stationUid: string
}>()

defineEmits<{
    'move-up': []
    'move-down': []
    'remove': []
    'update-field': [field: 'altText' | 'subtext', value: string]
    'swap-image': [payload: {file: PageFile}]
    'drag-start': [ev: DragEvent]
    'drag-over': [ev: DragEvent]
    'drop': []
}>()

const {t} = useI18n()
</script>

<template>
    <div
        draggable="true"
        class="flex items-stretch gap-2 rounded-theme border border-(--border) p-2 bg-bg-light dark:bg-bg-dark"
        @dragstart="$emit('drag-start', $event)"
        @dragover="$emit('drag-over', $event)"
        @drop="$emit('drop')"
    >
        <div class="flex flex-col items-center justify-center gap-1 shrink-0">
            <IconButton
                :icon="['fas', 'angle-up']" :label="t('common.moveUp')"
                :disabled="isFirst" @click="$emit('move-up')"
            />
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-move"/>
            <IconButton
                :icon="['fas', 'angle-down']" :label="t('common.moveDown')"
                :disabled="isLast" @click="$emit('move-down')"
            />
        </div>
        <img :src="pageImageUrl(stationUid, item.imageHash)" alt=""
             class="w-24 h-24 object-cover rounded shrink-0"/>
        <div class="flex-1 flex flex-col gap-1 min-w-0">
            <TextInput
                :model-value="item.altText ?? ''"
                :placeholder="t('stationPages.editor.altText')"
                @update:model-value="$emit('update-field', 'altText', $event ?? '')"
            />
            <TextInput
                :model-value="item.subtext ?? ''"
                :placeholder="t('stationPages.editor.imageDescription')"
                @update:model-value="$emit('update-field', 'subtext', $event ?? '')"
            />
        </div>
        <PageFileBrowseButton
            :station-uid="stationUid"
            mime-prefix="image/"
            :label="t('stationPages.editor.replaceImage')"
            @pick="$emit('swap-image', $event)"
        />
        <IconButton
            :icon="['fas', 'trash']" :label="t('common.delete')"
            class="text-error" @click="$emit('remove')"
        />
    </div>
</template>
