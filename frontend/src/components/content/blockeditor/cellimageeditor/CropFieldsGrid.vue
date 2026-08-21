/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
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
    <div class="grid grid-cols-2 gap-3">
        <LabeledNumberInput :label="t('stationPages.editor.cropTop')" :model-value="config.cropTop ?? 0" :min="0" :max="90" @update:model-value="$emit('update', {cropTop: $event})" />
        <LabeledNumberInput :label="t('stationPages.editor.cropRight')" :model-value="config.cropRight ?? 0" :min="0" :max="90" @update:model-value="$emit('update', {cropRight: $event})" />
        <LabeledNumberInput :label="t('stationPages.editor.cropBottom')" :model-value="config.cropBottom ?? 0" :min="0" :max="90" @update:model-value="$emit('update', {cropBottom: $event})" />
        <LabeledNumberInput :label="t('stationPages.editor.cropLeft')" :model-value="config.cropLeft ?? 0" :min="0" :max="90" @update:model-value="$emit('update', {cropLeft: $event})" />
    </div>
</template>
