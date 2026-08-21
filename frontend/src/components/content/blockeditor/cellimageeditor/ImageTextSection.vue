/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
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
        <p class="text-xs uppercase tracking-wider text-(--text-muted)">{{ t('stationPages.editor.imageSectionText') }}</p>
        <div>
            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.altText') }}</FieldLabel>
            <TextInput
                :model-value="config.altText ?? ''"
                :placeholder="t('stationPages.editor.altTextPlaceholder')"
                @update:model-value="$emit('update', {altText: $event})"
            />
        </div>
        <div>
            <FieldLabel hint class="mb-1">{{ t('stationPages.editor.imageDescription') }}</FieldLabel>
            <TextInput
                :model-value="config.description ?? ''"
                :placeholder="t('stationPages.editor.imageDescriptionPlaceholder')"
                @update:model-value="$emit('update', {description: $event})"
            />
        </div>
    </div>
</template>
