/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorContentEmits, CellEditorContentProps} from '../cellTypes'

const props = defineProps<CellEditorContentProps>()
const emit = defineEmits<CellEditorContentEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('accordionTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <label class="flex items-center gap-2 text-xs">
        <ToggleInput :model-value="!!config.openByDefault" @update:model-value="patch({openByDefault: $event})"/>
        {{ TS('accordionOpenByDefault') }}
    </label>
    <FieldLabel hint class="mb-1">{{ TS('accordionBody') }}</FieldLabel>
    <MarkdownFieldInput :model-value="content" @update:model-value="emit('update:content', $event ?? '')"/>
</template>
