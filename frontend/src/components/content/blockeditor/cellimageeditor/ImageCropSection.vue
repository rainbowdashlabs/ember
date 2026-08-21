/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CropFieldsGrid from './CropFieldsGrid.vue'
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
        <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionCrop') }}</p>
        <CropFieldsGrid :config="config" @update="$emit('update', $event)" />
    </div>
</template>
