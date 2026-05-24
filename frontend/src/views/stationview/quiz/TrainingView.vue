/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import TrainingQuestionCard from './trainingview/TrainingQuestionCard.vue'
import type { QuizCatalog, QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()

const phase = ref<'select' | 'training' | 'finished'>('select')
const loading = ref(true)
const error = ref('')
const catalogs = ref<QuizCatalog[]>([])
const selectedCatalogIds = ref<Set<number>>(new Set())
const questions = ref<QuizQuestion[]>([])
const currentIndex = ref(0)
const showAnswer = ref(false)
const userAnswer = ref('')
const userMcSelections = ref<Set<number>>(new Set())
const userTfAnswer = ref<boolean | null>(null)
const userOrderItems = ref<number[]>([])
const userConnectPairs = ref<Record<string, string>>({})

const currentQuestion = computed(() => questions.value[currentIndex.value] ?? null)
const progress = computed(() => `${currentIndex.value + 1} / ${questions.value.length}`)
const progressPercent = computed(() =>
  questions.value.length > 0 ? ((currentIndex.value + 1) / questions.value.length) * 100 : 0
)

function parseConfig(config: string): Record<string, unknown> {
  try { return JSON.parse(config || '{}') } catch { return {} }
}

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
  const cfg = parseConfig(question.config)
  if (question.questionType === QuizQuestionTypes.ORDERING) {
    const items = (cfg.items as string[]) || []
    const indices = items.map((_: string, i: number) => i)
    userOrderItems.value = shuffle(indices)
  } else if (question.questionType === QuizQuestionTypes.CONNECT) {
    userConnectPairs.value = {}
  }
}

function resetUserInput() {
  showAnswer.value = false
  userAnswer.value = ''
  userMcSelections.value = new Set()
  userTfAnswer.value = null
  userOrderItems.value = []
  userConnectPairs.value = {}
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

async function loadCatalogs() {
  loading.value = true
  try {
    catalogs.value = await quiz.listTrainingCatalogs()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadCatalogs)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <SectionHeader>{{ t('quiz.training.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- Phase 1: Catalog selection -->
      <template v-if="phase === 'select' && !loading">
        <p class="text-(--text-muted)">{{ t('quiz.training.selectCatalogs') }}</p>

        <div v-if="catalogs.length === 0" class="text-(--text-muted) text-sm">
          {{ t('quiz.training.noCatalogs') }}
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div
            v-for="catalog in catalogs"
            :key="catalog.id"
            class="rounded-lg border-2 p-4 cursor-pointer transition-all"
            :class="selectedCatalogIds.has(catalog.id) ? 'border-success bg-success/10' : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary'"
            @click="toggleCatalog(catalog.id)"
          >
            <div class="flex items-center gap-2">
              <font-awesome-icon
                :icon="['fas', selectedCatalogIds.has(catalog.id) ? 'square-check' : 'square']"
                class="text-xl shrink-0"
                :class="selectedCatalogIds.has(catalog.id) ? 'text-success' : 'text-(--text-muted)'"
              />
              <div>
                <span class="font-medium">{{ catalog.name }}</span>
                <p v-if="catalog.description" class="text-xs text-(--text-muted) mt-0.5">
                  {{ catalog.description }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end">
          <PrimaryButton :disabled="selectedCatalogIds.size === 0" @click="startTraining">
            {{ t('quiz.training.start') }}
          </PrimaryButton>
        </div>
      </template>

      <!-- Phase 2: Training questions -->
      <template v-if="phase === 'training' && !loading && currentQuestion">
        <!-- Progress bar -->
        <div class="space-y-1">
          <div class="flex justify-between text-sm text-(--text-muted)">
            <span>{{ t('quiz.training.progress', { current: currentIndex + 1, total: questions.length }) }}</span>
            <span>{{ progress }}</span>
          </div>
          <div class="w-full h-2 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
            <div class="h-full rounded-full bg-primary transition-all duration-300" :style="{ width: `${progressPercent}%` }" />
          </div>
        </div>

        <TrainingQuestionCard
          :question="currentQuestion"
          :show-answer="showAnswer"
          :user-answer="userAnswer"
          :user-mc-selections="userMcSelections"
          :user-tf-answer="userTfAnswer"
          :user-order-items="userOrderItems"
          :user-connect-pairs="userConnectPairs"
          @toggle-mc-option="toggleMcOption"
          @update:user-tf-answer="(v: boolean) => userTfAnswer = v"
          @update:user-answer="(v: string) => userAnswer = v"
          @reorder-items="reorderItems"
          @move-order-item="moveOrderItem"
          @set-connect-pair="setConnectPair"
        />

        <!-- Single action button: Show Answer → Next Question → Finish -->
        <div class="flex justify-end">
          <PrimaryButton @click="revealAndNext">
            <template v-if="!showAnswer">
              <font-awesome-icon :icon="['fas', 'eye']" class="mr-1" />
              {{ t('quiz.training.showAnswer') }}
            </template>
            <template v-else-if="currentIndex < questions.length - 1">
              {{ t('quiz.training.next') }}
              <font-awesome-icon :icon="['fas', 'arrow-right']" class="ml-1" />
            </template>
            <template v-else>
              {{ t('quiz.training.finish') }}
              <font-awesome-icon :icon="['fas', 'check']" class="ml-1" />
            </template>
          </PrimaryButton>
        </div>
      </template>

      <!-- Phase 3: Finished -->
      <template v-if="phase === 'finished'">
        <SuccessContainer>
          <div class="text-center space-y-4 py-4">
            <font-awesome-icon :icon="['fas', 'check']" class="text-4xl" />
            <SubHeader>{{ t('quiz.training.finished') }}</SubHeader>
            <div class="flex justify-center">
              <SecondaryButton :icon="['fas', 'rotate']" @click="restart">
                {{ t('quiz.training.restart') }}
              </SecondaryButton>
            </div>
          </div>
        </SuccessContainer>
      </template>
    </div>
  </ViewContent>
</template>
