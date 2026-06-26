/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import type {QuizQuestion, QuizTestAnswer} from '@/api/types'
import {quiz} from '@/api'

const props = defineProps<{
  question: QuizQuestion
  answer: QuizTestAnswer | undefined
  index: number
  points: number
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="flex items-center gap-2 mb-1">
      <SectionLabel>
        {{ props.index + 1 }}.
        {{ t(`quiz.questionTypes.${props.question.quizQuestionType}`) }}
      </SectionLabel>
      <span class="text-xs text-(--text-muted)">
        ({{ props.question.points }} {{ t('quiz.points') }})
      </span>
      <SuccessBadge v-if="props.answer?.graded">
        {{ props.points }}P
      </SuccessBadge>
    </div>
    <p class="font-medium">{{ props.question.title }}</p>
    <AuthImage
      v-if="props.question.imageUrl"
      :src="quiz.questionImageUrl(props.question.id, 300)"
      class="max-h-48 rounded-lg object-contain mt-2"
      alt=""
    />
  </div>
</template>
