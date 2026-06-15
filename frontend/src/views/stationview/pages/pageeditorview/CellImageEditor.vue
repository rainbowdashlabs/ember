/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import {
    ImageFit,
    uploadPageImage,
    pageImageUrl,
    type ImageConfig,
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
const uploading = ref(false)

const imageConfig = computed<ImageConfig>(() => (props.config as ImageConfig) ?? {})

const imageUrl = computed(() => {
    if (!props.content) return ''
    return pageImageUrl(props.stationUid, Number(props.content))
})

async function onImageUpload(file: File) {
    if (!props.pageId) return
    uploading.value = true
    try {
        const image = await uploadPageImage(props.pageId, file)
        emit('update:content', String(image.id))
    } finally {
        uploading.value = false
    }
}

function updateConfig(patch: Record<string, unknown>) {
    emit('update:config', {...props.config, ...patch})
}
</script>

<template>
    <div class="space-y-3 overflow-hidden">
        <div v-if="imageUrl" class="relative overflow-hidden space-y-1">
            <img
                :src="imageUrl"
                :alt="(imageConfig.altText as string) ?? ''"
                :title="(imageConfig.altText as string) ?? ''"
                class="max-h-48 w-full rounded-theme object-contain"
            />
            <p v-if="imageConfig.description" class="text-xs text-(--text-muted) italic text-center">
                {{ imageConfig.description }}
            </p>
        </div>
        <FileUploadButton accept="image/*" :disabled="uploading" @select="onImageUpload">
            {{ uploading ? t('common.loading') : t('stationPages.editor.uploadImage') }}
        </FileUploadButton>
        <div class="grid grid-cols-1 gap-3">
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageFit') }}</FieldLabel>
                <SelectInput
                    :model-value="(imageConfig.imageFit as string) ?? ImageFit.CONTAIN"
                    @update:model-value="updateConfig({imageFit: $event})"
                >
                    <option :value="ImageFit.COVER">{{ t('stationPages.imageFit.cover') }}</option>
                    <option :value="ImageFit.CONTAIN">{{ t('stationPages.imageFit.contain') }}</option>
                    <option :value="ImageFit.FILL">{{ t('stationPages.imageFit.fill') }}</option>
                </SelectInput>
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.altText') }}</FieldLabel>
                <TextInput
                    :model-value="(imageConfig.altText as string) ?? ''"
                    :placeholder="t('stationPages.editor.altTextPlaceholder')"
                    @update:model-value="updateConfig({altText: $event})"
                />
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageDescription') }}</FieldLabel>
                <TextInput
                    :model-value="(imageConfig.description as string) ?? ''"
                    :placeholder="t('stationPages.editor.imageDescriptionPlaceholder')"
                    @update:model-value="updateConfig({description: $event})"
                />
            </div>
            <div>
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.maxHeight') }}</FieldLabel>
                <NumberInput
                    :model-value="(imageConfig.maxHeight as number) ?? undefined"
                    :placeholder="t('stationPages.editor.maxHeightPlaceholder')"
                    @update:model-value="updateConfig({maxHeight: $event || null})"
                />
            </div>
        </div>
    </div>
</template>
