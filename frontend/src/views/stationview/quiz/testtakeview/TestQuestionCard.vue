/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import type { QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'

const props = defineProps<{
  questionDetail: QuizQuestion
  config: Record<string, unknown>
  answerParsed: Record<string, unknown>
  connectLeftItems: string[]
  connectRightItems: string[]
}>()

const emit = defineEmits<{
  setMCAnswer: [optionIndex: number, isMulti: boolean]
  setFillBlankGap: [gapIndex: number, value: string]
  setFreeAnswer: [text: string]
  setConnectPair: [leftIndex: number, rightValue: string]
  setImageTextAnswer: [text: string]
  setTrueFalse: [value: boolean]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
}>()

const { t } = useI18n()

function mcOptions(): { text: string; correct?: boolean }[] {
  const opts = props.config.options
  if (Array.isArray(opts)) return opts as { text: string; correct?: boolean }[]
  return []
}

function isMcSelected(i: number): boolean {
  const selected = (props.answerParsed as { selected?: number[] }).selected ?? []
  return selected.includes(i)
}

function fillGapValue(gapIndex: number): string {
  const gaps = (props.answerParsed as { gaps?: Record<string, string> }).gaps ?? {}
  return gaps[String(gapIndex)] ?? ''
}

function freeAnswerText(): string {
  return (props.answerParsed as { text?: string }).text ?? ''
}

function connectPairValue(leftIndex: number): string {
  const pairs = (props.answerParsed as { pairs?: Record<string, string> }).pairs ?? {}
  return pairs[String(leftIndex)] ?? ''
}

function tfValue(): boolean | null {
  const v = (props.answerParsed as { value?: boolean | null }).value
  return v ?? null
}

function orderItems(): string[] {
  const cfgItems = (props.config.items as string[]) ?? []
  const order = (props.answerParsed as { order?: number[] }).order
  if (order && order.length === cfgItems.length) return order.map(i => cfgItems[i])
  return cfgItems
}

function orderIndices(): number[] {
  const cfgItems = (props.config.items as string[]) ?? []
  const order = (props.answerParsed as { order?: number[] }).order
  if (order && order.length === cfgItems.length) return order
  return cfgItems.map((_, i) => i)
}

const isMultipleCorrect = (): boolean => {
  const opts = mcOptions()
  return opts.filter(o => o.correct).length > 1
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <!-- Question header -->
    <div class="space-y-1">
      <p class="font-semibold">{{ questionDetail.title }}</p>
      <p v-if="questionDetail.description" class="text-sm text-(--text-muted)">{{ questionDetail.description }}</p>
      <img
        v-if="questionDetail.imageUrl"
        :src="questionDetail.imageUrl"
        class="max-h-48 rounded-lg object-contain"
        alt=""
      />
    </div>

    <!-- Multiple Choice -->
    <template v-if="questionDetail.questionType === QuizQuestionTypes.MULTIPLE_CHOICE">
      <div class="space-y-2">
        <div
          v-for="(opt, i) in mcOptions()"
          :key="i"
          class="flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors"
          :class="isMcSelected(i) ? 'border-primary bg-primary/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50'"
          @click="emit('setMCAnswer', i, isMultipleCorrect())"
        >
          <font-awesome-icon
            :icon="['fas', isMultipleCorrect() ? (isMcSelected(i) ? 'square-check' : 'square') : (isMcSelected(i) ? 'circle-dot' : 'circle')]"
            :class="isMcSelected(i) ? 'text-primary' : 'text-(--text-muted)'"
          />
          <span class="text-sm">{{ opt.text }}</span>
        </div>
      </div>
    </template>

    <!-- True / False -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.TRUE_FALSE">
      <div class="flex gap-3">
        <SelectionToggleButton :selected="tfValue() === true" @toggle="emit('setTrueFalse', true)">
          {{ t('quiz.attempt.true') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="tfValue() === false" @toggle="emit('setTrueFalse', false)">
          {{ t('quiz.attempt.false') }}
        </SelectionToggleButton>
      </div>
    </template>

    <!-- Fill in the Blank -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
      <div class="space-y-2">
        <div
          v-for="(_, gapIdx) in ((config.answers as string[]) ?? [])"
          :key="gapIdx"
          class="flex items-center gap-2"
        >
          <span class="text-sm text-(--text-muted) w-6">{{ gapIdx + 1 }}.</span>
          <input
            :value="fillGapValue(gapIdx)"
            class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm"
            :placeholder="t('quiz.attempt.fillGapPlaceholder', { n: gapIdx + 1 })"
            @input="emit('setFillBlankGap', gapIdx, ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>
    </template>

    <!-- Free Answer -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.FREE_ANSWER">
      <textarea
        :value="freeAnswerText()"
        rows="4"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="emit('setFreeAnswer', ($event.target as HTMLTextAreaElement).value)"
      />
    </template>

    <!-- Image Text -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.IMAGE_TEXT">
      <textarea
        :value="freeAnswerText()"
        rows="4"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="emit('setImageTextAnswer', ($event.target as HTMLTextAreaElement).value)"
      />
    </template>

    <!-- Connect -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.CONNECT">
      <div class="space-y-2">
        <div v-for="(left, leftIdx) in connectLeftItems" :key="leftIdx" class="flex items-center gap-3">
          <span class="text-sm font-medium w-1/3 shrink-0">{{ left }}</span>
          <select
            :value="connectPairValue(leftIdx)"
            class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-sm focus:outline-none focus:border-primary"
            @change="emit('setConnectPair', leftIdx, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">—</option>
            <option v-for="right in connectRightItems" :key="right" :value="right">{{ right }}</option>
          </select>
        </div>
      </div>
    </template>

    <!-- Ordering -->
    <template v-else-if="questionDetail.questionType === QuizQuestionTypes.ORDERING">
      <div class="space-y-2">
        <div
          v-for="(item, i) in orderItems()"
          :key="orderIndices()[i]"
          class="flex items-center gap-3 p-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent"
        >
          <span class="text-xs text-(--text-muted) w-5 text-right shrink-0">{{ i + 1 }}.</span>
          <span class="flex-1 text-sm">{{ item }}</span>
          <div class="flex flex-col gap-0.5 shrink-0">
            <IconButton
              :icon="['fas', 'chevron-up']"
              :label="t('quiz.attempt.moveUp')"
              :disabled="i === 0"
              class="text-xs"
              @click="emit('moveOrderItem', i, -1)"
            />
            <IconButton
              :icon="['fas', 'chevron-down']"
              :label="t('quiz.attempt.moveDown')"
              :disabled="i === orderItems().length - 1"
              class="text-xs"
              @click="emit('moveOrderItem', i, 1)"
            />
          </div>
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
