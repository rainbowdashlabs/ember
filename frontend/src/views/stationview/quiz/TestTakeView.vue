/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TestResultSummary from './testtakeview/TestResultSummary.vue'
import TestActivePhase from './testtakeview/TestActivePhase.vue'
import TestSubmitModal from './testtakeview/TestSubmitModal.vue'
import { QuizQuestionTypes } from '@/api/types'
import type { QuizAttemptDetail, QuizTest, QuizQuestion } from '@/api/types'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()
const { loaded } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const testId = computed(() => Number(route.params.id))

const attemptDetail = ref<QuizAttemptDetail | null>(null)
const test = ref<QuizTest | null>(null)
const questionData = ref<Map<number, QuizQuestion>>(new Map())

const currentIndex = ref(0)

const answers = ref<Map<number, string>>(new Map())

const timerSeconds = ref(0)
const timerInterval = ref<ReturnType<typeof setInterval> | null>(null)

const submitted = ref(false)
const submitModalOpen = ref(false)

const attempt = computed(() => attemptDetail.value?.attempt ?? null)
const attemptQuestions = computed(() => attemptDetail.value?.questions ?? [])
const sortedQuestions = computed(() =>
  [...attemptQuestions.value].sort((a, b) => a.position - b.position)
)
const currentQuestion = computed(() => sortedQuestions.value[currentIndex.value] ?? null)
const totalQuestions = computed(() => sortedQuestions.value.length)
const isFirstQuestion = computed(() => currentIndex.value === 0)
const isLastQuestion = computed(() => currentIndex.value === totalQuestions.value - 1)

const currentQuestionDetail = computed(() => {
  if (!currentQuestion.value) return null
  return questionData.value.get(currentQuestion.value.questionId) ?? null
})

const displayOrders = ref<Map<string, number[]>>(new Map())

function shuffle<T>(array: T[]): T[] {
  const result = [...array]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

function getDisplayOrder(key: string, length: number): number[] {
  if (displayOrders.value.has(key)) return displayOrders.value.get(key)!
  const order = shuffle(Array.from({ length }, (_, i) => i))
  displayOrders.value.set(key, order)
  return order
}

const mcDisplayOrder = computed<number[]>(() => {
  if (!currentQuestion.value || !currentQuestionDetail.value) return []
  if (currentQuestionDetail.value.quizQuestionType !== QuizQuestionTypes.MULTIPLE_CHOICE) return []
  const opts = (currentQuestionDetail.value.config?.options as unknown[]) ?? []
  return getDisplayOrder(`mc-${currentQuestion.value.questionId}`, opts.length)
})

const currentConfig = computed(() => {
  if (!currentQuestionDetail.value) return {}
  return currentQuestionDetail.value.config ?? {}
})

const connectLeftItems = computed<string[]>(() => {
  const cfg = currentConfig.value
  if (cfg.leftItems) return cfg.leftItems as string[]
  if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.left)
  return []
})
const connectRightItemsRaw = computed<string[]>(() => {
  const cfg = currentConfig.value
  if (cfg.rightItems) return cfg.rightItems as string[]
  if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.right)
  return []
})
const connectRightItems = computed<string[]>(() => {
  const raw = connectRightItemsRaw.value
  if (!currentQuestion.value || raw.length === 0) return raw
  const order = getDisplayOrder(`connect-${currentQuestion.value.questionId}`, raw.length)
  return order.map(i => raw[i])
})

const currentAnswer = computed({
  get: () => {
    if (!currentQuestion.value) return ''
    return answers.value.get(currentQuestion.value.questionId) ?? ''
  },
  set: (val: string) => {
    if (!currentQuestion.value) return
    answers.value.set(currentQuestion.value.questionId, val)
  },
})

const currentAnswerParsed = computed(() => {
  try { return JSON.parse(currentAnswer.value || '{}') } catch { return {} }
})

