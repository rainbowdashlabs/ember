/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import PageFileBrowseButton from './PageFileBrowseButton.vue'
import CellImagePreview from './CellImagePreview.vue'
import {
    ImageFit,
    pageImageUrl,
    type ImageConfig,
    type PageFile,
} from '@/api/pageManage'

const props = defineProps<{
    content: string
    config: Record<string, unknown>
    pageId: number
    stationUid: string
}>()

const emit = defineEmits<{
    'update:content': [value: string]
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const settingsOpen = ref(false)

function onPick(p: {file: PageFile; url: string}) {
    emit('update:content', p.file.contentHash ?? '')
}

const imageConfig = computed<ImageConfig>(() => (props.config as ImageConfig) ?? {})

const imageUrl = computed(() => {
    if (!props.content) return ''
    return pageImageUrl(props.stationUid, props.content)
})

function updateConfig(patch: Record<string, unknown>) {
    emit('update:config', {...props.config, ...patch})
}
</script>

<template>
    <div class="space-y-2 overflow-hidden">
        <div v-if="imageUrl" class="space-y-1">
            <CellImagePreview
                :src="imageUrl"
                :alt="imageConfig.altText ?? ''"
                :config="imageConfig"
            />
            <p v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
                {{ imageConfig.description }}
            </p>
        </div>
        <div class="flex items-center gap-2">
            <PageFileBrowseButton
                :station-uid="stationUid"
                mime-prefix="image/"
                @pick="onPick"
            />
            <IconButton
                v-if="imageUrl"
                :icon="['fas', 'sliders']"
                :label="t('stationPages.editor.imageSettings')"
                class="text-(--text-muted) hover:text-(--text)"
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
                />
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <!-- Display -->
                <div class="space-y-3 sm:col-span-2">
                    <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionDisplay') }}</p>
                    <div>
                        <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageFit') }}</FieldLabel>
                        <SelectInput
                            :model-value="imageConfig.imageFit ?? ImageFit.CONTAIN"
                            @update:model-value="updateConfig({imageFit: $event})"
                        >
                            <option :value="ImageFit.COVER">{{ t('stationPages.imageFit.cover') }}</option>
                            <option :value="ImageFit.CONTAIN">{{ t('stationPages.imageFit.contain') }}</option>
                            <option :value="ImageFit.FILL">{{ t('stationPages.imageFit.fill') }}</option>
                        </SelectInput>
                    </div>
                    <div>
                        <FieldLabel hint class="mb-1">{{ t('stationPages.editor.maxHeight') }}</FieldLabel>
                        <NumberInput
                            :model-value="imageConfig.maxHeight ?? undefined"
                            :placeholder="t('stationPages.editor.maxHeightPlaceholder')"
                            @update:model-value="updateConfig({maxHeight: $event || null})"
                        />
                    </div>
                </div>

                <!-- Crop -->
                <div class="space-y-3 sm:col-span-2">
                    <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionCrop') }}</p>
                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.cropTop') }}</FieldLabel>
                            <NumberInput
                                :model-value="imageConfig.cropTop ?? 0"
                                :min="0" :max="90"
                                @update:model-value="updateConfig({cropTop: $event || null})"
                            />
                        </div>
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.cropRight') }}</FieldLabel>
                            <NumberInput
                                :model-value="imageConfig.cropRight ?? 0"
                                :min="0" :max="90"
                                @update:model-value="updateConfig({cropRight: $event || null})"
                            />
                        </div>
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.cropBottom') }}</FieldLabel>
                            <NumberInput
                                :model-value="imageConfig.cropBottom ?? 0"
                                :min="0" :max="90"
                                @update:model-value="updateConfig({cropBottom: $event || null})"
                            />
                        </div>
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.cropLeft') }}</FieldLabel>
                            <NumberInput
                                :model-value="imageConfig.cropLeft ?? 0"
                                :min="0" :max="90"
                                @update:model-value="updateConfig({cropLeft: $event || null})"
                            />
                        </div>
                    </div>
                </div>

                <!-- Style -->
                <div class="space-y-3 sm:col-span-2">
                    <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionStyle') }}</p>
                    <div>
                        <FieldLabel hint class="mb-1">{{ t('stationPages.editor.borderRadiusPercent') }}</FieldLabel>
                        <NumberInput
                            :model-value="imageConfig.borderRadiusPercent ?? 0"
                            :min="0" :max="50"
                            @update:model-value="updateConfig({borderRadiusPercent: $event || null})"
                        />
                    </div>
                    <div class="grid grid-cols-2 gap-3">
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.borderWidthPx') }}</FieldLabel>
                            <NumberInput
                                :model-value="imageConfig.borderWidthPx ?? 0"
                                :min="0" :max="20"
                                @update:model-value="updateConfig({borderWidthPx: $event || null})"
                            />
                        </div>
                        <div>
                            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.borderColor') }}</FieldLabel>
                            <input
                                type="color"
                                :value="imageConfig.borderColor ?? '#000000'"
                                class="h-10 w-full rounded-theme border border-(--border) bg-(--bg) cursor-pointer"
                                @input="updateConfig({borderColor: ($event.target as HTMLInputElement).value})"
                            />
                        </div>
                    </div>
                </div>

                <!-- Text -->
                <div class="space-y-3 sm:col-span-2">
                    <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionText') }}</p>
                    <div>
                        <FieldLabel hint class="mb-1">{{ t('stationPages.editor.altText') }}</FieldLabel>
                        <TextInput
                            :model-value="imageConfig.altText ?? ''"
                            :placeholder="t('stationPages.editor.altTextPlaceholder')"
                            @update:model-value="updateConfig({altText: $event})"
                        />
                    </div>
                    <div>
                        <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageDescription') }}</FieldLabel>
                        <TextInput
                            :model-value="imageConfig.description ?? ''"
                            :placeholder="t('stationPages.editor.imageDescriptionPlaceholder')"
                            @update:model-value="updateConfig({description: $event})"
                        />
                    </div>
                </div>
            </div>
        </Modal>
    </div>
</template>
