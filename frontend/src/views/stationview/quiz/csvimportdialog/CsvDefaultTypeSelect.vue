/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'

const modelValue = defineModel<QuizQuestionTypeName>({required: true})

const { t } = useI18n()
</script>

<template>
  <div>
    <FieldLabel hint class="mb-1">{{ t('quiz.questions.type') }} ({{ t('quiz.csv.defaultType') }})</FieldLabel>
    <SelectInput :model-value="modelValue" class="w-full" @update:model-value="modelValue = ($event ?? modelValue) as QuizQuestionTypeName">
      <option :value="QuizQuestionTypes.MULTIPLE_CHOICE">{{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</option>
      <option :value="QuizQuestionTypes.FREE_ANSWER">{{ t('quiz.questionTypes.FREE_ANSWER') }}</option>
      <option :value="QuizQuestionTypes.TRUE_FALSE">{{ t('quiz.questionTypes.TRUE_FALSE') }}</option>
      <option :value="QuizQuestionTypes.FILL_IN_THE_BLANK">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</option>
      <option :value="QuizQuestionTypes.ORDERING">{{ t('quiz.questionTypes.ORDERING') }}</option>
      <option :value="QuizQuestionTypes.CONNECT">{{ t('quiz.questionTypes.CONNECT') }}</option>
    </SelectInput>
  </div>
</template>
