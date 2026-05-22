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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DragList from '@/components/input/DragList.vue'
import IconButton from '@/components/button/IconButton.vue'
import type { QuizCatalog, QuizQuestion } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'

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

function shuffleWords(question: QuizQuestion): string[] {
  const cfg = parseConfig(question.config)
  const answers = (cfg.answers as string[]) || []
  const distractors = (cfg.distractors as string[]) || []
  return shuffle([...answers, ...distractors])
}

function getCorrectAnswer(question: QuizQuestion): string {
  const cfg = parseConfig(question.config)
  switch (question.questionType) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const options = (cfg.options as { text: string; correct: boolean }[]) || []
      return options.filter(o => o.correct).map(o => o.text).join(', ')
    }
    case QuizQuestionTypes.TRUE_FALSE:
      return cfg.correctAnswer === true ? t('quiz.trueLabel') : t('quiz.falseLabel')
    case QuizQuestionTypes.FILL_IN_THE_BLANK: {
      const answers = (cfg.answers as string[]) || []
      return answers.join(', ')
    }
    case QuizQuestionTypes.ORDERING:
      return ((cfg.items as string[]) || []).join(' → ')
    case QuizQuestionTypes.CONNECT: {
      const pairs = (cfg.pairs as { left: string; right: string }[]) || []
      return pairs.map(p => `${p.left} → ${p.right}`).join(', ')
    }
    case QuizQuestionTypes.IMAGE_TEXT:
      return (cfg.answer as string) || ''
    case QuizQuestionTypes.FREE_ANSWER: {
      const answers = (cfg.answers as string[]) || []
      return answers.length > 0 ? answers.join(', ') : t('quiz.noSampleAnswer')
    }
    default:
      return ''
  }
}

function isAnswerCorrect(question: QuizQuestion): boolean | null {
  const cfg = parseConfig(question.config)
  switch (question.questionType) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const options = (cfg.options as { text: string; correct: boolean }[]) || []
      const correctSet = new Set(options.map((o, i) => o.correct ? i : -1).filter(i => i >= 0))
      if (userMcSelections.value.size === 0) return null
      if (userMcSelections.value.size !== correctSet.size) return false
      for (const idx of userMcSelections.value) {
        if (!correctSet.has(idx)) return false
      }
      return true
    }
    case QuizQuestionTypes.TRUE_FALSE:
      if (userTfAnswer.value === null) return null
      return userTfAnswer.value === (cfg.correctAnswer as boolean)
    default:
      return null
  }
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

function getConnectLeftItems(question: QuizQuestion): string[] {
  const cfg = parseConfig(question.config)
  const pairs = (cfg.pairs as { left: string; right: string }[]) || []
  return pairs.map(p => p.left)
}

