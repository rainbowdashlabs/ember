/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ReadonlyQuestionList from './ReadonlyQuestionList.vue'
import QuestionCard from './QuestionCard.vue'
import QuestionInlineEditor from './QuestionInlineEditor.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import BatchActionModal from './BatchActionModal.vue'
import type { QuizCategory, QuizQuestion, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'

const props = defineProps<{
  catalogId: number
  questions: QuizQuestion[]
  categories: QuizCategory[]
  isFederated: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  updated: []
  error: [message: string]
}>()

const { t } = useI18n()
const router = useRouter()

const expandedQuestion = ref<number | 'new' | null>(null)
const editingQuestion = ref<QuizQuestion | null>(null)
const questionTitle = ref('')
const questionDescription = ref('')
const questionType = ref<QuizQuestionTypeName>(QuizQuestionTypes.MULTIPLE_CHOICE)
const questionCategoryId = ref<number | null>(null)
const questionPoints = ref(1)
const questionAutoPoints = ref(true)
const questionImageFile = ref<File | null>(null)
const questionImagePreview = ref<string | null>(null)
const questionAuthImageSrc = ref<string | null>(null)
const questionHasImage = ref(false)
const questionConfig = ref<Record<string, unknown>>({})
const savingQuestion = ref(false)

const showDeleteQuestionModal = ref(false)
const questionToDelete = ref<QuizQuestion | null>(null)

function getDefaultConfig(type: QuizQuestionTypeName): Record<string, unknown> {
  switch (type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE:
      return { options: [{ text: '', correct: false }], pointsPerCorrect: 1 }
    case QuizQuestionTypes.FILL_IN_THE_BLANK:
      return { text: '', answers: [], distractors: [], useDropdown: false }
    case QuizQuestionTypes.FREE_ANSWER:
      return { lines: 3, answers: [] }
    case QuizQuestionTypes.CONNECT:
      return { pairs: [{ left: '', right: '' }] }
    case QuizQuestionTypes.IMAGE_TEXT:
      return { imageUrl: '', answer: '' }
    case QuizQuestionTypes.TRUE_FALSE:
      return { correctAnswer: true }
    case QuizQuestionTypes.ORDERING:
      return { items: [''] }
    default:
      return {}
  }
}

function resetQuestionForm() {
  editingQuestion.value = null
  questionTitle.value = ''
  questionDescription.value = ''
  questionType.value = QuizQuestionTypes.MULTIPLE_CHOICE
  questionCategoryId.value = null
  questionPoints.value = 1
  questionAutoPoints.value = true
  questionImageFile.value = null
  questionImagePreview.value = null
  questionAuthImageSrc.value = null
  questionHasImage.value = false
  questionConfig.value = getDefaultConfig(QuizQuestionTypes.MULTIPLE_CHOICE)
}

function expandNewQuestion() {
  if (expandedQuestion.value === 'new') {
    expandedQuestion.value = null
    return
  }
  resetQuestionForm()
  expandedQuestion.value = 'new'
}

function expandEditQuestion(q: QuizQuestion) {
  if (expandedQuestion.value === q.id) {
    expandedQuestion.value = null
    return
  }
  editingQuestion.value = q
  questionTitle.value = q.title
  questionDescription.value = q.description
  questionType.value = q.quizQuestionType
  questionCategoryId.value = q.categoryId
  questionPoints.value = q.points
  questionAutoPoints.value = q.autoPoints
  questionImageFile.value = null
  questionImagePreview.value = null
  questionAuthImageSrc.value = q.imageUrl ? quiz.questionImageUrl(q.id, 300) : null
  questionHasImage.value = !!q.imageUrl
  questionConfig.value = JSON.parse(JSON.stringify(q.config ?? getDefaultConfig(q.quizQuestionType)))
  expandedQuestion.value = q.id
}

function onQuestionTypeChange(val: QuizQuestionTypeName) {
  questionType.value = val
  questionConfig.value = getDefaultConfig(val)
}

function collapseQuestion() {
  expandedQuestion.value = null
}

function onImageSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  questionImageFile.value = file
  questionImagePreview.value = URL.createObjectURL(file)
  questionAuthImageSrc.value = null
  questionHasImage.value = true
}

async function removeImage() {
  if (editingQuestion.value && questionHasImage.value && !questionImageFile.value) {
    try {
      await quiz.deleteQuestionImage(editingQuestion.value.id)
    } catch {
      /* ignore */
    }
  }
  questionImageFile.value = null
  questionImagePreview.value = null
  questionAuthImageSrc.value = null
  questionHasImage.value = false
}

