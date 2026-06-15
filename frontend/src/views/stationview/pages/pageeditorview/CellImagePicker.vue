/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import FileUploadField from '@/components/input/FileUploadField.vue'
import {pageImageUrl, uploadPageImage} from '@/api/pageManage'

const props = withDefaults(defineProps<{
    /** Multi-mode: list of image IDs. Single-mode: single image ID. */
    multi?: boolean
    imageIds?: number[] | null
    imageId?: number | null
    pageId: number
    stationUid: string
    maxSize?: number
}>(), {
    multi: false,
    maxSize: 5 * 1024 * 1024,
})

const emit = defineEmits<{
    'update:imageIds': [value: number[]]
    'update:imageId': [value: number | null]
}>()

const {t} = useI18n()
const uploading = ref(false)
const uploadError = ref<string | null>(null)

const ids = computed<number[]>(() => {
    if (props.multi) return Array.isArray(props.imageIds) ? props.imageIds : []
    return props.imageId != null ? [props.imageId] : []
})

async function handleSelect(file: File) {
    uploading.value = true
    uploadError.value = null
    try {
        const image = await uploadPageImage(props.pageId, file)
        if (props.multi) {
            emit('update:imageIds', [...ids.value, image.id])
        } else {
            emit('update:imageId', image.id)
        }
    } catch {
        uploadError.value = t('fileUpload.uploadFailed')
    } finally {
        uploading.value = false
    }
}

function removeAt(index: number) {
    if (props.multi) {
        emit('update:imageIds', ids.value.filter((_, i) => i !== index))
    } else {
        emit('update:imageId', null)
    }
}

function moveAt(index: number, delta: number) {
    if (!props.multi) return
    const next = [...ids.value]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    const [moved] = next.splice(index, 1)
    next.splice(target, 0, moved)
    emit('update:imageIds', next)
}
</script>

<template>
    <div class="space-y-2">
        <div v-if="ids.length > 0" class="flex flex-wrap gap-2">
            <div
                v-for="(id, i) in ids" :key="id + '-' + i"
                class="relative rounded-theme overflow-hidden border border-(--border) w-24 h-24 group"
            >
                <img :src="pageImageUrl(stationUid, id)" alt="" class="w-full h-full object-cover"/>
                <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1">
                    <IconButton v-if="multi" :icon="['fas', 'angle-up']" :label="t('common.moveUp')" class="text-white" :class="{'opacity-30 pointer-events-none': i === 0}" @click="moveAt(i, -1)"/>
                    <IconButton v-if="multi" :icon="['fas', 'angle-down']" :label="t('common.moveDown')" class="text-white" :class="{'opacity-30 pointer-events-none': i === ids.length - 1}" @click="moveAt(i, 1)"/>
                    <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" class="text-white" @click="removeAt(i)"/>
                </div>
            </div>
        </div>
        <FileUploadField
            v-if="multi || ids.length === 0"
            accept="image/png,image/jpeg,image/webp"
            :max-size="maxSize"
            :disabled="uploading"
            :error="uploadError"
            :label="uploading ? t('common.loading') : (multi ? t('stationPages.editor.addImage') : t('stationPages.editor.uploadImage'))"
            @select="handleSelect"
        />
    </div>
</template>
