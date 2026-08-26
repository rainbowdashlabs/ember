/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import DragList from '@/components/input/DragList.vue'
import CellImagePickerMultiItem from './cellimagepicker/CellImagePickerMultiItem.vue'
import {type GalleryItem} from '@/api/pageManage'
import {mediaFileUrl, type StationFile} from '@/api/media'
import {moveWithin} from '@/util/reorder'

const itemsModel = defineModel<GalleryItem[] | null>('items')
const imageHashModel = defineModel<string | null>('imageHash')

const props = withDefaults(defineProps<{
    multi?: boolean
    stationUid: string
}>(), {
    multi: false,
})

const {t} = useI18n()

const items = computed<GalleryItem[]>(() => {
    if (props.multi) return Array.isArray(itemsModel.value) ? itemsModel.value : []
    return imageHashModel.value ? [{imageHash: imageHashModel.value}] : []
})

function newItemFromFile(f: StationFile): GalleryItem | null {
    if (!f.contentHash) return null
    return {imageHash: f.contentHash, altText: f.defaultAltText ?? '', subtext: f.defaultDescription ?? ''}
}

function pick(p: {file: StationFile}) {
    if (!p.file.contentHash) return
    if (props.multi) {
        const item = newItemFromFile(p.file)
        if (item) itemsModel.value = [...items.value, item]
    } else {
        imageHashModel.value = p.file.contentHash
    }
}

function pickMany(payloads: Array<{file: StationFile}>) {
    if (!props.multi) return
    const added: GalleryItem[] = []
    for (const p of payloads) {
        const item = newItemFromFile(p.file)
        if (item) added.push(item)
    }
    if (added.length > 0) itemsModel.value = [...items.value, ...added]
}

function removeAt(i: number) {
    if (props.multi) itemsModel.value = items.value.filter((_, idx) => idx !== i)
    else imageHashModel.value = null
}

function moveAt(fromIndex: number, toIndex: number) {
    if (!props.multi) return
    itemsModel.value = moveWithin(items.value, fromIndex, toIndex)
}

function updateField(i: number, field: 'altText' | 'subtext', value: string) {
    if (!props.multi) return
    itemsModel.value = items.value.map((it, idx) => idx === i ? {...it, [field]: value} : it)
}

function swapAt(i: number, payload: {file: StationFile}) {
    if (!payload.file.contentHash) return
    if (props.multi) {
        itemsModel.value = items.value.map(
            (it, idx) => idx === i ? {...it, imageHash: payload.file.contentHash!} : it)
    } else {
        imageHashModel.value = payload.file.contentHash
    }
}

</script>

<template>
    <div class="space-y-3">
        <!-- Multi-mode: vertical list of items with reorder + alt + subtext -->
        <div
            v-if="multi && items.length > 0"
            class="space-y-2 pr-1"
            :class="items.length > 5 ? 'max-h-96 overflow-y-auto' : ''"
        >
            <DragList
                :items="items"
                :key-fn="(item, i) => item.imageHash + '-' + i"
                class="space-y-2"
                @reorder="moveAt"
            >
                <template #default="{item, index}">
                    <CellImagePickerMultiItem
                        :item="item"
                        :station-uid="stationUid"
                        @remove="removeAt(index)"
                        @update-field="(field, value) => updateField(index, field, value)"
                        @swap-image="swapAt(index, $event)"
                    />
                </template>
            </DragList>
        </div>

        <!-- Single-mode: one image preview -->
        <div v-if="!multi && items.length > 0" class="flex items-start gap-2">
            <img :src="mediaFileUrl(stationUid, items[0]?.imageHash ?? '')" alt=""
                 class="w-32 h-32 object-cover rounded-theme border border-(--border)"/>
            <div class="flex flex-col gap-1">
                <MediaBrowseButton
                    :station-uid="stationUid"
                    mime-prefix="image/"
                    :label="t('stationPages.editor.replaceImage')"
                    @pick="swapAt(0, $event)"
                />
                <IconButton
                    :icon="['fas', 'trash']" :label="t('common.delete')"
                    class="text-error" @click="removeAt(0)"
                />
            </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
            <MediaBrowseButton
                :station-uid="stationUid"
                mime-prefix="image/"
                :multiple="multi"
                :label="multi ? t('stationPages.editor.addImage') : t('stationPages.editor.uploadImage')"
                @pick="pick"
                @pick-many="pickMany"
            />
        </div>
    </div>
</template>
