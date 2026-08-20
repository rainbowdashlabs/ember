/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import CellImagePreview from './CellImagePreview.vue'
import ImageDisplaySection from './cellimageeditor/ImageDisplaySection.vue'
import ImageCropSection from './cellimageeditor/ImageCropSection.vue'
import ImageStyleSection from './cellimageeditor/ImageStyleSection.vue'
import ImageTextSection from './cellimageeditor/ImageTextSection.vue'
import {type ImageConfig} from '@/api/pageManage'
import {mediaFileUrl, type StationFile} from '@/api/media'

const content = defineModel<string>('content', {required: true})
const config = defineModel<Record<string, unknown>>('config', {required: true})

const props = defineProps<{
    pageId: number
    stationUid: string
}>()

const {t} = useI18n()
const settingsOpen = ref(false)

function onPick(p: {file: StationFile; url: string}) {
    content.value = p.file.contentHash ?? ''
}

const imageConfig = computed<ImageConfig>(() => (config.value as ImageConfig) ?? {})

const imageUrl = computed(() => {
    if (!content.value) return ''
    return mediaFileUrl(props.stationUid, content.value)
})

function updateConfig(patch: Record<string, unknown>) {
    config.value = {...config.value, ...patch}
}
</script>

<template>
    <div class="space-y-2 overflow-hidden">
        <div v-if="imageUrl" class="space-y-1">
            <CellImagePreview
                :src="imageUrl"
                :alt="imageConfig.altText ?? ''"
                :config="imageConfig"
                :station-uid="stationUid"
                :content-hash="content"
                :width-hint="256"
            />
            <p v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
                {{ imageConfig.description }}
            </p>
        </div>
        <div class="flex items-center gap-2">
            <MediaBrowseButton
                :station-uid="stationUid"
                mime-prefix="image/"
                @pick="onPick"
            />
            <MutedIconButton
                v-if="imageUrl"
                :icon="['fas', 'sliders']"
                :label="t('stationPages.editor.imageSettings')"
                hover="text"
                @click="settingsOpen = true"
            />
        </div>

        <Modal v-model="settingsOpen" size="lg">
            <SectionHeader class="mb-4">{{ t('stationPages.editor.imageSettings') }}</SectionHeader>
            <div v-if="imageUrl" class="mb-4">
                <p class="text-xs uppercase tracking-wider text-(--text-muted) mb-1">{{ t('stationPages.editor.cropPreview') }}</p>
                <CellImagePreview
                    :src="imageUrl"
                    :alt="imageConfig.altText ?? ''"
                    :config="imageConfig"
                    :station-uid="stationUid"
                    :content-hash="content"
                    :width-hint="512"
                />
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <ImageDisplaySection :config="imageConfig" @update="updateConfig" />
                <ImageCropSection :config="imageConfig" @update="updateConfig" />
                <ImageStyleSection :config="imageConfig" @update="updateConfig" />
                <ImageTextSection :config="imageConfig" @update="updateConfig" />
            </div>
        </Modal>
    </div>
</template>
