/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import PageFileBrowseButton from './PageFileBrowseButton.vue'
import CellImagePickerMultiItem from './cellimagepicker/CellImagePickerMultiItem.vue'
import {pageImageUrl, type GalleryItem, type PageFile} from '@/api/pageManage'

const itemsModel = defineModel<GalleryItem[] | null>('items')
const imageHashModel = defineModel<string | null>('imageHash')

const props = withDefaults(defineProps<{
    multi?: boolean
    pageId: number
    stationUid: string
}>(), {
    multi: false,
})

const {t} = useI18n()
const dragIndex = ref<number | null>(null)

const items = computed<GalleryItem[]>(() => {
    if (props.multi) return Array.isArray(itemsModel.value) ? itemsModel.value : []
    return imageHashModel.value ? [{imageHash: imageHashModel.value}] : []
})

function newItemFromFile(f: PageFile): GalleryItem | null {
    if (!f.contentHash) return null
    return {imageHash: f.contentHash, altText: f.defaultAltText ?? '', subtext: f.defaultDescription ?? ''}
}

function pick(p: {file: PageFile}) {
    if (!p.file.contentHash) return
    if (props.multi) {
        const item = newItemFromFile(p.file)
        if (item) itemsModel.value = [...items.value, item]
    } else {
        imageHashModel.value = p.file.contentHash
    }
}

function pickMany(payloads: Array<{file: PageFile}>) {
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

function moveAt(i: number, delta: number) {
    if (!props.multi) return
    const next = [...items.value]
    const target = i + delta
    if (target < 0 || target >= next.length) return
    const [moved] = next.splice(i, 1)
    next.splice(target, 0, moved)
    itemsModel.value = next
}

function updateField(i: number, field: 'altText' | 'subtext', value: string) {
    if (!props.multi) return
    itemsModel.value = items.value.map((it, idx) => idx === i ? {...it, [field]: value} : it)
}

function swapAt(i: number, payload: {file: PageFile}) {
    if (!payload.file.contentHash) return
    if (props.multi) {
        itemsModel.value = items.value.map(
            (it, idx) => idx === i ? {...it, imageHash: payload.file.contentHash!} : it)
    } else {
        imageHashModel.value = payload.file.contentHash
    }
}

function onDragStart(i: number, ev: DragEvent) {
    dragIndex.value = i
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move'
}

function onDragOver(ev: DragEvent) {
    ev.preventDefault()
    if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move'
}

function onDrop(target: number) {
    if (dragIndex.value === null || dragIndex.value === target) return
    const next = [...items.value]
    const [moved] = next.splice(dragIndex.value, 1)
    next.splice(target, 0, moved)
    itemsModel.value = next
    dragIndex.value = null
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
            <CellImagePickerMultiItem
                v-for="(item, i) in items" :key="item.imageHash + '-' + i"
                :item="item"
                :index="i"
                :is-first="i === 0"
                :is-last="i === items.length - 1"
                :station-uid="stationUid"
                @move-up="moveAt(i, -1)"
                @move-down="moveAt(i, 1)"
                @remove="removeAt(i)"
                @update-field="(field, value) => updateField(i, field, value)"
                @swap-image="swapAt(i, $event)"
                @drag-start="onDragStart(i, $event)"
                @drag-over="onDragOver"
                @drop="onDrop(i)"
            />
        </div>

        <!-- Single-mode: one image preview -->
        <div v-if="!multi && items.length > 0" class="flex items-start gap-2">
            <img :src="pageImageUrl(stationUid, items[0].imageHash)" alt=""
                 class="w-32 h-32 object-cover rounded-theme border border-(--border)"/>
            <div class="flex flex-col gap-1">
                <PageFileBrowseButton
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
            <PageFileBrowseButton
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
