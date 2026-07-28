/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TestResultSummary from './testtakeview/TestResultSummary.vue'
import TestActivePhase from './testtakeview/TestActivePhase.vue'
import TestSubmitModal from './testtakeview/TestSubmitModal.vue'
import { useQuizAnswers } from './testtakeview/useQuizAnswers'
import { useQuizTimer } from './testtakeview/useQuizTimer'
import { useQuizQuestionOptions } from './testtakeview/useQuizQuestionOptions'
import type { QuizAttemptDetail, QuizTest, QuizQuestion } from '@/api/quiz'
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

const attemptId = computed(() => attempt.value?.id ?? null)
const currentQuestionId = computed(() => currentQuestion.value?.questionId ?? null)
const timeLimit = computed(() => test.value?.timeLimit ?? null)
const attemptStartedAt = computed(() => attempt.value?.startedAt ?? null)

const { currentConfig, mcDisplayOrder, connectLeftItems, connectRightItems } =
  useQuizQuestionOptions(currentQuestion, currentQuestionDetail)

const {
  answers,
  currentAnswerParsed,
  hydrate: hydrateAnswers,
  autoSaveCurrentAnswer,
  saveAll: saveAllAnswers,
  setMCAnswer,
  setFillBlankGap,
  setFreeAnswer,
  setConnectPair,
  setImageTextAnswer,
  setTrueFalse,
  reorderItems,
  moveOrderItem,
} = useQuizAnswers(attemptId, currentQuestionId)

const { timerDisplay, timerExpired, startTimer, stopTimer } = useQuizTimer(timeLimit, attemptStartedAt, doSubmit)

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

function confirmSubmit() {
  submitModalOpen.value = true
}

async function doSubmit() {
  submitModalOpen.value = false
  if (!attempt.value || submitted.value) return

  await saveAllAnswers()

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

  hydrateAnswers(detail, qMap)

  if (!submitted.value) {
    startTimer()
  }
}, {autoLoad: loaded.value})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent :title="t('pages.quiz-test-take.title')" :subtitle="t('pages.quiz-test-take.subtitle')">
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