function getConnectRightItems(question: QuizQuestion): string[] {
  const cfg = parseConfig(question.config)
  const pairs = (cfg.pairs as { left: string; right: string }[]) || []
  return shuffle(pairs.map(p => p.right))
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
      <h2 class="text-xl font-semibold">{{ t('quiz.training.title') }}</h2>

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
            <div class="flex items-center gap-3">
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

        <NeutralContainer>
          <div class="space-y-4">
            <!-- Question header -->
            <div>
              <span class="text-xs font-semibold text-(--text-muted) uppercase">
                {{ t(`quiz.questionTypes.${currentQuestion.questionType}`) }}
                <span class="ml-2">({{ currentQuestion.points }} {{ t('quiz.points') }})</span>
              </span>
              <h3 class="text-lg font-medium mt-1">{{ currentQuestion.title }}</h3>
              <p v-if="currentQuestion.description" class="text-sm text-(--text-muted) mt-0.5">{{ currentQuestion.description }}</p>
            </div>

            <!-- Question image -->
            <img v-if="currentQuestion.imageUrl" :src="quiz.questionImageUrl(currentQuestion.id, 400)" alt="" class="max-h-48 rounded" />

            <!-- Multiple Choice -->
            <template v-if="currentQuestion.questionType === QuizQuestionTypes.MULTIPLE_CHOICE">
              <div class="space-y-2">
                <div
                  v-for="(opt, oi) in (parseConfig(currentQuestion.config).options as { text: string; correct: boolean }[])"
                  :key="oi"
                  class="flex items-center gap-3 px-3 py-2 rounded-lg border cursor-pointer transition-all"
                  :class="[
                    showAnswer && opt.correct ? 'border-success bg-success/10' : '',
                    showAnswer && userMcSelections.has(oi) && !opt.correct ? 'border-error bg-error/10' : '',
                    !showAnswer && userMcSelections.has(oi) ? 'border-primary bg-primary/10' : '',
                    !showAnswer && !userMcSelections.has(oi) ? 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary' : '',
                  ]"
                  @click="toggleMcOption(oi)"
                >
                  <font-awesome-icon
                    :icon="['fas', userMcSelections.has(oi) ? 'square-check' : 'square']"
                    :class="showAnswer && opt.correct ? 'text-success' : showAnswer && userMcSelections.has(oi) && !opt.correct ? 'text-error' : userMcSelections.has(oi) ? 'text-primary' : 'text-(--text-muted)'"
                  />
                  <span class="text-sm">{{ opt.text }}</span>
                </div>
              </div>
            </template>

            <!-- True/False -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.TRUE_FALSE">
              <div class="flex gap-3">
                <div
                  v-for="(val, label) in { [t('quiz.trueLabel')]: true, [t('quiz.falseLabel')]: false } as Record<string, boolean>"
                  :key="label"
                  class="flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-lg border cursor-pointer transition-all text-sm font-medium"
                  :class="[
                    showAnswer && val === (parseConfig(currentQuestion.config).correctAnswer as boolean) ? 'border-success bg-success/10' : '',
                    showAnswer && userTfAnswer === val && val !== (parseConfig(currentQuestion.config).correctAnswer as boolean) ? 'border-error bg-error/10' : '',
                    !showAnswer && userTfAnswer === val ? 'border-primary bg-primary/10' : '',
                    !showAnswer && userTfAnswer !== val ? 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary' : '',
                  ]"
                  @click="() => { if (!showAnswer) userTfAnswer = val }"
                >
                  {{ label }}
                </div>
              </div>
            </template>

            <!-- Fill in the Blank -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.FILL_IN_THE_BLANK">
              <template v-if="(parseConfig(currentQuestion.config).useDropdown as boolean)">
                <!-- Dropdown mode: show text with selects in gaps -->
                <div class="text-sm leading-relaxed">
                  <template v-for="(part, pi) in (parseConfig(currentQuestion.config).text as string || '').split('___')" :key="pi">
                    <span>{{ part }}</span>
                    <select v-if="pi < ((parseConfig(currentQuestion.config).answers as string[]) || []).length"
                            :disabled="showAnswer"
                            class="mx-1 px-2 py-1 rounded border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark text-sm">
                      <option value="">...</option>
                      <option v-for="word in shuffleWords(currentQuestion)" :key="word" :value="word">{{ word }}</option>
                    </select>
                  </template>
                </div>
                <div v-if="!showAnswer" class="flex flex-wrap gap-2 mt-2">
                  <span class="text-xs text-(--text-muted)">{{ t('quiz.training.wordBank') }}:</span>
                  <span v-for="word in shuffleWords(currentQuestion)" :key="word" class="px-2 py-0.5 text-xs rounded bg-primary/10 border border-primary/20">{{ word }}</span>
                </div>
              </template>
              <template v-else>
                <TextInput v-model="userAnswer" :disabled="showAnswer" :placeholder="t('quiz.training.yourAnswer')" />
              </template>
            </template>

            <!-- Free Answer -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.FREE_ANSWER">
              <TextAreaInput v-model="userAnswer" :disabled="showAnswer" :placeholder="t('quiz.training.yourAnswer')" />
            </template>

            <!-- Image Text -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.IMAGE_TEXT">
              <TextAreaInput v-model="userAnswer" :disabled="showAnswer" :placeholder="t('quiz.training.yourAnswer')" />
            </template>

            <!-- Ordering -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.ORDERING">
              <p class="text-xs text-(--text-muted)">{{ t('quiz.training.orderHint') }}</p>
              <div class="space-y-1">
                <DragList
                  :items="userOrderItems"
                  :key-fn="(_item: number, index: number) => index"
                  @reorder="reorderItems"
                >
                  <template #default="{ item, index }: { item: number; index: number }">
                    <div class="flex items-center gap-2 px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent mb-1 cursor-grab bg-(--bg)">
                      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted)" />
                      <span class="text-xs text-(--text-muted) w-5">{{ index + 1 }}.</span>
                      <span class="flex-1 text-sm">{{ ((parseConfig(currentQuestion.config).items as string[]) || [])[item] }}</span>
                      <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')" class="text-(--text-muted) hover:text-primary" @click="moveOrderItem(index, -1)" />
                      <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')" class="text-(--text-muted) hover:text-primary" @click="moveOrderItem(index, 1)" />
                    </div>
                  </template>
                </DragList>
              </div>
            </template>

            <!-- Connect -->
            <template v-else-if="currentQuestion.questionType === QuizQuestionTypes.CONNECT">
              <div class="space-y-2">
                <div
                  v-for="(left, li) in getConnectLeftItems(currentQuestion)"
                  :key="li"
                  class="flex items-center gap-3 p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent"
                >
                  <span class="text-sm font-medium flex-1">{{ left }}</span>
                  <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
                  <template v-if="showAnswer">
                    <span class="px-2 py-1 rounded text-sm"
                          :class="userConnectPairs[String(li)] === ((parseConfig(currentQuestion.config).pairs as { left: string; right: string }[])[li]?.right)
                            ? 'bg-success/10 border border-success/20'
                            : 'bg-error/10 border border-error/20'">
                      {{ userConnectPairs[String(li)] || '—' }}
                    </span>
                    <span class="text-xs text-(--text-muted)">→</span>
                    <span class="px-2 py-1 rounded bg-success/10 border border-success/20 text-sm">
                      {{ ((parseConfig(currentQuestion.config).pairs as { left: string; right: string }[])[li]?.right) }}
                    </span>
                  </template>
                  <SelectInput
                    v-else
                    :model-value="userConnectPairs[String(li)] ?? ''"
                    class="flex-1"
                    @update:model-value="(v: string | undefined) => setConnectPair(li, v ?? '')"
                  >
                    <option value="">--</option>
                    <option v-for="(right, ri) in getConnectRightItems(currentQuestion)" :key="ri" :value="right">
                      {{ right }}
                    </option>
                  </SelectInput>
                </div>
              </div>
            </template>

            <!-- Correct/wrong feedback + answer reveal -->
            <template v-if="showAnswer">
              <SuccessContainer v-if="isAnswerCorrect(currentQuestion) === true">
                <p class="text-sm font-medium">{{ t('quiz.training.correct') }}</p>
              </SuccessContainer>
              <ErrorContainer v-else-if="isAnswerCorrect(currentQuestion) === false">
                <p class="text-sm font-medium">{{ t('quiz.training.wrong') }}</p>
              </ErrorContainer>
              <NeutralContainer>
                <label class="text-xs font-semibold block mb-1">{{ t('quiz.training.correctAnswer') }}</label>
                <p class="text-sm">{{ getCorrectAnswer(currentQuestion) }}</p>
              </NeutralContainer>
            </template>
          </div>
        </NeutralContainer>

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
            <h3 class="text-lg font-semibold">{{ t('quiz.training.finished') }}</h3>
            <div class="flex justify-center">
              <SecondaryButton @click="restart">
                <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1" />
                {{ t('quiz.training.restart') }}
              </SecondaryButton>
            </div>
          </div>
        </SuccessContainer>
      </template>
    </div>
  </ViewContent>
</template>
