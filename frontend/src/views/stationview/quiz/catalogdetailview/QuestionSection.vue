/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
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
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import QuestionEditor from '../QuestionEditor.vue'
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
const questionHasImage = ref(false)
const questionConfig = ref<Record<string, unknown>>({})
const savingQuestion = ref(false)

// Delete question modal
const showDeleteQuestionModal = ref(false)
const questionToDelete = ref<QuizQuestion | null>(null)

function getDefaultConfig(type: QuizQuestionTypeName): Record<string, unknown> {
  switch (type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE:
      return { options: [{ text: '', correct: false }], pointsPerCorrect: 0.5 }
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
  questionImagePreview.value = q.imageUrl ? quiz.questionImageUrl(q.id, 300) : null
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
</script>

<template>
  <!-- Editable questions section -->
  <div v-if="!isFederated" class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('quiz.questions.title') }}</SectionHeader>
      <div class="flex gap-2 flex-wrap">
        <SecondaryButton :icon="['fas', 'file-import']" @click="router.push({ name: 'quiz-catalog-import', params: { id: catalogId } })">
          {{ t('quiz.csv.import') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'brain']" @click="router.push({ name: 'quiz-catalog-generate', params: { id: catalogId } })">
          {{ t('quiz.ai.generateQuestions') }}
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

    <EmptyState compact v-if="questions.length === 0 && expandedQuestion !== 'new'">{{ t('quiz.questions.noQuestions') }}</EmptyState>

    <!-- Question list with inline expand -->
    <div class="space-y-2">
      <NeutralContainer v-for="q in questions" :key="q.id">
        <!-- Collapsed view -->
        <template v-if="expandedQuestion !== q.id">
          <div v-if="isMobile" class="space-y-2">
            <div class="flex items-center gap-2 flex-wrap cursor-pointer" @click="expandEditQuestion(q)">
              <span class="font-medium">{{ q.title }}</span>
              <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
            </div>
            <div class="flex items-center gap-2 flex-wrap text-xs text-(--text-muted)">
              <span>{{ q.points }} {{ t('quiz.questions.points') }}</span>
              <SecondaryBadge>{{ getCategoryName(q.categoryId) }}</SecondaryBadge>
            </div>
            <div class="flex items-center justify-end gap-2 border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
              <SecondaryButton :icon="['fas', 'pen']" @click="expandEditQuestion(q)">
                {{ t('common.edit') }}
              </SecondaryButton>
              <DeleteButton @click="confirmDeleteQuestion(q)" />
            </div>
          </div>

          <div v-else class="flex items-center justify-between gap-4 cursor-pointer" @click="expandEditQuestion(q)">
            <div class="flex-1 min-w-0 space-y-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ q.title }}</span>
                <InfoBadge>{{ t(`quiz.questionTypes.${q.questionType}`) }}</InfoBadge>
                <SecondaryBadge>{{ getCategoryName(q.categoryId) }}</SecondaryBadge>
              </div>
              <p v-if="q.description" class="text-xs text-(--text-muted) truncate">{{ q.description }}</p>
            </div>
            <div class="flex items-center gap-2 shrink-0" @click.stop>
              <span class="text-sm text-(--text-muted)">{{ q.points }} {{ t('quiz.questions.points') }}</span>
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
</template>
