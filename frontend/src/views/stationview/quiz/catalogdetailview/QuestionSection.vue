/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import QuestionEditor from '../QuestionEditor.vue'
import BatchActionModal from './BatchActionModal.vue'
import type { QuizCategory, QuizQuestion, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import { useBreakpoint } from '@/composables/useBreakpoint'

const props = defineProps<{
  catalogId: number
  questions: QuizQuestion[]
  categories: QuizCategory[]
  isFederated: boolean
}>()

const emit = defineEmits<{
  updated: []
  error: [message: string]
}>()

const { t } = useI18n()
const router = useRouter()
const { isMobile } = useBreakpoint()

// Inline question editor state
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

// Delete question modal
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
  questionType.value = q.questionType
  questionCategoryId.value = q.categoryId
  questionPoints.value = q.points
  questionAutoPoints.value = q.autoPoints
  questionImageFile.value = null
  questionImagePreview.value = null
  questionAuthImageSrc.value = q.imageUrl ? quiz.questionImageUrl(q.id, 300) : null
  questionHasImage.value = !!q.imageUrl
  try {
    questionConfig.value = typeof q.config === 'string' ? JSON.parse(q.config) : JSON.parse(JSON.stringify(q.config))
  } catch {
    questionConfig.value = getDefaultConfig(q.questionType)
  }
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
    } catch { /* ignore */ }
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
    questionType: questionType.value,
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

// --- Filtering ---
const filterType = ref<string>('')
const filterCategory = ref<string>('')

const filteredQuestions = computed(() => {
  return props.questions.filter(q => {
    if (filterType.value && q.questionType !== filterType.value) return false
    if (filterCategory.value === 'none' && q.categoryId !== null) return false
    if (filterCategory.value && filterCategory.value !== 'none' && q.categoryId !== Number(filterCategory.value)) return false
    return true
  })
})

const questionTypeOptions = computed(() => {
  const types = new Set(props.questions.map(q => q.questionType))
  return [...types].map(type => ({value: type, label: t(`quiz.questionTypes.${type}`)}))
})

// --- Selection ---
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
const selectedHasMc = computed(() => selectedQuestions.value.some(q => q.questionType === QuizQuestionTypes.MULTIPLE_CHOICE))

