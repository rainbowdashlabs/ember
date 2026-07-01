/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import {pageImageUrl, type PageFile} from '@/api/pageManage'

const props = defineProps<{
    file: PageFile | null
    stationUid: string
}>()

const emit = defineEmits<{
    close: []
}>()

const {t} = useI18n()

const previewUrl = computed(() => {
    const f = props.file
    return f?.contentHash && props.stationUid ? pageImageUrl(props.stationUid, f.contentHash) : ''
})

const previewKind = computed<'image' | 'video' | 'audio' | 'pdf' | 'other'>(() => {
    const m = props.file?.mimeType ?? ''
    if (m.startsWith('image/')) return 'image'
    if (m.startsWith('video/')) return 'video'
    if (m.startsWith('audio/')) return 'audio'
    if (m === 'application/pdf') return 'pdf'
    return 'other'
})

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
    <Modal v-if="file" :model-value="!!file" size="xl"
           @update:model-value="(v: boolean) => { if (!v) emit('close') }">
        <div class="space-y-3">
            <SubHeader>{{ file.fileName }}</SubHeader>
            <div class="flex items-center justify-center bg-(--bg-accent) rounded-theme overflow-hidden max-h-[70vh]">
                <img v-if="previewKind === 'image'" :src="previewUrl"
                     :alt="file.defaultAltText ?? file.fileName"
                     class="max-w-full max-h-[70vh] object-contain"/>
                <video v-else-if="previewKind === 'video'" :src="previewUrl"
                       controls class="max-w-full max-h-[70vh]"/>
                <audio v-else-if="previewKind === 'audio'" :src="previewUrl"
                       controls class="w-full"/>
                <iframe v-else-if="previewKind === 'pdf'" :src="previewUrl"
                        class="w-full h-[70vh]"/>
                <div v-else class="flex flex-col items-center gap-2 p-8 text-(--text-muted)">
                    <font-awesome-icon :icon="['fas', 'file']" class="text-5xl"/>
                    <p class="text-sm">{{ file.mimeType ?? '—' }}</p>
                </div>
            </div>
            <div class="flex items-center justify-between text-xs text-(--text-muted)">
                <span>{{ formatSize(file.fileSize) }}</span>
                <a :href="previewUrl" target="_blank" rel="noopener noreferrer" class="hover:text-primary">
                    <font-awesome-icon :icon="['fas', 'expand']" class="mr-1"/>
                    {{ t('common.open') }}
                </a>
            </div>
        </div>
    </Modal>
</template>
