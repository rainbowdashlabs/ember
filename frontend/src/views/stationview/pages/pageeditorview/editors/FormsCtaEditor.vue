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
import FormPickerInput from '@/components/input/search/FormPickerInput.vue'
import {FormPurpose} from '@/api/types'

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
    <FieldLabel hint class="mb-1">{{ TS('formsCtaPicker') }}</FieldLabel>
    <FormPickerInput :model-value="(config.formPublicUid as string) ?? null" :purpose="FormPurpose.CONTACT" @update:model-value="patch({formPublicUid: $event})"/>
    <FieldLabel hint class="mb-1 mt-3">{{ TS('formsCtaHeadlineOverride') }}</FieldLabel>
    <TextInput :model-value="(config.headlineOverride as string) ?? ''" @update:model-value="patch({headlineOverride: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('formsCtaBodyOverride') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.bodyOverride as string) ?? ''" @update:model-value="patch({bodyOverride: $event ?? ''})"/>
</template>