// --- Batch Actions ---
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
  <!-- Editable questions section -->
  <div v-if="!isFederated" class="space-y-3">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <SectionHeader>{{ t('quiz.questions.title') }}</SectionHeader>
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

    <!-- New question inline editor -->
    <NeutralContainer v-if="expandedQuestion === 'new'">
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <SubHeader>{{ t('quiz.questions.create') }}</SubHeader>
          <IconButton :icon="['fas', 'xmark']" label="Close" class="text-(--text-muted) hover:text-error" @click="collapseQuestion" />
        </div>
        <QuestionEditor
          :title="questionTitle"
          :description="questionDescription"
          :question-type="questionType"
          :category-id="questionCategoryId"
          :points="questionPoints"
          :auto-points="questionAutoPoints"
          :image-preview="questionImagePreview"
          :auth-image-src="questionAuthImageSrc"
          :has-image="questionHasImage"
          :config="questionConfig"
          :categories="categories"
          :is-editing="false"
          @update:title="questionTitle = $event"
          @update:description="questionDescription = $event"
          @update:question-type="onQuestionTypeChange"
          @update:category-id="questionCategoryId = $event"
          @update:points="questionPoints = $event"
          @update:auto-points="questionAutoPoints = $event"
          @update:config="questionConfig = $event"
          @select-image="onImageSelected"
          @remove-image="removeImage"
          @save="saveQuestion"
          @cancel="collapseQuestion"
        />
      </div>
    </NeutralContainer>

    <!-- Filter bar -->
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

    <!-- Selection toolbar -->
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

    <!-- Question list with inline expand -->
    <div class="space-y-2">
      <NeutralContainer v-for="q in filteredQuestions" :key="q.id">
        <!-- Collapsed view -->
        <template v-if="expandedQuestion !== q.id">
          <div v-if="isMobile" class="space-y-2">
            <div class="flex items-center gap-2 cursor-pointer" @click.stop>
              <CheckboxInput :model-value="selectedIds.has(q.id)" @update:model-value="toggleSelect(q.id)"/>
              <div class="flex-1" @click="expandEditQuestion(q)">
                <div class="flex items-center gap-1.5 flex-wrap mb-0.5">
                  <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
                  <SecondaryBadge>{{ getCategoryName(q.categoryId) }}</SecondaryBadge>
                  <span class="text-xs text-(--text-muted)">{{ q.points }} {{ t('quiz.questions.points') }}</span>
                </div>
                <span class="font-medium">{{ q.title }}</span>
              </div>
            </div>
            <div class="flex items-center justify-end gap-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
              <SecondaryButton :icon="['fas', 'pen']" @click="expandEditQuestion(q)">
                {{ t('common.edit') }}
              </SecondaryButton>
              <DeleteButton @click="confirmDeleteQuestion(q)" />
            </div>
          </div>

          <div v-else class="flex items-center justify-between gap-4">
            <div class="flex items-center gap-2 shrink-0" @click.stop>
              <CheckboxInput :model-value="selectedIds.has(q.id)" @update:model-value="toggleSelect(q.id)"/>
            </div>
            <div class="flex-1 min-w-0 space-y-0.5 cursor-pointer" @click="expandEditQuestion(q)">
              <div class="flex items-center gap-1.5 flex-wrap">
                <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
                <SecondaryBadge>{{ getCategoryName(q.categoryId) }}</SecondaryBadge>
                <span class="text-xs text-(--text-muted)">{{ q.points }} {{ t('quiz.questions.points') }}</span>
              </div>
              <span class="font-medium">{{ q.title }}</span>
              <p v-if="q.description" class="text-xs text-(--text-muted) truncate">{{ q.description }}</p>
            </div>
            <div class="flex items-center gap-2 shrink-0" @click.stop>
              <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" class="text-(--text-muted) hover:text-primary" @click="expandEditQuestion(q)" />
              <DeleteButton @click="confirmDeleteQuestion(q)" />
            </div>
          </div>
        </template>

        <!-- Expanded inline editor -->
        <template v-else>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <SubHeader>{{ t('quiz.questions.edit') }}</SubHeader>
              <IconButton :icon="['fas', 'xmark']" label="Close" class="text-(--text-muted) hover:text-error" @click="collapseQuestion" />
            </div>
            <QuestionEditor
              :title="questionTitle"
              :description="questionDescription"
              :question-type="questionType"
              :category-id="questionCategoryId"
              :points="questionPoints"
              :auto-points="questionAutoPoints"
              :image-preview="questionImagePreview"
              :auth-image-src="questionAuthImageSrc"
              :has-image="questionHasImage"
              :config="questionConfig"
              :categories="categories"
              :is-editing="true"
              @update:title="questionTitle = $event"
              @update:description="questionDescription = $event"
              @update:question-type="onQuestionTypeChange"
              @update:category-id="questionCategoryId = $event"
              @update:points="questionPoints = $event"
              @update:auto-points="questionAutoPoints = $event"
              @update:config="questionConfig = $event"
              @select-image="onImageSelected"
              @remove-image="removeImage"
              @save="saveQuestion"
              @cancel="collapseQuestion"
            />
          </div>
        </template>
      </NeutralContainer>
    </div>
  </div>

  <!-- Read-only question list for federated catalogs -->
  <div v-if="isFederated && questions.length > 0" class="space-y-3">
    <SectionHeader>{{ t('quiz.questions.title') }}</SectionHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="q in questions" :key="q.id">
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1 min-w-0 space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="font-medium">{{ q.title }}</span>
              <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
              <SecondaryBadge>{{ getCategoryName(q.categoryId) }}</SecondaryBadge>
            </div>
            <p v-if="q.description" class="text-xs text-(--text-muted) truncate">{{ q.description }}</p>
          </div>
          <span class="text-sm text-(--text-muted) shrink-0">{{ q.points }} {{ t('quiz.questions.points') }}</span>
        </div>
      </NeutralContainer>
    </div>
  </div>

  <!-- Question Delete Modal -->
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

  <!-- Batch Action Modal -->
  <BatchActionModal
      :show="showBatchModal"
      :action="batchAction"
      :questions="selectedQuestions"
      :categories="categories"
      :catalog-id="catalogId"
      @update:show="showBatchModal = $event"
      @done="onBatchDone"
      @error="emit('error', $event)"
  />
</template>
