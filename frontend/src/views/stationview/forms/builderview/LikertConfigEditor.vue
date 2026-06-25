/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import OptionList from './OptionList.vue'
import type { QuestionDraft } from './types'

const props = defineProps<{
  question: QuestionDraft
}>()

const { t } = useI18n()

function updateStatements(items: string[]) {
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
  <OptionList :items="(question.config.statements as string[])" :label="t('forms.likert.statements')"
              :add-label="t('forms.likert.addStatement')" @update:items="updateStatements" />
</template>
