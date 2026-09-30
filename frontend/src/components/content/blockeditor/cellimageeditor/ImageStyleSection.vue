/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import LabeledNumberInput from './LabeledNumberInput.vue'
import ColorPickerInput from '@/components/input/ColorPickerInput.vue'
import type {ImageConfig} from '@/api/pageManage'

defineProps<{
    config: ImageConfig
}>()

defineEmits<{
    update: [patch: Record<string, unknown>]
}>()

const {t} = useI18n()
const borderColorId = useId()
</script>

<template>
    <div class="space-y-3 sm:col-span-2">
        <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionStyle') }}</p>
        <LabeledNumberInput
            :label="t('stationPages.editor.borderRadiusPercent')"
            :model-value="config.borderRadiusPercent ?? 0"
            :min="0"
            :max="50"
            @update:model-value="$emit('update', {borderRadiusPercent: $event})"
        />
        <div class="grid grid-cols-2 gap-3">
            <LabeledNumberInput
                :label="t('stationPages.editor.borderWidthPx')"
                :model-value="config.borderWidthPx ?? 0"
                :min="0"
                :max="20"
                @update:model-value="$emit('update', {borderWidthPx: $event})"
            />
            <div>
                <FieldLabel :for="borderColorId" hint class="mb-1">{{ t('stationPages.editor.borderColor') }}</FieldLabel>
                <ColorPickerInput
                    :id="borderColorId"
                    :model-value="config.borderColor ?? '#000000'"
                    class="h-10 w-full rounded-theme border border-(--border) bg-(--bg) cursor-pointer"
                    @update:model-value="value => $emit('update', {borderColor: value})"
                />
            </div>
        </div>
    </div>
</template>
