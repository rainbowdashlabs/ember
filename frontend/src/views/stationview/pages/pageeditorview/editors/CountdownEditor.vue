/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

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
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('countdownTarget') }}</FieldLabel>
    <DateTimeInput :model-value="(config.targetDate as string) ?? ''" @update:model-value="patch({targetDate: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('countdownLabel') }}</FieldLabel>
    <TextInput :model-value="(config.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('countdownSublabel') }}</FieldLabel>
    <TextInput :model-value="(config.sublabel as string) ?? ''" @update:model-value="patch({sublabel: $event})"/>
</template>
