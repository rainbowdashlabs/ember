/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import ReadonlyQuestionCard from './ReadonlyQuestionCard.vue'
import type { QuizQuestion, QuizCategory } from '@/api/types'

defineProps<{
  questions: QuizQuestion[]
  categories: QuizCategory[]
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="questions.length > 0" class="space-y-3">
    <SubHeader>{{ t('quiz.questions.title') }}</SubHeader>
    <div class="space-y-2">
      <ReadonlyQuestionCard v-for="q in questions" :key="q.id" :question="q" :categories="categories" />
    </div>
  </div>
</template>
