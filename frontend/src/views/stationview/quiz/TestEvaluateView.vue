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
import NumberInput from '@/components/input/number/NumberInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type { QuizAttemptDetail, QuizQuestion, QuizTestAnswer, StationMember } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz, stationMembers } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const testId = computed(() => Number(route.params.id))
const attemptId = computed(() => Number(route.params.attemptId))

const loading = ref(true)
const error = ref('')
const grading = ref(false)
const graded = ref(false)

const attemptDetail = ref<QuizAttemptDetail | null>(null)
const questionsMap = ref<Map<number, QuizQuestion>>(new Map())
const pointsOverrides = ref<Map<number, number>>(new Map())
const member = ref<StationMember | null>(null)

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

function parseConfig(config: string): Record<string, unknown> {
  try { return JSON.parse(config || '{}') } catch { return {} }
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

    // Load member info
    try {
      member.value = await stationMembers.getMember(detail.attempt.memberId)
    } catch { /* skip */ }

    // Load questions
    const questionIds = new Set(detail.questions.map(q => q.questionId))
    const questions = await Promise.all(Array.from(questionIds).map(qId => quiz.getQuestion(qId)))
    for (const q of questions) {
      questionsMap.value.set(q.id, q)
    }

    // Initialize points from existing graded answers
    for (const answer of detail.answers) {
      if (answer.points !== null) {
        pointsOverrides.value.set(answer.id, answer.points)
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
            <h2 class="text-xl font-semibold">{{ t('quiz.evaluate.title') }}</h2>
            <p class="text-(--text-muted) text-sm mt-1">
              {{ member?.name ?? member?.email ?? `#${attemptDetail.attempt.memberId}` }}
            </p>
          </div>
          <div class="flex items-center gap-3">
            <span class="text-sm font-medium">
              {{ totalPoints }} / {{ maxPoints }} {{ t('quiz.points') }}
            </span>
          </div>
        </div>

        <SuccessContainer v-if="graded">
          <div class="flex items-center gap-3">
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
                    <span class="text-xs font-semibold text-(--text-muted) uppercase">
                      {{ index + 1 }}.
                      {{ t(`quiz.questionTypes.${questionsMap.get(aq.questionId)!.questionType}`) }}
                    </span>
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
                <template v-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.MULTIPLE_CHOICE">
                  <div class="space-y-1">
                    <div
                      v-for="(opt, oi) in (parseConfig(questionsMap.get(aq.questionId)!.config).options as { text: string; correct: boolean }[] ?? [])"
                      :key="oi"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-sm"
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
                <template v-else-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.TRUE_FALSE">
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
                <template v-else-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.CONNECT">
                  <div class="space-y-1">
                    <div
                      v-for="(pair, pi) in (parseConfig(questionsMap.get(aq.questionId)!.config).pairs as { left: string; right: string }[] ?? [])"
                      :key="pi"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-sm border"
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
                <template v-else-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.ORDERING">
                  <div class="space-y-1">
                    <div
                      v-for="(itemIdx, pos) in (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').order as number[] ?? [])"
                      :key="pos"
                      class="flex items-center gap-2 px-3 py-1.5 rounded text-sm border"
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
                <template v-else-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
                  <div class="space-y-2">
                    <div>
                      <label class="text-xs font-semibold text-(--text-muted) block mb-1">{{ t('quiz.evaluate.studentAnswer') }}</label>
                      <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').text as string) || t('quiz.noAnswer') }}
                      </p>
                    </div>
                    <div>
                      <label class="text-xs font-semibold text-success block mb-1">{{ t('quiz.evaluate.correctAnswer') }}</label>
                      <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">
                        {{ ((parseConfig(questionsMap.get(aq.questionId)!.config).answers as string[]) ?? []).join(', ') }}
                      </p>
                    </div>
                  </div>
                </template>

                <!-- FREE_ANSWER / IMAGE_TEXT -->
                <template v-else>
                  <div class="space-y-2">
                    <div>
                      <label class="text-xs font-semibold text-(--text-muted) block mb-1">{{ t('quiz.evaluate.studentAnswer') }}</label>
                      <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent whitespace-pre-wrap">
                        {{ (parseAnswer(getAnswerForQuestion(aq.questionId)?.answer ?? '{}').text as string) || t('quiz.noAnswer') }}
                      </p>
                    </div>
                    <div v-if="questionsMap.get(aq.questionId)!.questionType === QuizQuestionTypes.FREE_ANSWER">
                      <label class="text-xs font-semibold text-success block mb-1">{{ t('quiz.evaluate.sampleAnswers') }}</label>
                      <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">
                        {{ ((parseConfig(questionsMap.get(aq.questionId)!.config).answers as string[]) ?? []).join(', ') || '—' }}
                      </p>
                    </div>
                  </div>
                </template>

                <!-- Points input -->
                <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent flex-wrap">
                  <label class="text-sm font-medium shrink-0">{{ t('quiz.evaluate.points') }}:</label>
                  <div class="flex items-center gap-1">
                    <NumberInput
                      :model-value="getAnswerForQuestion(aq.questionId)
                        ? getPointsForAnswer(getAnswerForQuestion(aq.questionId)!)
                        : 0"
                      class="w-20"
                      @update:model-value="(v: number | undefined) => {
                        const answer = getAnswerForQuestion(aq.questionId)
                        if (answer) setPoints(answer.id, questionsMap.get(aq.questionId)!.points, v)
                      }"
                    />
                    <span class="text-sm text-(--text-muted) shrink-0">/ {{ questionsMap.get(aq.questionId)!.points }}</span>
                  </div>
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
              <SuccessButton :disabled="grading || graded" @click="finishGrading">
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
