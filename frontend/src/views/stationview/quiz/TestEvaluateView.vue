/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import {QuizAttemptStatus, QuizQuestionTypes, type QuizAttemptDetail, type QuizQuestion, type QuizTestAnswer} from '@/api/quiz'
import {StationPermission} from '@/api/types'
import {quiz} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import EvaluationHeader from './testevaluateview/EvaluationHeader.vue'
import EvaluationSummary from './testevaluateview/EvaluationSummary.vue'
import QuestionEvaluationList from './testevaluateview/QuestionEvaluationList.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission} = useSession()
const canReview = computed(() => hasPermission(StationPermission.TEST_REVIEW))

const testId = computed(() => Number(route.params.id))
const attemptId = computed(() => Number(route.params.attemptId))

const graded = ref(false)

const attemptDetail = ref<QuizAttemptDetail | null>(null)
const questionsMap = ref<Map<number, QuizQuestion>>(new Map())
const pointsOverrides = ref<Map<number, number>>(new Map())

const totalPoints = computed(() => {
  if (!attemptDetail.value) return 0
  let sum = 0
  for (const answer of attemptDetail.value.answers) {
    const override = pointsOverrides.value.get(answer.id)
    if (override !== undefined) sum += override
    else if (answer.points !== null) sum += answer.points
  }
  return sum
})

const maxPoints = computed(() => {
  if (!attemptDetail.value) return 0
  let sum = 0
  for (const aq of attemptDetail.value.questions) {
    const q = questionsMap.value.get(aq.questionId)
    if (q) sum += q.points
  }
  return sum
})

function parseConfig(config: Record<string, unknown>): Record<string, unknown> {
  return config ?? {}
}

function parseAnswer(answer: string): Record<string, unknown> {
  try { return JSON.parse(answer || '{}') } catch { return {} }
}

function getAnswerForQuestion(questionId: number): QuizTestAnswer | undefined {
  return attemptDetail.value?.answers.find(a => a.questionId === questionId)
}

function getPointsForAnswer(answer: QuizTestAnswer): number {
  const override = pointsOverrides.value.get(answer.id)
  if (override !== undefined) return override
  return answer.points ?? 0
}

let saveDebounce: ReturnType<typeof setTimeout> | null = null

function setPoints(answerId: number, maxPts: number, value: number | undefined) {
  const clamped = value === undefined ? 0 : Math.max(0, Math.min(value, maxPts))
  pointsOverrides.value.set(answerId, clamped)

  if (saveDebounce) clearTimeout(saveDebounce)
  saveDebounce = setTimeout(() => {
    quiz.gradeAnswer(answerId, clamped).catch(() => {})
  }, 500)
}

function markCorrect(answerId: number, maxPts: number) {
  setPoints(answerId, maxPts, maxPts)
}

function markWrong(answerId: number, maxPts: number) {
  setPoints(answerId, maxPts, 0)
}

const gapCorrectOverrides = ref<Map<number, Set<number>>>(new Map())

function toggleGapCorrect(answerId: number, gapIndex: number, questionId: number) {
  const current = gapCorrectOverrides.value.get(answerId) ?? new Set()
  const next = new Set(current)
  if (next.has(gapIndex)) next.delete(gapIndex)
  else next.add(gapIndex)
  gapCorrectOverrides.value.set(answerId, next)

  const q = questionsMap.value.get(questionId)
  if (!q) return
  const cfg = parseConfig(q.config)
  const ppc = (cfg.pointsPerCorrect as number) || 1
  const points = next.size * ppc
  setPoints(answerId, q.points, points)
}

function isGapCorrect(answerId: number, gapIndex: number): boolean {
  return (gapCorrectOverrides.value.get(answerId) ?? new Set()).has(gapIndex)
}

