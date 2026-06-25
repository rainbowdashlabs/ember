/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CellListItemsEditor, {type ItemFieldDef} from '../CellListItemsEditor.vue'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'
import {useConfigPatch} from '@/composables/useConfigPatch'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)

const STAT_FIELDS: ItemFieldDef[] = [
    {key: 'label', label: TS('statLabel'), type: 'text'},
    {key: 'value', label: TS('statValue'), type: 'text'},
    {key: 'suffix', label: TS('statSuffix'), type: 'text'},
]
</script>

<template>
    <CellListItemsEditor
        :items="config.items as Record<string, unknown>[]"
        :fields="STAT_FIELDS"
        :add-label="TS('addStat')"
        :grid-cols="3"
        @update:items="patch({items: $event})"
    />
</template>
