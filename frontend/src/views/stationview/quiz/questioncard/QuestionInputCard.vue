/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type { QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { useI18n } from 'vue-i18n'
import QuestionHeader from './questioninputcard/QuestionHeader.vue'
import MultipleChoiceInput from './questioninputcard/MultipleChoiceInput.vue'
import FillInBlankInput from './questioninputcard/FillInBlankInput.vue'
import OrderingInput from './questioninputcard/OrderingInput.vue'
import ConnectInput from './questioninputcard/ConnectInput.vue'

const tfAnswer = defineModel<boolean | null>('tfAnswer', {required: true})
const freeAnswer = defineModel<string>('freeAnswer', {required: true})

defineProps<{
  question: QuizQuestion
  config: Record<string, unknown>
  disabled: boolean
  mcSelections: Set<number>
  fillGaps: Record<string, string>
  orderItems: number[]
  connectPairs: Record<string, string>
  mcDisplayOrder?: number[]
  connectRightOrder?: number[]
  /**
   * When provided, the question image is rendered with a plain {@code <img>} pointing at this
   * URL instead of going through the authenticated image loader. Used by public surfaces
   * (e.g. the {@code QUIZ_TEASER} page cell) that already receive an absolute public image
   * URL from the server.
   */
  directImageSrc?: string | null
}>()

const emit = defineEmits<{
  toggleMcOption: [idx: number]
  setFillGap: [gapIndex: number, value: string]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
  setConnectPair: [leftIndex: number, rightValue: string]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <QuestionHeader :question="question" :direct-image-src="directImageSrc" />

    <template v-if="question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE">
      <MultipleChoiceInput
        :config="config"
        :disabled="disabled"
        :mc-selections="mcSelections"
        :mc-display-order="mcDisplayOrder"
        @toggle-mc-option="(idx) => emit('toggleMcOption', idx)"
      />
    </template>

    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.TRUE_FALSE">
      <div class="flex gap-3">
        <SelectionToggleButton
          :selected="tfAnswer === true"
          :disabled="disabled"
          @toggle="!disabled && (tfAnswer = true)"
        >
          {{ t('quiz.attempt.true') }}
        </SelectionToggleButton>
        <SelectionToggleButton
          :selected="tfAnswer === false"
          :disabled="disabled"
          @toggle="!disabled && (tfAnswer = false)"
        >
          {{ t('quiz.attempt.false') }}
        </SelectionToggleButton>
      </div>
    </template>

    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.FREE_ANSWER || question.quizQuestionType === QuizQuestionTypes.ENUMERATION || question.quizQuestionType === QuizQuestionTypes.IMAGE_TEXT">
      <textarea
        :value="freeAnswer"
        rows="4"
        :disabled="disabled"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none disabled:opacity-60"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="freeAnswer = ($event.target as HTMLTextAreaElement).value"
      />
    </template>

    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
      <FillInBlankInput
        :config="config"
        :disabled="disabled"
        :fill-gaps="fillGaps"
        @set-fill-gap="(idx, val) => emit('setFillGap', idx, val)"
      />
    </template>

    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.ORDERING">
      <OrderingInput
        :config="config"
        :disabled="disabled"
        :order-items="orderItems"
        @reorder-items="(from, to) => emit('reorderItems', from, to)"
        @move-order-item="(idx, dir) => emit('moveOrderItem', idx, dir)"
      />
    </template>

    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.CONNECT">
      <ConnectInput
        :config="config"
        :disabled="disabled"
        :connect-pairs="connectPairs"
        :connect-right-order="connectRightOrder"
        @set-connect-pair="(leftIdx, rightVal) => emit('setConnectPair', leftIdx, rightVal)"
      />
    </template>

    <slot />
  </NeutralContainer>
</template>
