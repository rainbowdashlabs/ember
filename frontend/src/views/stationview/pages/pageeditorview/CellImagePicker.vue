/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PageFileBrowseButton from './PageFileBrowseButton.vue'
import {pageImageUrl, type GalleryItem, type PageFile} from '@/api/pageManage'

const props = withDefaults(defineProps<{
    multi?: boolean
    items?: GalleryItem[] | null
    imageHash?: string | null
    pageId: number
    stationUid: string
}>(), {
    multi: false,
})

const emit = defineEmits<{
    'update:items': [value: GalleryItem[]]
    'update:imageHash': [value: string | null]
}>()

const {t} = useI18n()
const dragIndex = ref<number | null>(null)

const items = computed<GalleryItem[]>(() => {
    if (props.multi) return Array.isArray(props.items) ? props.items : []
    return props.imageHash ? [{imageHash: props.imageHash}] : []
})

function newItemFromFile(f: PageFile): GalleryItem | null {
    if (!f.contentHash) return null
    return {imageHash: f.contentHash, altText: f.defaultAltText ?? '', subtext: f.defaultDescription ?? ''}
}

function pick(p: {file: PageFile}) {
    if (!p.file.contentHash) return
    if (props.multi) {
        const item = newItemFromFile(p.file)
        if (item) emit('update:items', [...items.value, item])
    } else {
        emit('update:imageHash', p.file.contentHash)
    }
}

function pickMany(payloads: Array<{file: PageFile}>) {
    if (!props.multi) return
    const added: GalleryItem[] = []
    for (const p of payloads) {
        const item = newItemFromFile(p.file)
        if (item) added.push(item)
    }
    if (added.length > 0) emit('update:items', [...items.value, ...added])
}

function removeAt(i: number) {
    if (props.multi) emit('update:items', items.value.filter((_, idx) => idx !== i))
    else emit('update:imageHash', null)
}

function moveAt(i: number, delta: number) {
    if (!props.multi) return
    const next = [...items.value]
    const target = i + delta
    if (target < 0 || target >= next.length) return
    const [moved] = next.splice(i, 1)
    next.splice(target, 0, moved)
    emit('update:items', next)
}

function updateField(i: number, field: 'altText' | 'subtext', value: string) {
    if (!props.multi) return
    emit('update:items', items.value.map((it, idx) => idx === i ? {...it, [field]: value} : it))
}

function swapAt(i: number, payload: {file: PageFile}) {
    if (!payload.file.contentHash) return
    if (props.multi) {
        emit('update:items', items.value.map(
            (it, idx) => idx === i ? {...it, imageHash: payload.file.contentHash!} : it))
    } else {
        emit('update:imageHash', payload.file.contentHash)
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
    emit('update:items', next)
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
            <div
                v-for="(item, i) in items" :key="item.imageHash + '-' + i"
                draggable="true"
                class="flex items-stretch gap-2 rounded-theme border border-(--border) p-2 bg-bg-light dark:bg-bg-dark"
                @dragstart="onDragStart(i, $event)"
                @dragover="onDragOver"
                @drop="onDrop(i)"
            >
                <div class="flex flex-col items-center justify-center gap-1 shrink-0">
                    <IconButton
                        :icon="['fas', 'angle-up']" :label="t('common.moveUp')"
                        :disabled="i === 0" @click="moveAt(i, -1)"
                    />
                    <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-move"/>
                    <IconButton
                        :icon="['fas', 'angle-down']" :label="t('common.moveDown')"
                        :disabled="i === items.length - 1" @click="moveAt(i, 1)"
                    />
                </div>
                <img :src="pageImageUrl(stationUid, item.imageHash)" alt=""
                     class="w-24 h-24 object-cover rounded shrink-0"/>
                <div class="flex-1 flex flex-col gap-1 min-w-0">
                    <TextInput
                        :model-value="item.altText ?? ''"
                        :placeholder="t('stationPages.editor.altText')"
                        @update:model-value="updateField(i, 'altText', $event)"
                    />
                    <TextInput
                        :model-value="item.subtext ?? ''"
                        :placeholder="t('stationPages.editor.imageDescription')"
                        @update:model-value="updateField(i, 'subtext', $event)"
                    />
                </div>
                <PageFileBrowseButton
                    :station-uid="stationUid"
                    mime-prefix="image/"
                    :label="t('stationPages.editor.replaceImage')"
                    @pick="swapAt(i, $event)"
                />
                <IconButton
                    :icon="['fas', 'trash']" :label="t('common.delete')"
                    class="text-error" @click="removeAt(i)"
                />
            </div>
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
