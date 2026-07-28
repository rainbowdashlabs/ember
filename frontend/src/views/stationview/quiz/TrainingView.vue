/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TrainingCatalogSelect from './trainingview/TrainingCatalogSelect.vue'
import TrainingActiveSession from './trainingview/TrainingActiveSession.vue'
import TrainingFinished from './trainingview/TrainingFinished.vue'
import type { QuizCatalog, QuizQuestion } from '@/api/quiz'
import { QuizQuestionTypes } from '@/api/quiz'
import { quiz } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()

const phase = ref<'select' | 'training' | 'finished'>('select')
const { config: catalogs, loading, error } = useConfigPanel<QuizCatalog[]>({
  initial: [],
  fetch: () => quiz.listTrainingCatalogs(),
})
const selectedCatalogIds = ref<Set<number>>(new Set())
const questions = ref<QuizQuestion[]>([])
const currentIndex = ref(0)
const showAnswer = ref(false)
const userAnswer = ref('')
const userMcSelections = ref<Set<number>>(new Set())
const userTfAnswer = ref<boolean | null>(null)
const userOrderItems = ref<number[]>([])
const userConnectPairs = ref<Record<string, string>>({})
const userFillGaps = ref<Record<string, string>>({})
const mcDisplayOrder = ref<number[]>([])
const connectRightOrder = ref<number[]>([])

const currentQuestion = computed(() => questions.value[currentIndex.value] ?? null)
const progress = computed(() => `${currentIndex.value + 1} / ${questions.value.length}`)
const progressPercent = computed(() =>
  questions.value.length > 0 ? ((currentIndex.value + 1) / questions.value.length) * 100 : 0
)

function toggleCatalog(id: number) {
  const next = new Set(selectedCatalogIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedCatalogIds.value = next
}

function shuffle<T>(array: T[]): T[] {
  const result = [...array]
  for (let i = result.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[result[i], result[j]] = [result[j], result[i]]
  }
  return result
}

function initQuestionState(question: QuizQuestion) {
  const cfg = question.config ?? {}
  if (question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE) {
    const opts = (cfg.options as unknown[]) || []
    mcDisplayOrder.value = shuffle(opts.map((_: unknown, i: number) => i))
  } else {
    mcDisplayOrder.value = []
  }
  if (question.quizQuestionType === QuizQuestionTypes.ORDERING) {
    const items = (cfg.items as string[]) || []
    userOrderItems.value = shuffle(items.map((_: string, i: number) => i))
  } else if (question.quizQuestionType === QuizQuestionTypes.CONNECT) {
    userConnectPairs.value = {}
    const pairs = (cfg.pairs as { left: string; right: string }[]) || []
    connectRightOrder.value = shuffle(pairs.map((_: unknown, i: number) => i))
  } else {
    connectRightOrder.value = []
  }
}

function resetUserInput() {
  showAnswer.value = false
  userAnswer.value = ''
  userMcSelections.value = new Set()
  userTfAnswer.value = null
  userOrderItems.value = []
  userConnectPairs.value = {}
  userFillGaps.value = {}
}

function reorderItems(fromIndex: number, toIndex: number) {
  const order = [...userOrderItems.value]
  const [moved] = order.splice(fromIndex, 1)
  order.splice(toIndex, 0, moved)
  userOrderItems.value = order
}

function moveOrderItem(index: number, direction: -1 | 1) {
  const order = [...userOrderItems.value]
  const newIdx = index + direction
  if (newIdx < 0 || newIdx >= order.length) return
  const temp = order[index]
  order[index] = order[newIdx]
  order[newIdx] = temp
  userOrderItems.value = order
}

function setConnectPair(leftIndex: number, rightValue: string) {
  userConnectPairs.value = { ...userConnectPairs.value, [String(leftIndex)]: rightValue }
}

function setFillGap(gapIndex: number, value: string) {
  userFillGaps.value = { ...userFillGaps.value, [String(gapIndex)]: value }
}

async function startTraining() {
  if (selectedCatalogIds.value.size === 0) return
  loading.value = true
  error.value = ''
  try {
    const allQuestions: QuizQuestion[] = []
    for (const catalogId of selectedCatalogIds.value) {
      const qs = await quiz.getTrainingQuestions(catalogId)
      allQuestions.push(...qs)
    }
    if (allQuestions.length === 0) {
      error.value = t('quiz.training.noQuestions')
      loading.value = false
      return
    }
    questions.value = shuffle(allQuestions)
    currentIndex.value = 0
    resetUserInput()
    initQuestionState(questions.value[0])
    phase.value = 'training'
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function revealAndNext() {
  if (!showAnswer.value) {
    showAnswer.value = true
  } else {
    if (currentIndex.value < questions.value.length - 1) {
      currentIndex.value++
      resetUserInput()
      initQuestionState(questions.value[currentIndex.value])
    } else {
      phase.value = 'finished'
    }
  }
}

function toggleMcOption(idx: number) {
  if (showAnswer.value) return
  const next = new Set(userMcSelections.value)
  if (next.has(idx)) next.delete(idx)
  else next.add(idx)
  userMcSelections.value = next
}

function restart() {
  phase.value = 'select'
  questions.value = []
  currentIndex.value = 0
  resetUserInput()
  selectedCatalogIds.value = new Set()
}

</script>

<template>
  <ViewContent :title="t('pages.quiz-training.title')" :subtitle="t('pages.quiz-training.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <TrainingCatalogSelect
        v-if="phase === 'select' && !loading"
        :catalogs="catalogs"
        :selected-catalog-ids="selectedCatalogIds"
        @toggle="toggleCatalog"
        @start="startTraining"
      />

      <TrainingActiveSession
        v-if="phase === 'training' && !loading && currentQuestion"
        :current-question="currentQuestion"
        :current-index="currentIndex"
        :questions-total="questions.length"
        :progress="progress"
        :progress-percent="progressPercent"
        :show-answer="showAnswer"
        v-model:user-answer="userAnswer"
        :user-mc-selections="userMcSelections"
        v-model:user-tf-answer="userTfAnswer"
        :user-order-items="userOrderItems"
        :user-connect-pairs="userConnectPairs"
        :user-fill-gaps="userFillGaps"
        :mc-display-order="mcDisplayOrder"
        :connect-right-order="connectRightOrder"
        @toggle-mc-option="toggleMcOption"
        @reorder-items="reorderItems"
        @move-order-item="moveOrderItem"
        @set-connect-pair="setConnectPair"
        @set-fill-gap="setFillGap"
        @reveal-and-next="revealAndNext"
      />

      <TrainingFinished v-if="phase === 'finished'" @restart="restart" />
    </div>
  </ViewContent>
</template>