const {running: grading, error: gradeError, run: finishGrading} = useAsyncAction(
    async () => {
      for (const [answerId, pts] of pointsOverrides.value.entries()) {
        await quiz.gradeAnswer(answerId, pts)
      }
      await quiz.gradeAttempt(attemptId.value)
      graded.value = true

      try {
        const all = await quiz.listAttempts(testId.value)
        const next = all
          .filter(a => a.id !== attemptId.value && a.status !== QuizAttemptStatus.GRADED && a.status !== QuizAttemptStatus.IN_PROGRESS)
          .sort((a, b) => {
            const aT = a.submittedAt ? new Date(a.submittedAt).getTime() : 0
            const bT = b.submittedAt ? new Date(b.submittedAt).getTime() : 0
            return aT - bT
          })[0]
        if (next) {
          router.push({name: 'quiz-test-evaluate', params: {id: testId.value, attemptId: next.id}})
          return
        }
        router.push({name: 'quiz-test-detail', params: {id: testId.value}})
      } catch {
        return
      }
    },
    {formatError: () => t('common.error')},
)

function goBack() {
  router.push({name: 'quiz-test-detail', params: {id: testId.value}})
}

const {loading, error, reload} = useAsyncLoader(async () => {
  const detail = await quiz.getAttemptDetail(attemptId.value)
  attemptDetail.value = detail

  if (detail.questionDetails) {
    for (const q of detail.questionDetails) {
      questionsMap.value.set(q.id, q)
    }
  }

  for (const answer of detail.answers) {
    if (answer.points !== null) {
      pointsOverrides.value.set(answer.id, answer.points)
    }

    const q = questionsMap.value.get(answer.questionId)
    if (q && q.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK && answer.graded && answer.points !== null) {
      const cfg = parseConfig(q.config)
      const answers_list = (cfg.answers as string[]) ?? []
      const parsed = parseAnswer(answer.answer)
      const gaps = (parsed.gaps as Record<string, string>) ?? {}
      const correctGaps = new Set<number>()
      for (let i = 0; i < answers_list.length; i++) {
        const given = (gaps[String(i)] ?? '').trim()
        const expected = answers_list[i]?.trim() ?? ''
        if (given && given.toLowerCase() === expected.toLowerCase()) {
          correctGaps.add(i)
        }
      }
      if (correctGaps.size > 0) gapCorrectOverrides.value.set(answer.id, correctGaps)
    }
  }
})

watch(attemptId, async (newId, oldId) => {
  if (newId === oldId) return
  attemptDetail.value = null
  questionsMap.value = new Map()
  pointsOverrides.value = new Map()
  gapCorrectOverrides.value = new Map()
  graded.value = false
  await reload()
})

function pointsForQuestion(aq: { questionId: number }): number {
  const answer = getAnswerForQuestion(aq.questionId)
  return answer ? getPointsForAnswer(answer) : 0
}
</script>

<template>
  <ViewContent :title="t('pages.quiz-test-evaluate.title')" :subtitle="t('pages.quiz-test-evaluate.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error || gradeError" variant="error">{{ error || gradeError }}</Alert>

      <template v-if="!loading && attemptDetail">
        <EvaluationHeader
          :attempt="attemptDetail"
          :total-points="totalPoints"
          :max-points="maxPoints"
        />

        <SuccessContainer v-if="graded">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'check-circle']" class="text-lg" />
            <span>{{ t('quiz.evaluate.gradedSuccess') }}</span>
          </div>
        </SuccessContainer>

        <QuestionEvaluationList
          :attempt="attemptDetail"
          :questions-map="questionsMap"
          :can-review="canReview"
          :get-answer="getAnswerForQuestion"
          :get-points="pointsForQuestion"
          :is-gap-correct="isGapCorrect"
          @toggle-gap="toggleGapCorrect"
          @mark-correct="markCorrect"
          @mark-wrong="markWrong"
          @update-points="setPoints"
        />

        <EvaluationSummary
          :total-points="totalPoints"
          :max-points="maxPoints"
          :can-review="canReview"
          :grading="grading"
          :graded="graded"
          @back="goBack"
          @finish="finishGrading"
        />
      </template>
    </div>
  </ViewContent>
</template>
