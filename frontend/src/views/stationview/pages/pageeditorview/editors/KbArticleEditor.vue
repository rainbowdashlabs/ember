/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import WikiSearchPicker from '@/components/input/search/WikiSearchPicker.vue'

const props = defineProps<{
    config: Record<string, unknown>
    stationUid: string
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('chooseKbArticle') }}</FieldLabel>
    <WikiSearchPicker
        :model-value="config.articleId != null ? String(config.articleId) : null"
        :station-uid="stationUid"
        @pick="(file: {id: number; name: string}) => patch({articleId: file.id, fallbackTitle: file.name})"
        @update:model-value="(v: string | null) => patch({articleId: v != null ? Number(v) : null})"
    />
</template>