const timerDisplay = computed(() => {
  if (!test.value?.timeLimit) return ''
  const remaining = Math.max(0, (test.value.timeLimit * 60) - timerSeconds.value)
  const minutes = Math.floor(remaining / 60)
  const seconds = remaining % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const timerExpired = computed(() => {
  if (!test.value?.timeLimit) return false
  return timerSeconds.value >= test.value.timeLimit * 60
})

function startTimer() {
  if (timerInterval.value) return
  if (!test.value?.timeLimit) return
  if (!attempt.value) return

  const startedAt = new Date(attempt.value.startedAt).getTime()
  const now = Date.now()
  timerSeconds.value = Math.floor((now - startedAt) / 1000)

  timerInterval.value = setInterval(() => {
    timerSeconds.value++
    if (timerExpired.value) {
      stopTimer()
      doSubmit()
    }
  }, 1000)
}

function stopTimer() {
  if (timerInterval.value) {
    clearInterval(timerInterval.value)
    timerInterval.value = null
  }
}

function navigateToQuestion(index: number) {
  if (index >= 0 && index < totalQuestions.value) {
    autoSaveCurrentAnswer()
    currentIndex.value = index
  }
}

function goNext() {
  if (!isLastQuestion.value) navigateToQuestion(currentIndex.value + 1)
}

function goPrev() {
  if (!isFirstQuestion.value) navigateToQuestion(currentIndex.value - 1)
}

function setMCAnswer(optionIndex: number, isMulti: boolean) {
  const parsed = currentAnswerParsed.value
  let selected: number[] = Array.isArray(parsed.selected) ? [...parsed.selected] : []

  if (isMulti) {
    const idx = selected.indexOf(optionIndex)
    if (idx >= 0) selected.splice(idx, 1)
    else selected.push(optionIndex)
  } else {
    selected = [optionIndex]
  }

  currentAnswer.value = JSON.stringify({ selected })
}

function setFillBlankGap(gapIndex: number, value: string) {
  const parsed = currentAnswerParsed.value
  const gaps: Record<string, string> = parsed.gaps ?? {}
  gaps[String(gapIndex)] = value
  currentAnswer.value = JSON.stringify({ gaps })
}

function setFreeAnswer(text: string) {
  currentAnswer.value = JSON.stringify({ text })
}

function setConnectPair(leftIndex: number, rightValue: string) {
  const parsed = currentAnswerParsed.value
  const pairs: Record<string, string> = parsed.pairs ?? {}
  pairs[String(leftIndex)] = rightValue
  currentAnswer.value = JSON.stringify({ pairs })
}

function setImageTextAnswer(text: string) {
  currentAnswer.value = JSON.stringify({ text })
}

function setTrueFalse(value: boolean) {
  currentAnswer.value = JSON.stringify({ value })
}

function reorderItems(fromIndex: number, toIndex: number) {
  const parsed = currentAnswerParsed.value
  const order: number[] = Array.isArray(parsed.order) ? [...parsed.order] : []
  const [moved] = order.splice(fromIndex, 1)
  order.splice(toIndex, 0, moved)
  currentAnswer.value = JSON.stringify({ order })
}

function moveOrderItem(index: number, direction: -1 | 1) {
  const parsed = currentAnswerParsed.value
  const order: number[] = Array.isArray(parsed.order) ? [...parsed.order] : []
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= order.length) return
  const temp = order[index]
  order[index] = order[newIdx]
  order[newIdx] = temp
  currentAnswer.value = JSON.stringify({ order })
}

let saveDebounce: ReturnType<typeof setTimeout> | null = null

function autoSaveCurrentAnswer() {
  if (!attempt.value || !currentQuestion.value) return
  const answerStr = answers.value.get(currentQuestion.value.questionId) ?? ''
  if (!answerStr) return

  if (saveDebounce) clearTimeout(saveDebounce)
  saveDebounce = setTimeout(async () => {
    try {
      await quiz.saveAnswer(attempt.value!.id, currentQuestion.value!.questionId, answerStr)
    } catch {
      void 0
    }
  }, 500)
}

watch(currentAnswer, () => {
  autoSaveCurrentAnswer()
})

function confirmSubmit() {
  submitModalOpen.value = true
}

async function doSubmit() {
  submitModalOpen.value = false
  if (!attempt.value || submitted.value) return

  for (const [questionId, answerStr] of answers.value.entries()) {
    if (answerStr) {
      try {
        await quiz.saveAnswer(attempt.value.id, questionId, answerStr)
      } catch { void 0 }
    }
  }

  try {
    await quiz.submitAttempt(attempt.value.id)
    stopTimer()
    submitted.value = true
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
}

const {loading, error, reload} = useAsyncLoader(async () => {
  let detail: QuizAttemptDetail
  try {
    const existing = await quiz.getMyAttempt(testId.value)
    if (existing.attempt && existing.attempt.id) {
      detail = existing
    } else {
      detail = await quiz.startAttempt(testId.value)
    }
  } catch {
    detail = await quiz.startAttempt(testId.value)
  }
  attemptDetail.value = detail

  if (detail.attempt.submittedAt) {
    submitted.value = true
  }

  const testDetail = await quiz.getTest(testId.value)
  test.value = testDetail.test

  const qMap = new Map<number, QuizQuestion>()
  const questionIds = [...new Set(detail.questions.map(q => q.questionId))]
  const questionPromises = questionIds.map(async (qId) => {
    try {
      const q = await quiz.getQuestion(qId)
      qMap.set(qId, q)
    } catch { void 0 }
  })
  await Promise.all(questionPromises)
  questionData.value = qMap

  for (const ans of detail.answers) {
    answers.value.set(ans.questionId, ans.answer)
  }

  for (const aq of detail.questions) {
    if (!answers.value.has(aq.questionId)) {
      const q = qMap.get(aq.questionId)
      if (q) {
        const config = q.config ?? {}
        if (q.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE) {
          answers.value.set(aq.questionId, JSON.stringify({ selected: [] }))
        } else if (q.quizQuestionType === QuizQuestionTypes.ORDERING) {
          const items = (config.items as string[]) ?? []
          answers.value.set(aq.questionId, JSON.stringify({ order: shuffle(items.map((_: string, i: number) => i)) }))
        } else if (q.quizQuestionType === QuizQuestionTypes.TRUE_FALSE) {
          answers.value.set(aq.questionId, JSON.stringify({ value: null }))
        } else if (q.quizQuestionType === QuizQuestionTypes.CONNECT) {
          answers.value.set(aq.questionId, JSON.stringify({ pairs: {} }))
        } else if (q.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK) {
          answers.value.set(aq.questionId, JSON.stringify({ gaps: {} }))
        } else {
          answers.value.set(aq.questionId, JSON.stringify({ text: '' }))
        }
      }
    }
  }

  if (!submitted.value) {
    startTimer()
  }
}, {autoLoad: loaded.value})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})