async function saveQuestion() {
  if (!questionTitle.value.trim() || savingQuestion.value) return
  savingQuestion.value = true
  const data: Record<string, unknown> = {
    title: questionTitle.value.trim(),
    description: questionDescription.value.trim(),
    quizQuestionType: questionType.value,
    categoryId: questionCategoryId.value,
    points: questionPoints.value,
    autoPoints: questionAutoPoints.value,
    imageUrl: questionHasImage.value ? 'uploaded' : null,
    config: questionConfig.value,
  }
  try {
    let questionId: number
    if (editingQuestion.value) {
      const updated = await quiz.updateQuestion(editingQuestion.value.id, data)
      questionId = updated.id
    } else {
      const created = await quiz.createQuestion(props.catalogId, data)
      questionId = created.id
    }
    if (questionImageFile.value) {
      await quiz.uploadQuestionImage(questionId, questionImageFile.value)
    }
    expandedQuestion.value = null
    emit('updated')
  } catch {
    emit('error', t('common.error'))
  } finally {
    savingQuestion.value = false
  }
}

function confirmDeleteQuestion(q: QuizQuestion) {
  questionToDelete.value = q
  showDeleteQuestionModal.value = true
}

async function deleteQuestion() {
  if (!questionToDelete.value) return
  try {
    const deletedId = questionToDelete.value.id
    await quiz.deleteQuestion(deletedId)
    showDeleteQuestionModal.value = false
    questionToDelete.value = null
    if (expandedQuestion.value === deletedId) expandedQuestion.value = null
    emit('updated')
  } catch {
    emit('error', t('common.error'))
  }
}

function getCategoryName(catId: number | null): string {
  if (!catId) return t('quiz.questions.noCategory')
  const cat = props.categories.find(c => c.id === catId)
  return cat ? cat.name : t('quiz.questions.noCategory')
}

const filterType = ref<string>('')
const filterCategory = ref<string>('')

const filteredQuestions = computed(() => {
  return props.questions.filter(q => {
    if (filterType.value && q.quizQuestionType !== filterType.value) return false
    if (filterCategory.value === 'none' && q.categoryId !== null) return false
    if (filterCategory.value && filterCategory.value !== 'none' && q.categoryId !== Number(filterCategory.value)) return false
    return true
  })
})

const questionTypeOptions = computed(() => {
  const types = new Set(props.questions.map(q => q.quizQuestionType))
  return [...types].map(type => ({value: type, label: t(`quiz.questionTypes.${type}`)}))
})

const selectedIds = ref(new Set<number>())

function toggleSelect(id: number) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedIds.value = s
}

function selectAll() {
  selectedIds.value = new Set(filteredQuestions.value.map(q => q.id))
}

function deselectAll() {
  selectedIds.value = new Set()
}

const selectedQuestions = computed(() => props.questions.filter(q => selectedIds.value.has(q.id)))
const hasSelection = computed(() => selectedIds.value.size > 0)
const selectedHasMc = computed(() => selectedQuestions.value.some(q => q.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE))

const showBatchModal = ref(false)
const batchAction = ref('')

function openBatchAction(action: string) {
  batchAction.value = action
  showBatchModal.value = true
}

function onBatchDone() {
  emit('updated')
}
</script>

