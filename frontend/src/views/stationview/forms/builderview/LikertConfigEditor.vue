/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FormOptionsEditor from './FormOptionsEditor.vue'
import type { FormOption } from '@/api/forms'
import { optionsOf } from '@/util/formOptions'
import type { QuestionDraft } from './types'

const props = defineProps<{
  question: QuestionDraft
}>()

const { t } = useI18n()

function updateStatements(items: FormOption[]) {
  props.question.config.statements = items
}
</script>

<template>
  <div class="flex gap-4 items-center">
    <label class="text-sm">{{ t('forms.likert.scaleMin') }}</label>
    <NumberInput v-model="(question.config.scaleMin as number)" class="w-20" />
    <label class="text-sm">{{ t('forms.likert.scaleMax') }}</label>
    <NumberInput v-model="(question.config.scaleMax as number)" class="w-20" />
  </div>
  <FormOptionsEditor :add-label="t('forms.likert.addStatement')" :label="t('forms.likert.statements')"
                     :model-value="optionsOf(question.config, 'statements')"
                     @update:model-value="updateStatements"/>
</template>
