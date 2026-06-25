/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import LabeledNumberInput from './LabeledNumberInput.vue'
import {ImageFit, type ImageConfig} from '@/api/pageManage'

defineProps<{
    config: ImageConfig
}>()

defineEmits<{
    update: [patch: Record<string, unknown>]
}>()

const {t} = useI18n()
</script>

<template>
    <div class="space-y-3 sm:col-span-2">
        <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionDisplay') }}</p>
        <div>
            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageFit') }}</FieldLabel>
            <SelectInput
                :model-value="config.imageFit ?? ImageFit.CONTAIN"
                @update:model-value="$emit('update', {imageFit: $event})"
            >
                <option :value="ImageFit.COVER">{{ t('stationPages.imageFit.cover') }}</option>
                <option :value="ImageFit.CONTAIN">{{ t('stationPages.imageFit.contain') }}</option>
                <option :value="ImageFit.FILL">{{ t('stationPages.imageFit.fill') }}</option>
            </SelectInput>
        </div>
        <LabeledNumberInput
            :label="t('stationPages.editor.maxHeight')"
            :model-value="config.maxHeight"
            :placeholder="t('stationPages.editor.maxHeightPlaceholder')"
            @update:model-value="$emit('update', {maxHeight: $event})"
        />
    </div>
</template>