<template>
  <div v-if="!readonly" class="space-y-3">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <SubHeader>{{ t('quiz.questions.title') }}</SubHeader>
      <div class="grid grid-cols-2 sm:flex gap-2">
        <SecondaryButton :icon="['fas', 'file-import']" @click="router.push({ name: 'quiz-catalog-import', params: { id: catalogId } })">
          {{ t('quiz.csv.import') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'brain']" @click="router.push({ name: 'quiz-catalog-generate', params: { id: catalogId } })">
          {{ t('quiz.ai.generateQuestions') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'brain']" @click="router.push({name: 'quiz-catalog-mc-fill', params: {id: catalogId}})">
          {{ t('quiz.ai.fillMcAnswers') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="expandNewQuestion">
          {{ t('quiz.questions.create') }}
        </PrimaryButton>
      </div>
    </div>

    <NeutralContainer v-if="expandedQuestion === 'new'">
      <QuestionInlineEditor
        :is-editing="false"
        v-model:title="questionTitle"
        v-model:description="questionDescription"
        :question-type="questionType"
        v-model:category-id="questionCategoryId"
        v-model:points="questionPoints"
        v-model:auto-points="questionAutoPoints"
        v-model:config="questionConfig"
        :image-preview="questionImagePreview"
        :auth-image-src="questionAuthImageSrc"
        :has-image="questionHasImage"
        :categories="categories"
        @update:question-type="onQuestionTypeChange"
        @select-image="onImageSelected"
        @remove-image="removeImage"
        @save="saveQuestion"
        @cancel="collapseQuestion"
      />
    </NeutralContainer>

    <div v-if="questions.length > 0" class="grid grid-cols-2 sm:flex items-center gap-2 mb-3">
      <SelectInput v-model="filterType" class="w-auto text-sm">
        <option value="">{{ t('quiz.questions.allTypes') }}</option>
        <option v-for="opt in questionTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
      <SelectInput v-model="filterCategory" class="w-auto text-sm">
        <option value="">{{ t('quiz.questions.allCategories') }}</option>
        <option value="none">{{ t('quiz.questions.noCategory') }}</option>
        <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
      </SelectInput>
      <MutedText size="sm">{{ filteredQuestions.length }}/{{ questions.length }}</MutedText>
    </div>

    <div v-if="hasSelection" class="flex items-center gap-2 flex-wrap mb-3 p-2 rounded bg-primary/10 border border-primary/30">
      <MutedText size="sm" class="font-medium">{{ selectedIds.size }} {{ t('quiz.batch.selected') }}</MutedText>
      <SecondaryButton compact @click="selectAll">{{ t('quiz.batch.selectAll') }}</SecondaryButton>
      <SecondaryButton compact @click="deselectAll">{{ t('quiz.batch.deselectAll') }}</SecondaryButton>
      <span class="border-l border-primary/30 h-4"/>
      <SecondaryButton compact @click="openBatchAction('autoPoints')">{{ t('quiz.batch.toggleAutoPoints') }}</SecondaryButton>
      <SecondaryButton compact @click="openBatchAction('setPoints')">{{ t('quiz.batch.setPoints') }}</SecondaryButton>
      <SecondaryButton compact v-if="selectedHasMc" @click="openBatchAction('pointsPerCorrect')">{{ t('quiz.batch.setPointsPerCorrect') }}</SecondaryButton>
      <SecondaryButton compact @click="openBatchAction('setCategory')">{{ t('quiz.batch.setCategory') }}</SecondaryButton>
      <SecondaryButton compact :icon="['fas', 'brain']" @click="openBatchAction('generate')">{{ t('quiz.batch.generate') }}</SecondaryButton>
    </div>

    <EmptyState compact v-if="questions.length === 0 && expandedQuestion !== 'new'">{{ t('quiz.questions.noQuestions') }}</EmptyState>

    <div class="space-y-2">
      <QuestionCard
        v-for="q in filteredQuestions"
        :key="q.id"
        :question="q"
        :expanded="expandedQuestion === q.id"
        :selected="selectedIds.has(q.id)"
        :category-name="getCategoryName(q.categoryId)"
        :categories="categories"
        v-model:editor-title="questionTitle"
        v-model:editor-description="questionDescription"
        :editor-question-type="questionType"
        v-model:editor-category-id="questionCategoryId"
        v-model:editor-points="questionPoints"
        v-model:editor-auto-points="questionAutoPoints"
        v-model:editor-config="questionConfig"
        :editor-image-preview="questionImagePreview"
        :editor-auth-image-src="questionAuthImageSrc"
        :editor-has-image="questionHasImage"
        @toggle-select="toggleSelect(q.id)"
        @edit="expandEditQuestion(q)"
        @delete="confirmDeleteQuestion(q)"
        @update:editor-question-type="onQuestionTypeChange"
        @select-image="onImageSelected"
        @remove-image="removeImage"
        @save="saveQuestion"
        @cancel="collapseQuestion"
      />
    </div>
  </div>

  <ReadonlyQuestionList v-if="readonly" :questions="questions" :categories="categories" />

  <Modal v-model="showDeleteQuestionModal">
    <div class="space-y-4">
      <SubHeader>{{ t('common.delete') }}</SubHeader>
      <p class="text-sm">{{ t('quiz.questions.deleteConfirm') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showDeleteQuestionModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="deleteQuestion">{{ t('common.delete') }}</ErrorButton>
      </div>
    </div>
  </Modal>

  <BatchActionModal
      v-model:show="showBatchModal"
      :action="batchAction"
      :questions="selectedQuestions"
      :categories="categories"
      :catalog-id="catalogId"
      @done="onBatchDone"
      @error="emit('error', $event)"
  />
</template>
