/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import {type GalleryItem} from '@/api/pageManage'
import {mediaFileUrl, type StationFile} from '@/api/media'

defineProps<{
    item: GalleryItem
    stationUid: string
}>()

defineEmits<{
    'remove': []
    'update-field': [field: 'altText' | 'subtext', value: string]
    'swap-image': [payload: {file: StationFile}]
}>()

const {t} = useI18n()
</script>

<template>
    <div class="flex items-stretch gap-2 rounded-theme border border-(--border) p-2 bg-bg-light dark:bg-bg-dark">
        <img :src="mediaFileUrl(stationUid, item.imageHash)" alt=""
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
        <MediaBrowseButton
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
