/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownFieldInput from '@/components/input/text/MarkdownFieldInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('blogSignupTitle') }}</FieldLabel>
    <TextInput :model-value="(config.title as string) ?? ''" @update:model-value="patch({title: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('blogSignupDescription') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.description as string) ?? ''" @update:model-value="patch({description: $event ?? ''})"/>
    <p class="text-xs text-(--text-muted) italic">{{ TS('blogSignupFeedHint') }}</p>
</template>
