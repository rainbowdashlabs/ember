/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import FormOptionsEditor from './FormOptionsEditor.vue'
import { FormQuestionType, type Option } from '@/api/generated/schema'
import { optionsOf, type OptionField } from '@/util/formOptions'
import type { QuestionDraft } from './types'

/**
 * What makes a question the question it is, and so stays on its tile: the options of a choice or a
 * ranking, the statements of a Likert grid. The other kinds have no content beyond their title.
 */
const question = defineModel<QuestionDraft>('question', {required: true})

const { t } = useI18n()

/** Where this kind of question keeps its content and what its editor calls it, or null where it has none. */
const content = computed<{field: OptionField, label: string, addLabel: string} | null>(() => {
  switch (question.value.questionType) {
    case FormQuestionType.CHOICE:
      return {field: 'options', label: t('forms.choice.options'), addLabel: t('forms.choice.addOption')}
    case FormQuestionType.RANKING:
      return {field: 'options', label: t('forms.ranking.options'), addLabel: t('forms.ranking.addOption')}
    case FormQuestionType.LIKERT:
      return {field: 'statements', label: t('forms.likert.statements'), addLabel: t('forms.likert.addStatement')}
    default:
      return null
  }
})

function update(field: OptionField, items: Option[]) {
  question.value.config[field] = items
}
</script>

<template>
  <FormOptionsEditor v-if="content" :label="content.label" :add-label="content.addLabel"
                     :model-value="optionsOf(question.config, content.field)"
                     @update:model-value="update(content.field, $event)"/>
</template>
