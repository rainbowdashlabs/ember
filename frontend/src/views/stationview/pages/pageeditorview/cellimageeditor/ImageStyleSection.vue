/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import LabeledNumberInput from './LabeledNumberInput.vue'
import type {ImageConfig} from '@/api/pageManage'

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
                <FieldLabel hint class="mb-1">{{ t('stationPages.editor.borderColor') }}</FieldLabel>
                <input
                    type="color"
                    :value="config.borderColor ?? '#000000'"
                    class="h-10 w-full rounded-theme border border-(--border) bg-(--bg) cursor-pointer"
                    @input="$emit('update', {borderColor: ($event.target as HTMLInputElement).value})"
                />
            </div>
        </div>
    </div>
</template>
