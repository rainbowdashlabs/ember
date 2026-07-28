/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/quiz'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
  points: number
  canReview: boolean
}>()

const emit = defineEmits<{
  (e: 'update-points', answerId: number, maxPoints: number, value: number | undefined): void
}>()

const {t} = useI18n()

function onUpdate(value: number | undefined) {
  if (!props.answer) return
  emit('update-points', props.answer.id, props.question.points, value)
}
</script>

<template>
  <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent flex-wrap">
    <label class="text-sm font-medium shrink-0">{{ t('quiz.evaluate.points') }}:</label>
    <div v-if="props.canReview" class="flex items-center gap-1">
      <DecimalInput
        :model-value="props.points"
        step="0.5"
        class="w-20"
        @update:model-value="onUpdate"
      />
      <MutedText size="sm" class="shrink-0">/ {{ props.question.points }}</MutedText>
    </div>
    <span v-else class="text-sm font-mono">{{ props.points }} / {{ props.question.points }}</span>
  </div>
</template>
