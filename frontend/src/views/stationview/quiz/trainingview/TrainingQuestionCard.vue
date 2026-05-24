/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import type { QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'

const props = defineProps<{
  question: QuizQuestion
  showAnswer: boolean
  userAnswer: string
  userMcSelections: Set<number>
  userTfAnswer: boolean | null
  userOrderItems: number[]
  userConnectPairs: Record<string, string>
}>()

const emit = defineEmits<{
  toggleMcOption: [idx: number]
  'update:userTfAnswer': [v: boolean]
  'update:userAnswer': [v: string]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
  setConnectPair: [leftIndex: number, rightValue: string]
}>()

const { t } = useI18n()

const config = computed<Record<string, unknown>>(() => {
  try { return JSON.parse(props.question.config || '{}') } catch { return {} }
})

const mcOptions = computed<{ text: string; correct?: boolean }[]>(() => {
  const opts = config.value.options
  return Array.isArray(opts) ? (opts as { text: string; correct?: boolean }[]) : []
})

const connectLeftItems = computed<string[]>(() => {
  const pairs = config.value.pairs as { left: string; right: string }[] | undefined
  if (pairs) return pairs.map(p => p.left)
  return (config.value.leftItems as string[]) ?? []
})

const connectRightItems = computed<string[]>(() => {
  const pairs = config.value.pairs as { left: string; right: string }[] | undefined
  if (pairs) return pairs.map(p => p.right)
  return (config.value.rightItems as string[]) ?? []
})

const orderedItems = computed<string[]>(() => {
  const items = (config.value.items as string[]) ?? []
  if (props.userOrderItems.length === items.length) return props.userOrderItems.map(i => items[i])
  return items
})

const correctTf = computed<boolean | undefined>(() => config.value.correctAnswer as boolean | undefined)

const correctOrderItems = computed<string[]>(() => (config.value.items as string[]) ?? [])

const correctPairs = computed<{ left: string; right: string }[]>(() => {
  return (config.value.pairs as { left: string; right: string }[]) ?? []
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <!-- Question -->
    <div class="space-y-1">
      <p class="font-semibold">{{ question.title }}</p>
      <p v-if="question.description" class="text-sm text-(--text-muted)">{{ question.description }}</p>
      <img v-if="question.imageUrl" :src="question.imageUrl" class="max-h-48 rounded-lg object-contain" alt="" />
    </div>

    <!-- Multiple Choice -->
    <template v-if="question.questionType === QuizQuestionTypes.MULTIPLE_CHOICE">
      <div class="space-y-2">
        <div
          v-for="(opt, i) in mcOptions"
          :key="i"
          class="flex items-center gap-3 p-3 rounded-lg border-2 cursor-pointer transition-colors"
          :class="[
            showAnswer
              ? opt.correct ? 'border-success bg-success/10' : userMcSelections.has(i) ? 'border-error bg-error/10' : 'border-bg-light-accent dark:border-bg-dark-accent'
              : userMcSelections.has(i) ? 'border-primary bg-primary/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50'
          ]"
          @click="!showAnswer && emit('toggleMcOption', i)"
        >
          <font-awesome-icon
            :icon="['fas', userMcSelections.has(i) ? 'square-check' : 'square']"
            :class="userMcSelections.has(i) ? 'text-primary' : 'text-(--text-muted)'"
          />
          <span class="text-sm flex-1">{{ opt.text }}</span>
          <font-awesome-icon v-if="showAnswer && opt.correct" :icon="['fas', 'check']" class="text-success" />
        </div>
      </div>
    </template>

    <!-- True / False -->
    <template v-else-if="question.questionType === QuizQuestionTypes.TRUE_FALSE">
      <div class="flex gap-3">
        <SelectionToggleButton
          :selected="userTfAnswer === true"
          :disabled="showAnswer"
          @toggle="!showAnswer && emit('update:userTfAnswer', true)"
        >
          {{ t('quiz.attempt.true') }}
        </SelectionToggleButton>
        <SelectionToggleButton
          :selected="userTfAnswer === false"
          :disabled="showAnswer"
          @toggle="!showAnswer && emit('update:userTfAnswer', false)"
        >
          {{ t('quiz.attempt.false') }}
        </SelectionToggleButton>
      </div>
      <div v-if="showAnswer" class="text-sm">
        <span class="text-(--text-muted)">{{ t('quiz.training.correctAnswer') }}: </span>
        <span :class="correctTf ? 'text-success' : 'text-error'">{{ correctTf ? t('quiz.attempt.true') : t('quiz.attempt.false') }}</span>
      </div>
    </template>

    <!-- Free Answer / Image Text -->
    <template v-else-if="question.questionType === QuizQuestionTypes.FREE_ANSWER || question.questionType === QuizQuestionTypes.IMAGE_TEXT">
      <textarea
        :value="userAnswer"
        rows="3"
        :disabled="showAnswer"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none disabled:opacity-60"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="emit('update:userAnswer', ($event.target as HTMLTextAreaElement).value)"
      />
      <div v-if="showAnswer && (config.answers as string[] | undefined)?.length" class="space-y-1">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.sampleAnswer') }}:</span>
        <p v-for="(ans, i) in (config.answers as string[])" :key="i" class="text-sm text-success">{{ ans }}</p>
      </div>
    </template>

    <!-- Fill in the Blank -->
    <template v-else-if="question.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
      <div class="space-y-2">
        <div
          v-for="(ans, gapIdx) in ((config.answers as string[]) ?? [])"
          :key="gapIdx"
          class="flex items-center gap-2"
        >
          <span class="text-sm text-(--text-muted) w-6">{{ gapIdx + 1 }}.</span>
          <input
            :value="userAnswer"
            :disabled="showAnswer"
            class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm disabled:opacity-60"
            :placeholder="t('quiz.attempt.fillGapPlaceholder', { n: gapIdx + 1 })"
            @input="emit('update:userAnswer', ($event.target as HTMLInputElement).value)"
          />
          <span v-if="showAnswer" class="text-sm text-success font-medium">{{ ans }}</span>
        </div>
      </div>
    </template>

    <!-- Ordering -->
    <template v-else-if="question.questionType === QuizQuestionTypes.ORDERING">
      <div class="space-y-2">
        <div
          v-for="(item, i) in orderedItems"
          :key="i"
          class="flex items-center gap-3 p-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent"
        >
          <span class="text-xs text-(--text-muted) w-5 text-right shrink-0">{{ i + 1 }}.</span>
          <span class="flex-1 text-sm">{{ item }}</span>
          <div v-if="!showAnswer" class="flex flex-col gap-0.5 shrink-0">
            <IconButton :icon="['fas', 'chevron-up']" :label="t('quiz.attempt.moveUp')" :disabled="i === 0" class="text-xs" @click="emit('moveOrderItem', i, -1)" />
            <IconButton :icon="['fas', 'chevron-down']" :label="t('quiz.attempt.moveDown')" :disabled="i === orderedItems.length - 1" class="text-xs" @click="emit('moveOrderItem', i, 1)" />
          </div>
        </div>
      </div>
      <div v-if="showAnswer" class="space-y-1">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctOrder') }}:</span>
        <div v-for="(item, i) in correctOrderItems" :key="i" class="flex items-center gap-2 text-sm text-success">
          <span class="w-5 text-right">{{ i + 1 }}.</span>
          <span>{{ item }}</span>
        </div>
      </div>
    </template>

    <!-- Connect -->
    <template v-else-if="question.questionType === QuizQuestionTypes.CONNECT">
      <div class="space-y-2">
        <div v-for="(left, leftIdx) in connectLeftItems" :key="leftIdx" class="flex items-center gap-3">
          <span class="text-sm font-medium w-1/3 shrink-0">{{ left }}</span>
          <select
            :value="userConnectPairs[String(leftIdx)] ?? ''"
            :disabled="showAnswer"
            class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-sm focus:outline-none focus:border-primary disabled:opacity-60"
            @change="emit('setConnectPair', leftIdx, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">—</option>
            <option v-for="right in connectRightItems" :key="right" :value="right">{{ right }}</option>
          </select>
        </div>
      </div>
      <div v-if="showAnswer" class="space-y-1">
        <span class="text-xs text-(--text-muted)">{{ t('quiz.training.correctPairs') }}:</span>
        <div v-for="pair in correctPairs" :key="pair.left" class="text-sm text-success flex items-center gap-2">
          <span>{{ pair.left }}</span>
          <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-xs" />
          <span>{{ pair.right }}</span>
        </div>
      </div>
    </template>

    <!-- Answer reveal overlay -->
    <SuccessContainer v-if="showAnswer && question.questionType === QuizQuestionTypes.FREE_ANSWER" class="text-xs">
      {{ t('quiz.training.checkYourAnswer') }}
    </SuccessContainer>
  </NeutralContainer>
</template>
