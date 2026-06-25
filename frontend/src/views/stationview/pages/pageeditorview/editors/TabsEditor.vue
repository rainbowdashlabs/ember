/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CellListItemsEditor, {type ItemFieldDef} from '../CellListItemsEditor.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)

const TAB_FIELDS: ItemFieldDef[] = [
    {key: 'title', label: TS('tabTitle'), type: 'text'},
    {key: 'body', label: TS('tabBody'), type: 'markdown', span: 2},
]
</script>

<template>
    <CellListItemsEditor
        :items="config.items as Record<string, unknown>[]"
        :fields="TAB_FIELDS"
        :add-label="TS('addTab')"
        @update:items="patch({items: $event})"
    />
</template>
