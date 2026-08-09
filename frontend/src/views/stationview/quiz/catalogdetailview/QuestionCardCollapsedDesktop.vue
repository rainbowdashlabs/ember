/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
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
  <div class="flex items-center justify-between gap-4">
    <div class="flex items-center gap-2 shrink-0" @click.stop>
      <CheckboxInput :model-value="selected" @update:model-value="emit('toggleSelect')"/>
    </div>
    <div class="flex-1 min-w-0 space-y-0.5 cursor-pointer" @click="emit('edit')">
      <div class="flex items-center gap-1.5 flex-wrap">
        <InfoBadge>{{ t(`quiz.questionTypes.${question.quizQuestionType}`) }}</InfoBadge>
        <SecondaryBadge>{{ categoryName }}</SecondaryBadge>
        <MutedText>{{ question.points }} {{ t('quiz.questions.points') }}</MutedText>
      </div>
      <span class="font-medium">{{ question.title }}</span>
      <MutedText v-if="question.description" tag="p" class="truncate">{{ question.description }}</MutedText>
    </div>
    <div class="flex items-center gap-2 shrink-0" @click.stop>
      <MutedIconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="emit('edit')" />
      <DeleteButton @click="emit('delete')" />
    </div>
  </div>
</template>
