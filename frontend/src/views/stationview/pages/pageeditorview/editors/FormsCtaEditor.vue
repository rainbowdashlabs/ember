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
import {FormPurpose} from '@/api/forms'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('formsCtaPicker') }}</FieldLabel>
    <FormPickerInput :model-value="(config.formPublicUid as string) ?? null" :purpose="FormPurpose.CONTACT" @update:model-value="patch({formPublicUid: $event})"/>
    <FieldLabel hint class="mb-1 mt-3">{{ TS('formsCtaHeadlineOverride') }}</FieldLabel>
    <TextInput :model-value="(config.headlineOverride as string) ?? ''" @update:model-value="patch({headlineOverride: $event})"/>
    <FieldLabel hint class="mb-1">{{ TS('formsCtaBodyOverride') }}</FieldLabel>
    <MarkdownFieldInput :model-value="(config.bodyOverride as string) ?? ''" @update:model-value="patch({bodyOverride: $event ?? ''})"/>
</template>
