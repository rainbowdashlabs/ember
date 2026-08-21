/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import WikiSearchPicker from '@/components/input/search/WikiSearchPicker.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorStationProps} from '../cellTypes'

const props = defineProps<CellEditorStationProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('chooseKbArticle') }}</FieldLabel>
    <WikiSearchPicker
        :model-value="config.articleId != null ? String(config.articleId) : null"
        :station-uid="stationUid"
        @pick="(file: {id: number; name: string}) => patch({articleId: file.id, fallbackTitle: file.name})"
        @update:model-value="(v: string | null | undefined) => patch({articleId: v != null ? Number(v) : null})"
    />
</template>
