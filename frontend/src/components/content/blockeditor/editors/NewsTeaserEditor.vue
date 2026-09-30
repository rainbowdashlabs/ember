/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NewsSearchPicker from '@/components/input/search/NewsSearchPicker.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorStationProps} from '../cellTypes'

const props = defineProps<CellEditorStationProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('newsTitle') }}</FieldLabel>
    <NewsSearchPicker
        :model-value="(config.newsUid as string) ?? null"
        :station-uid="stationUid"
        @pick="(item: {publicUid: string}) => patch({newsUid: item.publicUid})"
        @update:model-value="(v: string | null | undefined) => patch({newsUid: v ?? null})"
    />
</template>
