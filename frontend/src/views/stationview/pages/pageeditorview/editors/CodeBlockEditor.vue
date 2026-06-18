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

const props = defineProps<{
    config: Record<string, unknown>
    content: string
}>()

const emit = defineEmits<{
    'update:config': [value: Record<string, unknown>]
    'update:content': [value: string]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

function patch(partial: Record<string, unknown>) {
    emit('update:config', {...props.config, ...partial})
}
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('codeLanguage') }}</FieldLabel>
    <TextInput :model-value="(config.language as string) ?? ''" placeholder="java, ts, …" @update:model-value="patch({language: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('codeContent') }}</FieldLabel>
    <TextAreaInput :model-value="content" rows="8" @update:model-value="emit('update:content', $event ?? '')"/>
</template>
