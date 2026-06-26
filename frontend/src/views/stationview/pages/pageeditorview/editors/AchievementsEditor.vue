/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CellListItemsEditor, {type ItemFieldDef} from '../CellListItemsEditor.vue'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'
import {useConfigPatch} from '@/composables/useConfigPatch'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)

const ACHIEVEMENT_FIELDS: ItemFieldDef[] = [
    {key: 'title', label: TS('achievementTitle'), type: 'text'},
    {key: 'year', label: TS('achievementYear'), type: 'text'},
    {key: 'description', label: TS('achievementDescription'), type: 'textarea', span: 2},
]
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('listTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <CellListItemsEditor
        :items="config.items as Record<string, unknown>[]"
        :fields="ACHIEVEMENT_FIELDS"
        :add-label="TS('addAchievement')"
        @update:items="patch({items: $event})"
    />
</template>
