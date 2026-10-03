/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'

/** The template a run generates, from the templates in use. */
const templateId = defineModel<number | null>({required: true})

defineProps<{
  templates: DocumentTemplateSummary[]
  loading: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <MutedText v-if="!loading && templates.length === 0" size="sm" tag="p">
    {{ t('documentTemplates.noTemplates') }}
  </MutedText>
  <LabelledField v-else :label="t('documentTemplates.template')">
    <SelectInput :model-value="templateId" data-testid="bulk-template"
                 @update:model-value="value => templateId = value === null ? null : Number(value)">
      <option :value="null" disabled>{{ t('documentTemplates.chooseTemplate') }}</option>
      <option v-for="template in templates" :key="template.id" :value="template.id">{{ template.name }}</option>
    </SelectInput>
  </LabelledField>
</template>
