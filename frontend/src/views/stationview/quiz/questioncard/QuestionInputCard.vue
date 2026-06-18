/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import type { QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import AuthImage from '@/components/display/AuthImage.vue'

const props = defineProps<{
  question: QuizQuestion
  config: Record<string, unknown>
  disabled: boolean
  mcSelections: Set<number>
  tfAnswer: boolean | null
  freeAnswer: string
  fillGaps: Record<string, string>
  orderItems: number[]
  connectPairs: Record<string, string>
  mcDisplayOrder?: number[]
  connectRightOrder?: number[]
  /**
   * When provided, the question image is rendered with a plain {@code <img>} pointing at this
   * URL instead of going through the authenticated {@link AuthImage} loader. Used by public
   * surfaces (e.g. the {@code QUIZ_TEASER} page cell) that already receive an absolute public
   * image URL from the server.
   */
  directImageSrc?: string | null
}>()

const emit = defineEmits<{
  toggleMcOption: [idx: number]
  'update:tfAnswer': [v: boolean]
  'update:freeAnswer': [v: string]
  setFillGap: [gapIndex: number, value: string]
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
  setConnectPair: [leftIndex: number, rightValue: string]
}>()

const { t } = useI18n()

const mcOptionsRaw = computed<{ text: string; correct?: boolean }[]>(() => {
  const opts = props.config.options
  return Array.isArray(opts) ? (opts as { text: string; correct?: boolean }[]) : []
})

// Displayed MC options with original index preserved
const mcDisplayItems = computed<{ text: string; correct?: boolean; originalIndex: number }[]>(() => {
  const opts = mcOptionsRaw.value
  const order = props.mcDisplayOrder
  if (order && order.length === opts.length) {
    return order.map(origIdx => ({ ...opts[origIdx], originalIndex: origIdx }))
  }
  return opts.map((opt, i) => ({ ...opt, originalIndex: i }))
})

const connectLeftItems = computed<string[]>(() => {
  const pairs = props.config.pairs as { left: string; right: string }[] | undefined
  if (pairs) return pairs.map(p => p.left)
  return (props.config.leftItems as string[]) ?? []
})

const connectRightItems = computed<string[]>(() => {
  const pairs = props.config.pairs as { left: string; right: string }[] | undefined
  const raw = pairs ? pairs.map(p => p.right) : ((props.config.rightItems as string[]) ?? [])
  const order = props.connectRightOrder
  if (order && order.length === raw.length) return order.map(i => raw[i])
  return raw
})

const orderedItems = computed<string[]>(() => {
  const items = (props.config.items as string[]) ?? []
  if (props.orderItems.length === items.length) return props.orderItems.map(i => items[i])
  return items
})

const questionImageSrc = computed(() =>
  props.question.imageUrl ? quiz.questionImageUrl(props.question.id, 300) : null
)

const fillAnswers = computed<string[]>(() => (props.config.answers as string[]) ?? [])
const fillGapCount = computed(() => (props.config.gapCount as number) ?? fillAnswers.value.length)

// Drag-and-drop state for ordering
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function onDragStart(e: DragEvent, index: number) {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = index
}

function onDrop(e: DragEvent, toIndex: number) {
  e.preventDefault()
  if (dragIndex.value !== null && dragIndex.value !== toIndex) {
    emit('reorderItems', dragIndex.value, toIndex)
  }
  dragIndex.value = null
  dragOverIndex.value = null
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

// Connect interaction state
const connectContainer = ref<HTMLElement | null>(null)
const selectedLeftIdx = ref<number | null>(null)
const connectDragFrom = ref<number | null>(null)
const connectDragOver = ref<number | null>(null)

// Reverse lookup: which left index is a right item connected to?
const reverseConnectMap = computed<Record<string, number>>(() => {
  const map: Record<string, number> = {}
  for (const [leftIdx, rightVal] of Object.entries(props.connectPairs)) {
    if (rightVal) map[rightVal] = Number(leftIdx)
  }
  return map
})

function isRightConnected(right: string): boolean {
  return right in reverseConnectMap.value
}

function connectPair(leftIdx: number, right: string) {
  // Clear any existing connection to this right item (enforce 1:1)
  if (right in reverseConnectMap.value) {
    const oldLeftIdx = reverseConnectMap.value[right]
    if (oldLeftIdx !== leftIdx) {
      emit('setConnectPair', oldLeftIdx, '')
    }
  }
  emit('setConnectPair', leftIdx, right)
}

function onLeftClick(leftIdx: number) {
  if (props.disabled) return
  if (selectedLeftIdx.value === leftIdx) {
    selectedLeftIdx.value = null
  } else {
    selectedLeftIdx.value = leftIdx
  }
}

function onRightClick(rightIdx: number) {
  if (props.disabled) return
  const right = connectRightItems.value[rightIdx]
  if (selectedLeftIdx.value !== null) {
    connectPair(selectedLeftIdx.value, right)
    selectedLeftIdx.value = null
  } else {
    // If this right is already connected, clicking it removes the connection
    if (right in reverseConnectMap.value) {
      emit('setConnectPair', reverseConnectMap.value[right], '')
    }
  }
}

function onConnectDragStart(e: DragEvent, leftIdx: number) {
  if (props.disabled) return
  connectDragFrom.value = leftIdx
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'link'
    e.dataTransfer.setData('text/plain', String(leftIdx))
  }
}

function onConnectDragOverRight(e: DragEvent, rightIdx: number) {
  if (connectDragFrom.value === null) return
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'link'
  connectDragOver.value = rightIdx
}

function onConnectDropRight(e: DragEvent, rightIdx: number) {
  e.preventDefault()
  if (connectDragFrom.value !== null) {
    connectPair(connectDragFrom.value, connectRightItems.value[rightIdx])
  }
  connectDragFrom.value = null
  connectDragOver.value = null
}

function onConnectDragEnd() {
  connectDragFrom.value = null
  connectDragOver.value = null
  selectedLeftIdx.value = null
}

function removeConnection(leftIdx: number) {
  if (props.disabled) return
  emit('setConnectPair', leftIdx, '')
}

// SVG line positions
function getLinePoints(): { x1: number; y1: number; x2: number; y2: number; leftIdx: number }[] {
  if (!connectContainer.value) return []
  const container = connectContainer.value
  const containerRect = container.getBoundingClientRect()
  const lines: { x1: number; y1: number; x2: number; y2: number; leftIdx: number }[] = []

  for (const [leftIdxStr, rightVal] of Object.entries(props.connectPairs)) {
    if (!rightVal) continue
    const leftIdx = Number(leftIdxStr)
    const rightIdx = connectRightItems.value.indexOf(rightVal)
    if (rightIdx < 0) continue

    const leftEl = container.querySelector(`[data-connect-left="${leftIdx}"]`)
    const rightEl = container.querySelector(`[data-connect-right="${rightIdx}"]`)
    if (!leftEl || !rightEl) continue

    const leftRect = leftEl.getBoundingClientRect()
    const rightRect = rightEl.getBoundingClientRect()

    lines.push({
      x1: leftRect.right - containerRect.left,
      y1: leftRect.top + leftRect.height / 2 - containerRect.top,
      x2: rightRect.left - containerRect.left,
      y2: rightRect.top + rightRect.height / 2 - containerRect.top,
      leftIdx,
    })
  }
  return lines
}

interface ConnectLine {
  x1: number
  y1: number
  x2: number
  y2: number
  leftIdx: number
}

const connectLines = ref<ConnectLine[]>([])

function updateConnectLines() {
  requestAnimationFrame(() => {
    connectLines.value = getLinePoints()
  })
}

// Recalculate lines when pairs change
watch(() => props.connectPairs, () => nextTick(updateConnectLines), { deep: true })
onMounted(() => nextTick(updateConnectLines))

</script>

<template>
  <NeutralContainer class="space-y-4">
    <!-- Question header -->
    <div class="space-y-1">
      <p class="font-semibold">{{ question.title }}</p>
      <p v-if="question.description" class="text-sm text-(--text-muted)">{{ question.description }}</p>
      <img v-if="directImageSrc" :src="directImageSrc" class="max-h-48 rounded-lg object-contain" alt=""/>
      <AuthImage v-else-if="questionImageSrc" :src="questionImageSrc" class="max-h-48 rounded-lg object-contain" alt="" />
    </div>

    <!-- Multiple Choice -->
    <template v-if="question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE">
      <div class="space-y-2">
        <div
          v-for="item in mcDisplayItems"
          :key="item.originalIndex"
          class="flex items-center gap-2 p-3 rounded-lg border-2 cursor-pointer transition-colors"
          :class="[
            mcSelections.has(item.originalIndex) ? 'border-primary bg-primary/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50',
            disabled ? 'pointer-events-none opacity-60' : ''
          ]"
          @click="!disabled && emit('toggleMcOption', item.originalIndex)"
        >
          <font-awesome-icon
            :icon="['fas', mcSelections.has(item.originalIndex) ? 'square-check' : 'square']"
            :class="mcSelections.has(item.originalIndex) ? 'text-primary' : 'text-(--text-muted)'"
          />
          <span class="text-sm flex-1">{{ item.text }}</span>
        </div>
      </div>
    </template>

    <!-- True / False -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.TRUE_FALSE">
      <div class="flex gap-3">
        <SelectionToggleButton
          :selected="tfAnswer === true"
          :disabled="disabled"
          @toggle="!disabled && emit('update:tfAnswer', true)"
        >
          {{ t('quiz.attempt.true') }}
        </SelectionToggleButton>
        <SelectionToggleButton
          :selected="tfAnswer === false"
          :disabled="disabled"
          @toggle="!disabled && emit('update:tfAnswer', false)"
        >
          {{ t('quiz.attempt.false') }}
        </SelectionToggleButton>
      </div>
    </template>

    <!-- Free Answer / Enumeration -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.FREE_ANSWER || question.quizQuestionType === QuizQuestionTypes.ENUMERATION">
      <textarea
        :value="freeAnswer"
        rows="4"
        :disabled="disabled"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none disabled:opacity-60"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="emit('update:freeAnswer', ($event.target as HTMLTextAreaElement).value)"
      />
    </template>

    <!-- Image Text -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.IMAGE_TEXT">
      <textarea
        :value="freeAnswer"
        rows="4"
        :disabled="disabled"
        class="w-full px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm resize-none disabled:opacity-60"
        :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
        @input="emit('update:freeAnswer', ($event.target as HTMLTextAreaElement).value)"
      />
    </template>

    <!-- Fill in the Blank -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
      <p v-if="config.text" class="text-sm whitespace-pre-wrap">{{ config.text }}</p>
      <div class="space-y-2">
        <div
          v-for="gapIdx in fillGapCount"
          :key="gapIdx - 1"
          class="flex items-center gap-2"
        >
          <span class="text-sm text-(--text-muted) w-6">{{ gapIdx }}.</span>
          <input
            :value="fillGaps[String(gapIdx - 1)] ?? ''"
            :disabled="disabled"
            class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm disabled:opacity-60"
            :placeholder="t('quiz.attempt.fillGapPlaceholder', { n: gapIdx })"
            @input="emit('setFillGap', gapIdx - 1, ($event.target as HTMLInputElement).value)"
          />
        </div>
      </div>
    </template>

    <!-- Ordering -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.ORDERING">
      <div class="space-y-2">
        <div
          v-for="(item, i) in orderedItems"
          :key="i"
          :draggable="!disabled"
          class="flex items-center gap-2 p-2 rounded-lg border transition-colors"
          :class="[
            dragOverIndex === i && dragIndex !== i
              ? 'border-primary bg-primary/5'
              : 'border-bg-light-accent dark:border-bg-dark-accent',
            dragIndex === i ? 'opacity-50' : '',
            !disabled ? 'cursor-grab active:cursor-grabbing' : ''
          ]"
          @dragstart="onDragStart($event, i)"
          @dragover="onDragOver($event, i)"
          @drop="onDrop($event, i)"
          @dragend="onDragEnd"
        >
          <font-awesome-icon v-if="!disabled" :icon="['fas', 'grip-vertical']" class="text-xs text-(--text-muted) shrink-0" />
          <span class="text-xs text-(--text-muted) w-5 text-right shrink-0">{{ i + 1 }}.</span>
          <span class="flex-1 text-sm">{{ item }}</span>
          <div v-if="!disabled" class="flex flex-col gap-0.5 shrink-0">
            <IconButton :icon="['fas', 'chevron-up']" :label="t('quiz.attempt.moveUp')" :disabled="i === 0" class="text-xs" @click="emit('moveOrderItem', i, -1)" />
            <IconButton :icon="['fas', 'chevron-down']" :label="t('quiz.attempt.moveDown')" :disabled="i === orderedItems.length - 1" class="text-xs" @click="emit('moveOrderItem', i, 1)" />
          </div>
        </div>
      </div>
    </template>

    <!-- Connect -->
    <template v-else-if="question.quizQuestionType === QuizQuestionTypes.CONNECT">
      <div ref="connectContainer" class="relative">
        <!-- SVG lines -->
        <svg class="absolute inset-0 w-full h-full pointer-events-none z-10">
          <line
            v-for="line in connectLines"
            :key="line.leftIdx"
            :x1="line.x1" :y1="line.y1" :x2="line.x2" :y2="line.y2"
            class="stroke-primary"
            stroke-width="2"
            stroke-linecap="round"
          />
        </svg>

        <div class="flex justify-between gap-12">
          <!-- Left column -->
          <div class="flex flex-col gap-2 flex-1">
            <button
              v-for="(left, leftIdx) in connectLeftItems"
              :key="leftIdx"
              type="button"
              :data-connect-left="leftIdx"
              :draggable="!disabled"
              class="px-3 py-2 rounded-lg border-2 text-sm font-medium text-left transition-colors cursor-pointer"
              :class="[
                selectedLeftIdx === leftIdx
                  ? 'border-primary bg-primary/10'
                  : connectPairs[String(leftIdx)]
                    ? 'border-success bg-success/5'
                    : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50',
                disabled ? 'pointer-events-none opacity-60' : ''
              ]"
              @click="onLeftClick(leftIdx)"
              @dragstart="onConnectDragStart($event, leftIdx)"
              @dragend="onConnectDragEnd"
            >
              {{ left }}
              <font-awesome-icon
                v-if="connectPairs[String(leftIdx)]"
                :icon="['fas', 'xmark']"
                class="ml-1 text-xs text-(--text-muted) hover:text-error"
                @click.stop="removeConnection(leftIdx)"
              />
            </button>
          </div>

          <!-- Right column -->
          <div class="flex flex-col gap-2 flex-1">
            <button
              v-for="(right, rightIdx) in connectRightItems"
              :key="rightIdx"
              type="button"
              :data-connect-right="rightIdx"
              class="px-3 py-2 rounded-lg border-2 text-sm font-medium text-left transition-colors cursor-pointer"
              :class="[
                connectDragOver === rightIdx
                  ? 'border-primary bg-primary/10'
                  : isRightConnected(right)
                    ? 'border-success bg-success/5'
                    : selectedLeftIdx !== null
                      ? 'border-primary/50 hover:border-primary hover:bg-primary/5'
                      : 'border-bg-light-accent dark:border-bg-dark-accent',
                disabled ? 'pointer-events-none opacity-60' : ''
              ]"
              @click="onRightClick(rightIdx)"
              @dragover="onConnectDragOverRight($event, rightIdx)"
              @drop="onConnectDropRight($event, rightIdx)"
            >
              {{ right }}
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- Slot for answer reveal or other content below the input -->
    <slot />
  </NeutralContainer>
</template>
