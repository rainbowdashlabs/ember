/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { QuizAttemptDetail, QuizQuestion, QuizTestAnswer } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'

import SectionLabel from '@/components/typography/SectionLabel.vue'

import { StationPermission } from '@/api/types'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission } = useSession()
const canReview = computed(() => hasPermission(StationPermission.TEST_REVIEW))

const testId = computed(() => Number(route.params.id))
const attemptId = computed(() => Number(route.params.attemptId))

const loading = ref(true)
const error = ref('')
const grading = ref(false)
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

  // Auto-save with debounce
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

  // Calculate points: correctGaps * pointsPerCorrect
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

async function finishGrading() {
  grading.value = true
  error.value = ''
  try {
    for (const [answerId, pts] of pointsOverrides.value.entries()) {
      await quiz.gradeAnswer(answerId, pts)
    }
    await quiz.gradeAttempt(attemptId.value)
    graded.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    grading.value = false
  }
}

function goBack() {
  router.push({ name: 'quiz-test-detail', params: { id: testId.value } })
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const detail = await quiz.getAttemptDetail(attemptId.value)
    attemptDetail.value = detail

    // Build questions map from enriched response
    if (detail.questionDetails) {
      for (const q of detail.questionDetails) {
        questionsMap.value.set(q.id, q)
      }
    }

    // Initialize points from existing graded answers
    for (const answer of detail.answers) {
      if (answer.points !== null) {
        pointsOverrides.value.set(answer.id, answer.points)
      }

      // For fill-in-the-blank, infer which gaps were marked correct based on stored points
      const q = questionsMap.value.get(answer.questionId)
      if (q && q.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK && answer.graded && answer.points !== null) {
        const cfg = parseConfig(q.config)
        const answers_list = (cfg.answers as string[]) ?? []
        const parsed = parseAnswer(answer.answer)
        const gaps = (parsed.gaps as Record<string, string>) ?? {}
        const correctGaps = new Set<number>()
        for (let i = 0; i < answers_list.length; i++) {
          const given = (gaps[String(i)] ?? '').trim()
          if (given && given.toLowerCase() === answers_list[i].trim().toLowerCase()) {
            correctGaps.add(i)
          }
        }
        if (correctGaps.size > 0) gapCorrectOverrides.value.set(answer.id, correctGaps)
      }
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && attemptDetail">
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div>
            <SectionHeader>{{ t('quiz.evaluate.title') }}</SectionHeader>
            <MemberName :identity="attemptDetail.memberIdentity" size="md" class="mt-1" />
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">
              {{ totalPoints }} / {{ maxPoints }} {{ t('quiz.points') }}
            </span>
          </div>
        </div>

        <SuccessContainer v-if="graded">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'check-circle']" class="text-lg" />
            <span>{{ t('quiz.evaluate.gradedSuccess') }}</span>
          </div>
        </SuccessContainer>

        <div class="space-y-4">
          <NeutralContainer
            v-for="(aq, index) in attemptDetail.questions"
            :key="aq.id"
          >
            <template v-if="questionsMap.get(aq.questionId)">
              <div class="space-y-3">
                <!-- Question header -->
                <div>
                  <div class="flex items-center gap-2 mb-1">
                    <SectionLabel>
                      {{ index + 1 }}.
                      {{ t(`quiz.questionTypes.${questionsMap.get(aq.questionId)!.quizQuestionType}`) }}
                    </SectionLabel>
                    <span class="text-xs text-(--text-muted)">
                      ({{ questionsMap.get(aq.questionId)!.points }} {{ t('quiz.points') }})
                    </span>
                    <SuccessBadge v-if="getAnswerForQuestion(aq.questionId)?.graded">
                      {{ getPointsForAnswer(getAnswerForQuestion(aq.questionId)!) }}P
                    </SuccessBadge>
                  </div>
                  <p class="font-medium">{{ questionsMap.get(aq.questionId)!.title }}</p>
                </div>

                <!-- MC: show options with correct/wrong highlighting -->
                <template v-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE">
                  <div class="space-y-1">
                    <div
                      v-for="(opt, oi) in (parseConfig(questionsMap.get(aq.questionId)!.config).options as { text: string; correct: boolean }[] ?? [])"
                      :key="oi"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs"
                      :class="{
                        'bg-success/10 border border-success/30': opt.correct && (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi),
                        'bg-success/5 border border-success/20': opt.correct && !(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi),
                        'bg-error/10 border border-error/30': !opt.correct && (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi),
                        'border border-bg-light-accent dark:border-bg-dark-accent': !opt.correct && !(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi),
                      }"
                    >
                      <font-awesome-icon
                        :icon="['fas', (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi) ? 'square-check' : 'square']"
                        :class="opt.correct ? 'text-success' : (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').selected as number[] ?? []).includes(oi) ? 'text-error' : 'text-(--text-muted)'"
                      />
                      <span>{{ opt.text }}</span>
                      <SuccessBadge v-if="opt.correct" class="ml-auto text-xs">{{ t('quiz.evaluate.correct') }}</SuccessBadge>
                    </div>
                  </div>
                </template>

                <!-- TRUE_FALSE -->
                <template v-else-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.TRUE_FALSE">
                  <div class="space-y-2 text-sm">
                    <div class="flex items-center gap-2">
                      <span class="text-(--text-muted)">{{ t('quiz.evaluate.correctAnswer') }}:</span>
                      <span class="font-medium">{{ (parseConfig(questionsMap.get(aq.questionId)!.config).correctAnswer as boolean) ? t('quiz.attempt.true') : t('quiz.attempt.false') }}</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <span class="text-(--text-muted)">{{ t('quiz.evaluate.studentAnswer') }}:</span>
                      <span class="font-medium" :class="(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').value as boolean) === (parseConfig(questionsMap.get(aq.questionId)!.config).correctAnswer as boolean) ? 'text-success' : 'text-error'">
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').value) === true ? t('quiz.attempt.true') : (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').value) === false ? t('quiz.attempt.false') : t('quiz.noAnswer') }}
                      </span>
                      <font-awesome-icon
                        :icon="['fas', (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').value as boolean) === (parseConfig(questionsMap.get(aq.questionId)!.config).correctAnswer as boolean) ? 'check' : 'xmark']"
                        :class="(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').value as boolean) === (parseConfig(questionsMap.get(aq.questionId)!.config).correctAnswer as boolean) ? 'text-success' : 'text-error'"
                      />
                    </div>
                  </div>
                </template>

                <!-- CONNECT -->
                <template v-else-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.CONNECT">
                  <div class="space-y-1">
                    <div
                      v-for="(pair, pi) in (parseConfig(questionsMap.get(aq.questionId)!.config).pairs as { left: string; right: string }[] ?? [])"
                      :key="pi"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs border"
                      :class="(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').pairs as Record<string, string> ?? {})[String(pi)] === pair.right
                        ? 'border-success/30 bg-success/10'
                        : 'border-error/30 bg-error/10'"
                    >
                      <span class="font-medium">{{ pair.left }}</span>
                      <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
                      <span
                        :class="(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').pairs as Record<string, string> ?? {})[String(pi)] === pair.right
                          ? 'text-success'
                          : 'text-error'"
                      >
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').pairs as Record<string, string> ?? {})[String(pi)] || '—' }}
                      </span>
                      <template v-if="(parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').pairs as Record<string, string> ?? {})[String(pi)] !== pair.right">
                        <span class="text-xs text-(--text-muted)">→</span>
                        <span class="text-success text-xs">{{ pair.right }}</span>
                      </template>
                    </div>
                  </div>
                </template>

                <!-- ORDERING -->
                <template v-else-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.ORDERING">
                  <div class="space-y-1">
                    <div
                      v-for="(itemIdx, pos) in (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').order as number[] ?? [])"
                      :key="pos"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-xs border"
                      :class="itemIdx === pos ? 'border-success/30 bg-success/10' : 'border-error/30 bg-error/10'"
                    >
                      <span class="text-xs text-(--text-muted) w-5">{{ pos + 1 }}.</span>
                      <span>{{ ((parseConfig(questionsMap.get(aq.questionId)!.config).items as string[]) ?? [])[itemIdx] ?? `#${itemIdx}` }}</span>
                      <template v-if="itemIdx !== pos">
                        <span class="ml-auto text-xs text-success">
                          → {{ ((parseConfig(questionsMap.get(aq.questionId)!.config).items as string[]) ?? [])[pos] }}
                        </span>
                      </template>
                    </div>
                  </div>
                </template>

                <!-- FILL_IN_THE_BLANK -->
                <template v-else-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
                  <div v-if="parseConfig(questionsMap.get(aq.questionId)!.config).text" class="text-sm text-(--text-muted) whitespace-pre-wrap mb-2">
                    {{ parseConfig(questionsMap.get(aq.questionId)!.config).text }}
                  </div>
                  <div class="space-y-2">
                    <div
                      v-for="(correctAns, gapIdx) in ((parseConfig(questionsMap.get(aq.questionId)!.config).answers as string[]) ?? [])"
                      :key="gapIdx"
                      class="flex items-center gap-2 px-3 py-2 rounded border text-sm"
                      :class="getAnswerForQuestion(aq.questionId) && isGapCorrect(getAnswerForQuestion(aq.questionId)!.id, gapIdx)
                        ? 'border-success/30 bg-success/10'
                        : 'border-bg-light-accent dark:border-bg-dark-accent'"
                    >
                      <span class="text-xs text-(--text-muted) w-5 shrink-0">{{ gapIdx + 1 }}.</span>
                      <span class="flex-1">
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').gaps as Record<string, string> ?? {})[String(gapIdx)] || '—' }}
                      </span>
                      <span class="text-xs text-success shrink-0">{{ correctAns }}</span>
                      <IconButton
                        v-if="getAnswerForQuestion(aq.questionId)"
                        :icon="['fas', isGapCorrect(getAnswerForQuestion(aq.questionId)!.id, gapIdx) ? 'check-circle' : 'circle']"
                        :label="t('quiz.evaluate.correct')"
                        :class="isGapCorrect(getAnswerForQuestion(aq.questionId)!.id, gapIdx) ? 'text-success' : 'text-(--text-muted) hover:text-success'"
                        @click="toggleGapCorrect(getAnswerForQuestion(aq.questionId)!.id, gapIdx, aq.questionId)"
                      />
                    </div>
                  </div>
                </template>

                <!-- FREE_ANSWER / IMAGE_TEXT / ENUMERATION -->
                <template v-else>
                  <div class="space-y-2">
                    <div>
                      <FieldLabel hint class="mb-1">{{ t('quiz.evaluate.studentAnswer') }}</FieldLabel>
                      <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent whitespace-pre-wrap">
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').text as string) || t('quiz.noAnswer') }}
                      </p>
                    </div>
                    <div v-if="questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.FREE_ANSWER || questionsMap.get(aq.questionId)!.quizQuestionType === QuizQuestionTypes.ENUMERATION">
                      <label class="text-xs font-semibold text-success block mb-1">{{ t('quiz.evaluate.sampleAnswers') }}</label>
                      <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">
                        {{ ((parseConfig(questionsMap.get(aq.questionId)!.config).answers as string[]) ?? []).join(', ') || '—' }}
                      </p>
                    </div>
                    <div v-if="getAnswerForQuestion(aq.questionId)" class="flex items-center gap-2">
                      <IconButton
                        :icon="['fas', 'check']"
                        :label="t('quiz.evaluate.markCorrect')"
                        class="text-success hover:bg-success/10 rounded px-2 py-1"
                        @click="markCorrect(getAnswerForQuestion(aq.questionId)!.id, questionsMap.get(aq.questionId)!.points)"
                      />
                      <IconButton
                        :icon="['fas', 'xmark']"
                        :label="t('quiz.evaluate.markWrong')"
                        class="text-error hover:bg-error/10 rounded px-2 py-1"
                        @click="markWrong(getAnswerForQuestion(aq.questionId)!.id, questionsMap.get(aq.questionId)!.points)"
                      />
                    </div>
                  </div>
                </template>

                <!-- Points -->
                <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent flex-wrap">
                  <label class="text-sm font-medium shrink-0">{{ t('quiz.evaluate.points') }}:</label>
                  <div v-if="canReview" class="flex items-center gap-1">
                    <DecimalInput
                      :model-value="getAnswerForQuestion(aq.questionId)
                        ? getPointsForAnswer(getAnswerForQuestion(aq.questionId)!)
                        : 0"
                      step="0.5"
                      class="w-20"
                      @update:model-value="(v: number | undefined) => {
                        const answer = getAnswerForQuestion(aq.questionId)
                        if (answer) setPoints(answer.id, questionsMap.get(aq.questionId)!.points, v)
                      }"
                    />
                    <span class="text-sm text-(--text-muted) shrink-0">/ {{ questionsMap.get(aq.questionId)!.points }}</span>
                  </div>
                  <span v-else class="text-sm font-mono">{{ getAnswerForQuestion(aq.questionId) ? getPointsForAnswer(getAnswerForQuestion(aq.questionId)!) : 0 }} / {{ questionsMap.get(aq.questionId)!.points }}</span>
                </div>
              </div>
            </template>
          </NeutralContainer>
        </div>

        <!-- Summary and actions -->
        <NeutralContainer>
          <div class="flex items-center justify-between flex-wrap gap-4">
            <div class="text-lg font-semibold">
              {{ t('quiz.evaluate.total') }}: {{ totalPoints }} / {{ maxPoints }} {{ t('quiz.points') }}
            </div>
            <div class="flex gap-3">
              <SecondaryButton @click="goBack">{{ t('common.back') }}</SecondaryButton>
              <SuccessButton v-if="canReview" :disabled="grading || graded" @click="finishGrading">
                <Spinner v-if="grading" size="sm" />
                <template v-else>{{ t('quiz.evaluate.finishGrading') }}</template>
              </SuccessButton>
            </div>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
