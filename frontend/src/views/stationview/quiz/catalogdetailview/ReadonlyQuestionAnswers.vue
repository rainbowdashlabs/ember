/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { QuizQuestion } from '@/api/types'

defineProps<{
  question: QuizQuestion
}>()

const { t } = useI18n()
</script>

<template>
  <div class="pl-4 text-sm space-y-1">
    <template v-if="question.quizQuestionType === 'MULTIPLE_CHOICE' && Array.isArray(question.config?.options)">
      <div v-for="(opt, i) in (question.config.options as {text: string; correct?: boolean}[])" :key="i" class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', opt.correct ? 'circle-check' : 'circle']" :class="opt.correct ? 'text-success' : 'text-(--text-muted)'" class="w-3 h-3" />
        <span>{{ opt.text }}</span>
      </div>
    </template>
    <template v-else-if="question.quizQuestionType === 'TRUE_FALSE'">
      <span class="text-(--text-muted)">{{ question.config?.correctAnswer ? t('common.yes') : t('common.no') }}</span>
    </template>
    <template v-else-if="question.quizQuestionType === 'FILL_IN_THE_BLANK'">
      <p v-if="question.config?.text" class="text-sm whitespace-pre-wrap text-(--text-muted)">{{ question.config.text }}</p>
      <div v-if="Array.isArray(question.config?.answers)" class="flex flex-wrap gap-1">
        <span v-for="(a, i) in (question.config.answers as string[])" :key="i" class="inline-block bg-success/10 text-success rounded px-2 py-0.5 text-xs">{{ a }}</span>
      </div>
    </template>
    <template v-else-if="question.quizQuestionType === 'FREE_ANSWER' && question.config?.expectedAnswer">
      <span class="text-(--text-muted)">{{ question.config.expectedAnswer }}</span>
    </template>
    <template v-else-if="question.quizQuestionType === 'CONNECT' && Array.isArray(question.config?.pairs)">
      <div v-for="(p, i) in (question.config.pairs as {left: string; right: string}[])" :key="i" class="flex items-center gap-2">
        <span>{{ p.left }}</span>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="w-3 h-3 text-(--text-muted)" />
        <span>{{ p.right }}</span>
      </div>
    </template>
    <template v-else-if="question.quizQuestionType === 'ORDERING' && Array.isArray(question.config?.items)">
      <div v-for="(item, i) in (question.config.items as string[])" :key="i" class="flex items-center gap-2">
        <span class="text-xs text-(--text-muted) font-mono w-5">{{ i + 1 }}.</span>
        <span>{{ item }}</span>
      </div>
    </template>
    <template v-else-if="question.quizQuestionType === 'ENUMERATION' && Array.isArray(question.config?.answers)">
      <span v-for="(a, i) in (question.config.answers as string[])" :key="i" class="inline-block bg-primary/10 text-primary rounded px-2 py-0.5 mr-1 text-xs">{{ a }}</span>
    </template>
    <template v-else-if="question.quizQuestionType === 'IMAGE_TEXT' && question.config?.text">
      <span class="text-(--text-muted)">{{ question.config.text }}</span>
    </template>
  </div>
</template>
