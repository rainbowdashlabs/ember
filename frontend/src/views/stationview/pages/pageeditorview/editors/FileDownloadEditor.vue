/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const props = defineProps<{
    config: Record<string, unknown>
    stationUid: string
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
    <FieldLabel hint class="mb-1">{{ TS('fileUrl') }}</FieldLabel>
    <LinkSearchInput :model-value="(config.url as string) ?? ''" :station-uid="stationUid" @update:model-value="patch({url: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('fileLabel') }}</FieldLabel>
    <TextInput :model-value="(config.label as string) ?? ''" @update:model-value="patch({label: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('fileDescription') }}</FieldLabel>
    <TextInput :model-value="(config.description as string) ?? ''" @update:model-value="patch({description: $event})"/>
</template>
