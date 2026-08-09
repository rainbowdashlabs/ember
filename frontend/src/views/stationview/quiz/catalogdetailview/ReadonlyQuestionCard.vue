/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ReadonlyQuestionAnswers from './ReadonlyQuestionAnswers.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type { QuizQuestion, QuizCategory } from '@/api/quiz'

const props = defineProps<{
  question: QuizQuestion
  categories: QuizCategory[]
}>()

const { t } = useI18n()

const categoryName = computed(() => {
  if (!props.question.categoryId) return ''
  return props.categories.find(c => c.id === props.question.categoryId)?.name ?? ''
})
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between gap-4">
      <div class="flex-1 min-w-0 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ question.title }}</span>
          <InfoBadge>{{ t(`quiz.questionTypes.${question.quizQuestionType}`) }}</InfoBadge>
          <SecondaryBadge v-if="question.categoryId">{{ categoryName }}</SecondaryBadge>
        </div>
        <p v-if="question.description" class="text-xs text-(--text-muted)">{{ question.description }}</p>
      </div>
      <MutedText size="sm" class="shrink-0">{{ question.points }} {{ t('quiz.questions.points') }}</MutedText>
    </div>
    <ReadonlyQuestionAnswers :question="question" />
  </NeutralContainer>
</template>
