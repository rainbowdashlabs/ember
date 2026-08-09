/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type { QuizQuestion } from '@/api/quiz'

defineProps<{
  question: QuizQuestion
  selected: boolean
  categoryName: string
}>()

const emit = defineEmits<{
  toggleSelect: []
  edit: []
  delete: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center gap-2 cursor-pointer" @click.stop>
      <CheckboxInput :model-value="selected" @update:model-value="emit('toggleSelect')"/>
      <div class="flex-1" @click="emit('edit')">
        <div class="flex items-center gap-1.5 flex-wrap mb-0.5">
          <InfoBadge>{{ t(`quiz.questionTypes.${question.quizQuestionType}`) }}</InfoBadge>
          <SecondaryBadge>{{ categoryName }}</SecondaryBadge>
          <span class="text-xs text-(--text-muted)">{{ question.points }} {{ t('quiz.questions.points') }}</span>
        </div>
        <span class="font-medium">{{ question.title }}</span>
      </div>
    </div>
    <div class="flex items-center justify-end gap-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
      <SecondaryButton :icon="['fas', 'pen']" @click="emit('edit')">
        {{ t('common.edit') }}
      </SecondaryButton>
      <DeleteButton @click="emit('delete')" />
    </div>
  </div>
</template>