onUnmounted(() => {
  stopTimer()
  if (saveDebounce) clearTimeout(saveDebounce)
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <TestResultSummary v-if="!loading && submitted" />

      <TestActivePhase
        v-if="!loading && !submitted && currentQuestionDetail"
        :sorted-questions="sortedQuestions"
        :current-index="currentIndex"
        :total-questions="totalQuestions"
        :answers="answers"
        :timer-display="timerDisplay"
        :timer-expired="timerExpired"
        :is-first-question="isFirstQuestion"
        :is-last-question="isLastQuestion"
        :question-detail="currentQuestionDetail"
        :config="currentConfig"
        :answer-parsed="currentAnswerParsed"
        :connect-left-items="connectLeftItems"
        :connect-right-items="connectRightItems"
        :mc-display-order="mcDisplayOrder"
        @navigate="navigateToQuestion"
        @prev="goPrev"
        @next="goNext"
        @submit="confirmSubmit"
        @set-m-c-answer="setMCAnswer"
        @set-fill-blank-gap="setFillBlankGap"
        @set-free-answer="setFreeAnswer"
        @set-connect-pair="setConnectPair"
        @set-image-text-answer="setImageTextAnswer"
        @set-true-false="setTrueFalse"
        @reorder-items="reorderItems"
        @move-order-item="moveOrderItem"
      />

      <TestSubmitModal v-model="submitModalOpen" @confirm="doSubmit" />
    </div>
  </ViewContent>
</template>
