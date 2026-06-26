/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
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
    <FieldLabel hint class="mb-1">{{ TS('codeLanguage') }}</FieldLabel>
    <TextInput :model-value="(config.language as string) ?? ''" placeholder="java, ts, …" @update:model-value="patch({language: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('codeContent') }}</FieldLabel>
    <TextAreaInput :model-value="content" rows="8" @update:model-value="emit('update:content', $event ?? '')"/>
</template>
