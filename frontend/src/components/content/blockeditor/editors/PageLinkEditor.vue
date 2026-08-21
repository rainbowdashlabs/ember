/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageSearchPicker from '@/components/input/search/PageSearchPicker.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorStationProps} from '../cellTypes'

const props = defineProps<CellEditorStationProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('pageId') }}</FieldLabel>
    <PageSearchPicker
        :model-value="(config.pageUid as string) ?? null"
        :station-uid="stationUid"
        @pick="(item: {pageUid: string}) => patch({pageUid: item.pageUid})"
        @update:model-value="(v: string | null | undefined) => patch({pageUid: v ?? null})"
    />
</template>
