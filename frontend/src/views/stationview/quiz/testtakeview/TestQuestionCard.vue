/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import QuestionInputCard from '../questioncard/QuestionInputCard.vue'
import type { QuizQuestion } from '@/api/quiz'

const props = defineProps<{
  questionDetail: QuizQuestion
  config: Record<string, unknown>
  answerParsed: Record<string, unknown>
  connectLeftItems: string[]
  connectRightItems: string[]
  mcDisplayOrder?: number[]
}>()

const emit = defineEmits<{
  setMCAnswer: [optionIndex: number, isMulti: boolean]
  setFillBlankGap: [gapIndex: number, value: string]
  setFreeAnswer: [text: string]
  setConnectPair: [leftIndex: number, rightValue: string]
  setImageTextAnswer: [text: string]
  setTrueFalse: [value: boolean]
  reorderItems: [fromIndex: number, toIndex: number]
}>()

const mcSelections = computed<Set<number>>(() => {
  const selected = (props.answerParsed as { selected?: number[] }).selected ?? []
  return new Set(selected)
})

const tfAnswer = computed<boolean | null>(() => {
  const v = (props.answerParsed as { value?: boolean | null }).value
  return v ?? null
})

const freeAnswer = computed<string>(() => {
  return (props.answerParsed as { text?: string }).text ?? ''
})

const fillGaps = computed<Record<string, string>>(() => {
  return (props.answerParsed as { gaps?: Record<string, string> }).gaps ?? {}
})

const orderItems = computed<number[]>(() => {
  const cfgItems = (props.config.items as string[]) ?? []
  const order = (props.answerParsed as { order?: number[] }).order
  if (order && order.length === cfgItems.length) return order
  return cfgItems.map((_, i) => i)
})

const connectPairs = computed<Record<string, string>>(() => {
  return (props.answerParsed as { pairs?: Record<string, string> }).pairs ?? {}
})

const isMultipleCorrect = computed(() => {
  // Use the multiSelect hint from sanitized config, or fall back to counting correct options
  if (props.config.multiSelect !== undefined) return props.config.multiSelect as boolean
  const opts = props.config.options
  if (!Array.isArray(opts)) return false
  return (opts as { correct?: boolean }[]).filter(o => o.correct).length > 1
})

// Use the config with connect items injected for the shared component
const effectiveConfig = computed<Record<string, unknown>>(() => {
  const cfg = { ...props.config }
  if (props.connectLeftItems.length > 0) cfg.leftItems = props.connectLeftItems
  if (props.connectRightItems.length > 0) cfg.rightItems = props.connectRightItems
  return cfg
})

function handleToggleMc(idx: number) {
  emit('setMCAnswer', idx, isMultipleCorrect.value)
}

function handleFreeAnswer(text: string) {
  if (props.questionDetail.quizQuestionType === 'IMAGE_TEXT') {
    emit('setImageTextAnswer', text)
  } else {
    emit('setFreeAnswer', text)
  }
}
</script>

<template>
  <QuestionInputCard
    :question="questionDetail"
    :config="effectiveConfig"
    :disabled="false"
    :mc-selections="mcSelections"
    :tf-answer="tfAnswer"
    :free-answer="freeAnswer"
    :fill-gaps="fillGaps"
    :order-items="orderItems"
    :connect-pairs="connectPairs"
    :mc-display-order="mcDisplayOrder"
    @toggle-mc-option="handleToggleMc"
    @update:tf-answer="(v: boolean | null) => v !== null && emit('setTrueFalse', v)"
    @update:free-answer="handleFreeAnswer"
    @set-fill-gap="(gi: number, v: string) => emit('setFillBlankGap', gi, v)"
    @reorder-items="(from: number, to: number) => emit('reorderItems', from, to)"
    @set-connect-pair="(li: number, rv: string) => emit('setConnectPair', li, rv)"
  />
</template>
