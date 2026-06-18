/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import CellListItemsEditor, {type ItemFieldDef} from '../CellListItemsEditor.vue'

const props = defineProps<{
    config: Record<string, unknown>
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}

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
