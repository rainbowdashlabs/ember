/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type { QuizQuestion, QuizCategory } from '@/api/types'

defineProps<{
  questions: QuizQuestion[]
  categories: QuizCategory[]
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="questions.length > 0" class="space-y-3">
    <SectionHeader>{{ t('quiz.questions.title') }}</SectionHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="q in questions" :key="q.id" class="space-y-2">
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1 min-w-0 space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">{{ q.title }}</span>
              <InfoBadge>{{ t(`quiz.questionTypes.${q.quizQuestionType}`) }}</InfoBadge>
              <SecondaryBadge v-if="q.categoryId">{{ categories.find(c => c.id === q.categoryId)?.name ?? '' }}</SecondaryBadge>
            </div>
            <p v-if="q.description" class="text-xs text-(--text-muted)">{{ q.description }}</p>
          </div>
          <span class="text-sm text-(--text-muted) shrink-0">{{ q.points }} {{ t('quiz.questions.points') }}</span>
        </div>
        <div class="pl-4 text-sm space-y-1">
          <template v-if="q.quizQuestionType === 'MULTIPLE_CHOICE' && Array.isArray(q.config?.options)">
            <div v-for="(opt, i) in (q.config.options as {text: string; correct?: boolean}[])" :key="i" class="flex items-center gap-2">
              <font-awesome-icon :icon="['fas', opt.correct ? 'circle-check' : 'circle']" :class="opt.correct ? 'text-success' : 'text-(--text-muted)'" class="w-3 h-3" />
              <span>{{ opt.text }}</span>
            </div>
          </template>
          <template v-else-if="q.quizQuestionType === 'TRUE_FALSE'">
            <span class="text-(--text-muted)">{{ q.config?.correctAnswer ? t('common.yes') : t('common.no') }}</span>
          </template>
          <template v-else-if="q.quizQuestionType === 'FILL_IN_THE_BLANK'">
            <p v-if="q.config?.text" class="text-sm whitespace-pre-wrap text-(--text-muted)">{{ q.config.text }}</p>
            <div v-if="Array.isArray(q.config?.answers)" class="flex flex-wrap gap-1">
              <span v-for="(a, i) in (q.config.answers as string[])" :key="i" class="inline-block bg-success/10 text-success rounded px-2 py-0.5 text-xs">{{ a }}</span>
            </div>
          </template>
          <template v-else-if="q.quizQuestionType === 'FREE_ANSWER' && q.config?.expectedAnswer">
            <span class="text-(--text-muted)">{{ q.config.expectedAnswer }}</span>
          </template>
          <template v-else-if="q.quizQuestionType === 'CONNECT' && Array.isArray(q.config?.pairs)">
            <div v-for="(p, i) in (q.config.pairs as {left: string; right: string}[])" :key="i" class="flex items-center gap-2">
              <span>{{ p.left }}</span>
              <font-awesome-icon :icon="['fas', 'arrow-right']" class="w-3 h-3 text-(--text-muted)" />
              <span>{{ p.right }}</span>
            </div>
          </template>
          <template v-else-if="q.quizQuestionType === 'ORDERING' && Array.isArray(q.config?.items)">
            <div v-for="(item, i) in (q.config.items as string[])" :key="i" class="flex items-center gap-2">
              <span class="text-xs text-(--text-muted) font-mono w-5">{{ i + 1 }}.</span>
              <span>{{ item }}</span>
            </div>
          </template>
          <template v-else-if="q.quizQuestionType === 'ENUMERATION' && Array.isArray(q.config?.answers)">
            <span v-for="(a, i) in (q.config.answers as string[])" :key="i" class="inline-block bg-primary/10 text-primary rounded px-2 py-0.5 mr-1 text-xs">{{ a }}</span>
          </template>
          <template v-else-if="q.quizQuestionType === 'IMAGE_TEXT' && q.config?.text">
            <span class="text-(--text-muted)">{{ q.config.text }}</span>
          </template>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
