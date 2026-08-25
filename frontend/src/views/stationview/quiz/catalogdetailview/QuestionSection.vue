/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useConfirmAction } from '@/composables/useConfirmAction'
import ReadonlyQuestionList from './ReadonlyQuestionList.vue'
import QuestionCard from './QuestionCard.vue'
import QuestionInlineEditor from './QuestionInlineEditor.vue'
import QuestionSectionToolbar from './questionsection/QuestionSectionToolbar.vue'
import QuestionFilterBar from './questionsection/QuestionFilterBar.vue'
import QuestionBatchBar from './questionsection/QuestionBatchBar.vue'
import { useQuestionForm } from './questionsection/useQuestionForm'
import { useQuestionListState } from './questionsection/useQuestionListState'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import BatchActionModal from './BatchActionModal.vue'
import type { QuizCategory, QuizQuestion, QuizQuestionReport } from '@/api/quiz'
import { quiz } from '@/api'

const props = defineProps<{
  catalogId: number
  questions: QuizQuestion[]
  reports: QuizQuestionReport[]
  categories: QuizCategory[]
  isFederated: boolean
  readonly?: boolean
}>()

/** The open notes on one question, so each card carries only what was said about it. */
function reportsFor(questionId: number): QuizQuestionReport[] {
  return props.reports.filter(report => report.questionId === questionId)
}

const emit = defineEmits<{
  updated: []
  error: [message: string]
}>()

const { t } = useI18n()

const {
  expandedQuestion,
  questionTitle,
  questionDescription,
  questionType,
  questionCategoryId,
  questionPoints,
  questionAutoPoints,
  questionImagePreview,
  questionAuthImageSrc,
  questionHasImage,
  questionConfig,
  expandNewQuestion,
  expandEditQuestion,
  onQuestionTypeChange,
  collapseQuestion,
  onImageSelected,
  removeImage,
  saveQuestion,
} = useQuestionForm(toRef(props, 'catalogId'), () => emit('updated'), () => emit('error', t('common.error')))

const {
  filterType,
  filterCategory,
  filteredQuestions,
  questionTypeOptions,
  selectedIds,
  toggleSelect,
  selectAll,
  deselectAll,
  selectedQuestions,
  hasSelection,
  selectedHasMc,
} = useQuestionListState(toRef(props, 'questions'), type => t(`quiz.questionTypes.${type}`))

const deleteError = ref('')

const {
  show: showDeleteQuestionModal,
  request: confirmDeleteQuestion,
  confirm: deleteQuestion,
} = useConfirmAction<QuizQuestion>({
  onConfirm: q => quiz.deleteQuestion(q.id),
  onSuccess: q => {
    if (expandedQuestion.value === q.id) expandedQuestion.value = null
    emit('updated')
  },
  error: deleteError,
})

watch(deleteError, message => {
  if (!message) return
  deleteError.value = ''
  emit('error', message)
})

function getCategoryName(catId: number | null): string {
  if (!catId) return t('quiz.questions.noCategory')
  const cat = props.categories.find(c => c.id === catId)
  return cat ? cat.name : t('quiz.questions.noCategory')
}

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
    <QuestionSectionToolbar :catalog-id="catalogId" @create="expandNewQuestion" />

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

    <QuestionFilterBar
      v-if="questions.length > 0"
      v-model:type="filterType"
      v-model:category="filterCategory"
      :categories="categories"
      :type-options="questionTypeOptions"
      :filtered-count="filteredQuestions.length"
      :total-count="questions.length"
    />

    <QuestionBatchBar
      v-if="hasSelection"
      :selected-count="selectedIds.size"
      :has-multiple-choice="selectedHasMc"
      @select-all="selectAll"
      @deselect-all="deselectAll"
      @action="openBatchAction"
    />

    <EmptyState compact v-if="questions.length === 0 && expandedQuestion !== 'new'">{{ t('quiz.questions.noQuestions') }}</EmptyState>

    <div class="space-y-2">
      <QuestionCard
        v-for="q in filteredQuestions"
        :key="q.id"
        :question="q"
        :expanded="expandedQuestion === q.id"
        :selected="selectedIds.has(q.id)"
        :reports="reportsFor(q.id)"
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
        @report-acknowledged="emit('updated')"
        @report-error="(message: string) => emit('error', message)"
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
