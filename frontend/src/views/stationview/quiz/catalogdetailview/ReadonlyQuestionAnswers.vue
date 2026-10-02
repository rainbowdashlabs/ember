/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { isQuizQuestionOf } from '@/api/quiz'
import { QuizQuestionType, type QuizQuestion } from '@/api/generated/schema'

const props = defineProps<{
  question: QuizQuestion
}>()

const { t } = useI18n()

const choice = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.MULTIPLE_CHOICE) ? props.question.config : null)
const trueFalse = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.TRUE_FALSE) ? props.question.config : null)
const fillBlank = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.FILL_IN_THE_BLANK) ? props.question.config : null)
const freeAnswer = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.FREE_ANSWER) ? props.question.config : null)
const connect = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.CONNECT) ? props.question.config : null)
const ordering = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.ORDERING) ? props.question.config : null)
const enumeration = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.ENUMERATION) ? props.question.config : null)
const imageText = computed(() => isQuizQuestionOf(props.question, QuizQuestionType.IMAGE_TEXT) ? props.question.config : null)
</script>

<template>
  <div class="pl-4 text-sm space-y-1">
    <template v-if="choice?.options">
      <div v-for="(opt, i) in choice.options" :key="i" class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', opt.correct ? 'circle-check' : 'circle']" :class="opt.correct ? 'text-success' : 'text-(--text-muted)'" class="w-3 h-3" />
        <span>{{ opt.text }}</span>
      </div>
    </template>
    <template v-else-if="trueFalse">
      <span class="text-(--text-muted)">{{ trueFalse.correctAnswer ? t('common.yes') : t('common.no') }}</span>
    </template>
    <template v-else-if="fillBlank">
      <p v-if="fillBlank.text" class="text-sm whitespace-pre-wrap text-(--text-muted)">{{ fillBlank.text }}</p>
      <div v-if="fillBlank.answers" class="flex flex-wrap gap-1">
        <span v-for="(a, i) in fillBlank.answers" :key="i" class="inline-block bg-success/10 text-success rounded px-2 py-0.5 text-xs">{{ a }}</span>
      </div>
    </template>
    <template v-else-if="freeAnswer?.answers">
      <span v-for="(a, i) in freeAnswer.answers" :key="i" class="inline-block bg-success/10 text-success rounded px-2 py-0.5 mr-1 text-xs">{{ a }}</span>
    </template>
    <template v-else-if="connect?.pairs">
      <div v-for="(p, i) in connect.pairs" :key="i" class="flex items-center gap-2">
        <span>{{ p.left }}</span>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="w-3 h-3 text-(--text-muted)" />
        <span>{{ p.right }}</span>
      </div>
    </template>
    <template v-else-if="ordering?.items">
      <div v-for="(item, i) in ordering.items" :key="i" class="flex items-center gap-2">
        <span class="text-xs text-(--text-muted) font-mono w-5">{{ i + 1 }}.</span>
        <span>{{ item }}</span>
      </div>
    </template>
    <template v-else-if="enumeration?.answers">
      <span v-for="(a, i) in enumeration.answers" :key="i" class="inline-block bg-primary/10 text-primary rounded px-2 py-0.5 mr-1 text-xs">{{ a }}</span>
    </template>
    <template v-else-if="imageText?.answer">
      <span class="text-(--text-muted)">{{ imageText.answer }}</span>
    </template>
  </div>
</template>
