/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import {useBlockEditorOptions} from '@/composables/useBlockEditorOptions'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

/**
 * A divider's settings: its label, and where the editor offers it, whether the line runs vertically
 * between the blocks either side of it. A vertical line has no label.
 */
const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
const options = useBlockEditorOptions()
const vertical = computed(() => props.config.vertical === true)
</script>

<template>
    <div v-if="options.verticalDivider" class="flex items-end gap-2 pt-1">
        <ToggleInput :model-value="vertical" data-testid="divider-vertical"
                     @update:model-value="patch({vertical: $event || null})"/>
        <FieldLabel hint class="mb-0">{{ TS('dividerVertical') }}</FieldLabel>
    </div>
    <template v-if="!vertical">
        <FieldLabel hint class="mb-1">{{ TS('dividerLabel') }}</FieldLabel>
        <TextInput :model-value="(config.label as string) ?? ''" :placeholder="TS('dividerLabelPlaceholder')" @update:model-value="patch({label: $event})"/>
    </template>
</template>
