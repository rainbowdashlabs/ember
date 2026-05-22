/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DragList from '@/components/input/DragList.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import { QuizQuestionTypes } from '@/api/types'
import type { QuizAttemptDetail, QuizTest, QuizQuestion } from '@/api/types'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const testId = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const attemptDetail = ref<QuizAttemptDetail | null>(null)
const test = ref<QuizTest | null>(null)
const questionData = ref<Map<number, QuizQuestion>>(new Map())

// Navigation
const currentIndex = ref(0)

// Answers: questionId -> answer string (JSON serialized)
const answers = ref<Map<number, string>>(new Map())

// Timer
const timerSeconds = ref(0)
const timerInterval = ref<ReturnType<typeof setInterval> | null>(null)

// Submission
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

const currentConfig = computed(() => {
  if (!currentQuestionDetail.value) return {}
  try { return JSON.parse(currentQuestionDetail.value.config || '{}') } catch { return {} }
})

// Connect items — support both sanitized (leftItems/rightItems) and full (pairs) config
const connectLeftItems = computed<string[]>(() => {
  const cfg = currentConfig.value
  if (cfg.leftItems) return cfg.leftItems as string[]
  if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.left)
  return []
})
const connectRightItems = computed<string[]>(() => {
  const cfg = currentConfig.value
  if (cfg.rightItems) return cfg.rightItems as string[]
  if (cfg.pairs) return (cfg.pairs as { left: string; right: string }[]).map(p => p.right)
  return []
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

// Timer display
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

// Answer helpers per question type

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

  currentAnswer.value = JSON.stringify({ ...parsed, selected })
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

// Auto-save
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
      // silent — will retry on next change
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

  // Save current answer first
  if (currentQuestion.value) {
    const answerStr = answers.value.get(currentQuestion.value.questionId) ?? ''
    if (answerStr) {
      try {
        await quiz.saveAnswer(attempt.value.id, currentQuestion.value.questionId, answerStr)
      } catch { /* continue submit */ }
    }
  }

  try {
    await quiz.submitAttempt(attempt.value.id)
    stopTimer()
    submitted.value = true
  } catch {
    error.value = t('common.error')
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    // Start or resume attempt
    let detail: QuizAttemptDetail
    try {
      const existing = await quiz.getMyAttempt(testId.value)
      // Backend returns empty attempt object when no attempt exists
      if (existing.attempt && existing.attempt.id) {
        detail = existing
      } else {
        detail = await quiz.startAttempt(testId.value)
      }
    } catch {
      detail = await quiz.startAttempt(testId.value)
    }
    attemptDetail.value = detail

    // Check if already submitted
    if (detail.attempt.submittedAt) {
      submitted.value = true
    }

    // Load test info
    const testDetail = await quiz.getTest(testId.value)
    test.value = testDetail.test

    // Load question details for each attempt question
    const qMap = new Map<number, QuizQuestion>()
    const questionIds = [...new Set(detail.questions.map(q => q.questionId))]
    const questionPromises = questionIds.map(async (qId) => {
      try {
        const q = await quiz.getQuestion(qId)
        qMap.set(qId, q)
      } catch { /* skip */ }
    })
    await Promise.all(questionPromises)
    questionData.value = qMap

    // Restore existing answers
    for (const ans of detail.answers) {
      answers.value.set(ans.questionId, ans.answer)
    }

    // Initialize default answers for questions without existing answers
    for (const aq of detail.questions) {
      if (!answers.value.has(aq.questionId)) {
        const q = qMap.get(aq.questionId)
        if (q) {
          const config = (() => { try { return JSON.parse(q.config || '{}') } catch { return {} } })()
          if (q.questionType === QuizQuestionTypes.MULTIPLE_CHOICE) {
            answers.value.set(aq.questionId, JSON.stringify({ selected: [] }))
          } else if (q.questionType === QuizQuestionTypes.ORDERING) {
            const items = (config.items as string[]) ?? []
            answers.value.set(aq.questionId, JSON.stringify({ order: items.map((_: string, i: number) => i) }))
          } else if (q.questionType === QuizQuestionTypes.TRUE_FALSE) {
            answers.value.set(aq.questionId, JSON.stringify({ value: null }))
          } else if (q.questionType === QuizQuestionTypes.CONNECT) {
            answers.value.set(aq.questionId, JSON.stringify({ pairs: {} }))
          } else if (q.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK) {
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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
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

      <!-- Result summary after submission -->
      <template v-if="!loading && submitted">
        <SuccessContainer>
          <div class="text-center space-y-4 py-4">
            <font-awesome-icon :icon="['fas', 'check-circle']" class="text-4xl text-success" />
            <SectionHeader>{{ t('quiz.attempt.submitted') }}</SectionHeader>
            <p class="text-sm text-(--text-muted)">{{ t('quiz.attempt.submittedInfo') }}</p>
            <PrimaryButton @click="router.push({ name: 'quiz-test-detail', params: { id: testId } })">
              {{ t('quiz.attempt.backToTest') }}
            </PrimaryButton>
          </div>
        </SuccessContainer>
      </template>

      <!-- Active test taking -->
      <template v-if="!loading && !submitted && currentQuestionDetail">
        <!-- Timer and progress bar -->
        <div class="flex items-center justify-between">
          <span class="text-sm text-(--text-muted)">
            {{ t('quiz.attempt.questionProgress', { current: currentIndex + 1, total: totalQuestions }) }}
          </span>
          <span v-if="timerDisplay" class="text-sm font-mono font-bold" :class="timerExpired ? 'text-error' : 'text-(--text)'">
            <font-awesome-icon :icon="['fas', 'clock']" class="mr-1" />
            {{ timerDisplay }}
          </span>
        </div>

        <!-- Progress dots -->
        <div class="flex flex-wrap gap-1">
          <button
            v-for="(q, idx) in sortedQuestions"
            :key="q.id"
            class="w-7 h-7 rounded-full text-xs font-medium transition-colors"
            :class="{
              'bg-primary text-white': idx === currentIndex,
              'bg-primary/20 text-primary': idx !== currentIndex && answers.has(q.questionId) && answers.get(q.questionId) !== '',
              'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': idx !== currentIndex && (!answers.has(q.questionId) || answers.get(q.questionId) === ''),
            }"
            @click="navigateToQuestion(idx)"
          >
            {{ idx + 1 }}
          </button>
        </div>

        <!-- Question card -->
        <NeutralContainer>
          <div class="space-y-4">
            <div>
              <span class="text-xs font-semibold text-(--text-muted) uppercase">
                {{ t(`quiz.questionTypes.${currentQuestionDetail.questionType}`) }}
              </span>
              <h3 class="font-medium text-lg mt-1">{{ currentQuestionDetail.title }}</h3>
              <p v-if="currentQuestionDetail.description" class="text-sm text-(--text-muted) mt-1">
                {{ currentQuestionDetail.description }}
              </p>
            </div>

            <!-- MULTIPLE_CHOICE -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.MULTIPLE_CHOICE">
              <div class="space-y-2">
                <div
                  v-for="(opt, oi) in (currentConfig.options as { text: string }[] ?? [])"
                  :key="oi"
                  class="flex items-center gap-3 px-3 py-2 rounded-lg border cursor-pointer transition-all"
                  :class="(currentAnswerParsed.selected ?? []).includes(oi)
                    ? 'border-primary bg-primary/10'
                    : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary'"
                  @click="setMCAnswer(oi, true)"
                >
                  <font-awesome-icon
                    :icon="['fas', (currentAnswerParsed.selected ?? []).includes(oi) ? 'square-check' : 'square']"
                    :class="(currentAnswerParsed.selected ?? []).includes(oi) ? 'text-primary' : 'text-(--text-muted)'"
                  />
                  <span class="text-sm">{{ opt.text }}</span>
                </div>
              </div>
            </template>

            <!-- FILL_IN_THE_BLANK -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
              <div class="space-y-2">
                <template v-if="(currentConfig.useDropdown as boolean) && (currentConfig.wordBank as string[] ?? []).length > 0">
                  <!-- Dropdown mode: show text with selects in gaps -->
                  <div class="text-sm leading-relaxed" v-if="currentConfig.text">
                    <template v-for="(part, pi) in (currentConfig.text as string || '').split('___')" :key="pi">
                      <span>{{ part }}</span>
                      <SelectInput v-if="pi < (currentConfig.gapCount as number ?? 0)"
                                   :model-value="(currentAnswerParsed.gaps ?? {} as Record<string, string>)[String(pi)] ?? ''"
                                   class="inline-block mx-1 w-auto"
                                   @update:model-value="(v: string | undefined) => setFillBlankGap(pi, v ?? '')">
                        <option value="">...</option>
                        <option v-for="word in (currentConfig.wordBank as string[])" :key="word" :value="word">{{ word }}</option>
                      </SelectInput>
                    </template>
                  </div>
                </template>
                <template v-else>
                  <!-- Per-gap text inputs -->
                  <div v-for="gi in (currentConfig.gapCount as number ?? 1)" :key="gi" class="flex items-center gap-2">
                    <span class="text-xs text-(--text-muted) w-8 shrink-0">{{ t('quiz.attempt.gap') }} {{ gi }}:</span>
                    <TextInput
                      :model-value="(currentAnswerParsed.gaps ?? {} as Record<string, string>)[String(gi - 1)] ?? ''"
                      :placeholder="t('quiz.attempt.fillBlankPlaceholder')"
                      @update:model-value="(v: string | undefined) => setFillBlankGap(gi - 1, v ?? '')"
                    />
                  </div>
                </template>
              </div>
            </template>

            <!-- FREE_ANSWER -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.FREE_ANSWER">
              <TextAreaInput
                :model-value="currentAnswerParsed.text ?? ''"
                :placeholder="t('quiz.attempt.freeAnswerPlaceholder')"
                @update:model-value="(v: string | undefined) => setFreeAnswer(v ?? '')"
              />
            </template>

            <!-- CONNECT -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.CONNECT">
              <div class="space-y-2">
                <div
                  v-for="(left, li) in connectLeftItems"
                  :key="li"
                  class="flex items-center gap-3 p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent"
                >
                  <span class="text-sm font-medium flex-1">{{ left }}</span>
                  <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
                  <SelectInput
                    :model-value="(currentAnswerParsed.pairs ?? {})[String(li)] ?? ''"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => setConnectPair(li, v ?? '')"
                  >
                    <option value="">--</option>
                    <option v-for="(right, ri) in connectRightItems" :key="ri" :value="right">
                      {{ right }}
                    </option>
                  </SelectInput>
                </div>
              </div>
            </template>

            <!-- IMAGE_TEXT -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.IMAGE_TEXT">
              <div class="space-y-3">
                <img
                  v-if="currentQuestionDetail.imageUrl"
                  :src="currentQuestionDetail.imageUrl"
                  :alt="currentQuestionDetail.title"
                  class="max-w-full rounded border border-bg-light-accent dark:border-bg-dark-accent"
                />
                <TextAreaInput
                  :model-value="currentAnswerParsed.text ?? ''"
                  :placeholder="t('quiz.attempt.imageTextPlaceholder')"
                  @update:model-value="(v: string | undefined) => setImageTextAnswer(v ?? '')"
                />
              </div>
            </template>

            <!-- TRUE_FALSE -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.TRUE_FALSE">
              <div class="flex gap-4">
                <label
                  class="flex-1 flex items-center justify-center gap-2 p-4 rounded border cursor-pointer transition-colors"
                  :class="currentAnswerParsed.value === true
                    ? 'border-success bg-success/10'
                    : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-success/50'"
                  @click="setTrueFalse(true)"
                >
                  <input type="radio" :checked="currentAnswerParsed.value === true" class="h-4 w-4 accent-success" @click.stop @change="setTrueFalse(true)" />
                  <span class="font-medium">{{ t('quiz.attempt.true') }}</span>
                </label>
                <label
                  class="flex-1 flex items-center justify-center gap-2 p-4 rounded border cursor-pointer transition-colors"
                  :class="currentAnswerParsed.value === false
                    ? 'border-error bg-error/10'
                    : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-error/50'"
                  @click="setTrueFalse(false)"
                >
                  <input type="radio" :checked="currentAnswerParsed.value === false" class="h-4 w-4 accent-error" @click.stop @change="setTrueFalse(false)" />
                  <span class="font-medium">{{ t('quiz.attempt.false') }}</span>
                </label>
              </div>
            </template>

            <!-- ORDERING -->
            <template v-if="currentQuestionDetail.questionType === QuizQuestionTypes.ORDERING">
              <div class="space-y-1">
                <DragList
                  :items="(currentAnswerParsed.order as number[] ?? [])"
                  :key-fn="(_item: number, index: number) => index"
                  @reorder="reorderItems"
                >
                  <template #default="{ item, index }: { item: number; index: number }">
                    <div class="flex items-center gap-2 px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent mb-1 cursor-grab bg-(--bg)">
                      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted)" />
                      <span class="text-xs text-(--text-muted) w-5">{{ index + 1 }}.</span>
                      <span class="flex-1 text-sm">{{ (currentConfig.items as string[] ?? [])[item] ?? `#${item}` }}</span>
                      <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')" class="text-(--text-muted) hover:text-primary" @click="moveOrderItem(index, -1)" />
                      <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')" class="text-(--text-muted) hover:text-primary" @click="moveOrderItem(index, 1)" />
                    </div>
                  </template>
                </DragList>
              </div>
            </template>
          </div>
        </NeutralContainer>

        <!-- Navigation buttons -->
        <div class="flex items-center justify-between">
          <SecondaryButton :disabled="isFirstQuestion" @click="goPrev">
            <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
            {{ t('quiz.attempt.prev') }}
          </SecondaryButton>

          <SuccessButton @click="confirmSubmit">
            <font-awesome-icon :icon="['fas', 'paper-plane']" class="mr-1" />
            {{ t('quiz.attempt.submit') }}
          </SuccessButton>

          <SecondaryButton :disabled="isLastQuestion" @click="goNext">
            {{ t('quiz.attempt.next') }}
            <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
          </SecondaryButton>
        </div>
      </template>

      <!-- Submit confirmation modal -->
      <Modal v-model="submitModalOpen">
        <div class="space-y-4">
          <SectionHeader>{{ t('quiz.attempt.confirmSubmitTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('quiz.attempt.confirmSubmitMessage') }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="submitModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
            <SuccessButton @click="doSubmit">{{ t('quiz.attempt.submit') }}</SuccessButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
