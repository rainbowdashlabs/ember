/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FormPickerInput from '@/components/input/search/FormPickerInput.vue'
import {FormPurpose} from '@/api/types'
import {useConfigPatch} from '@/composables/useConfigPatch'
import type {CellEditorEmits, CellEditorProps} from '../cellTypes'

const props = defineProps<CellEditorProps>()
const emit = defineEmits<CellEditorEmits>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const patch = useConfigPatch(() => props.config, emit)
</script>

<template>
    <FieldLabel hint class="mb-1">{{ TS('pollPicker') }}</FieldLabel>
    <FormPickerInput :model-value="(config.formPublicUid as string) ?? null" :purpose="FormPurpose.POLL" @update:model-value="patch({formPublicUid: $event})"/>
    <FieldLabel inline class="mt-2">
        <ToggleInput :model-value="config.showResultsAfterVote !== false" @update:model-value="patch({showResultsAfterVote: $event})"/>
        {{ TS('pollShowResultsAfterVote') }}
    </FieldLabel>
</template>
