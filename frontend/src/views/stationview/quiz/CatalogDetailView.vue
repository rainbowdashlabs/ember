/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import QuestionEditor from './QuestionEditor.vue'
import CsvImportDialog from './CsvImportDialog.vue'
import type { QuizCatalogDetail, QuizCategory, QuizQuestion, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz, federation, storage } from '@/api'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'
import StationBadge from '@/components/badge/StationBadge.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()
const { isMobile } = useBreakpoint()

const catalogId = computed(() => Number(route.params.id))

const currentStationId = computed(() => {
  return storage.getItem('station_id') ?? null
})

const isFederated = computed(() => {
  if (!catalog.value || currentStationId.value == null) return false
  return catalog.value.stationId !== currentStationId.value
})

const catalog = ref<QuizCatalogDetail | null>(null)
const questions = ref<QuizQuestion[]>([])
const loading = ref(true)
const error = ref('')

// Catalog edit state
const editName = ref('')
const editDescription = ref('')
const editTrainingEnabled = ref(false)
const catalogDirty = ref(false)

// Category management
const showCategoryModal = ref(false)
const newCategoryName = ref('')
const newCategoryDescription = ref('')
const showEditCategoryModal = ref(false)
const editCategoryId = ref<number | null>(null)
const editCategoryName = ref('')
const editCategoryDescription = ref('')
const showDeleteCategoryModal = ref(false)
const categoryToDelete = ref<QuizCategory | null>(null)

// Inline question editor — expanded question id, or 'new' for creating
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

// CSV import
const showCsvImport = ref(false)

// Delete question modal
const showDeleteQuestionModal = ref(false)
const questionToDelete = ref<QuizQuestion | null>(null)

// --- Data Loading ---

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [catalogData, questionsData] = await Promise.all([
      quiz.getCatalog(catalogId.value),
      quiz.listQuestions(catalogId.value),
    ])
    catalog.value = catalogData
    questions.value = questionsData
    editName.value = catalogData.name
    editDescription.value = catalogData.description
    editTrainingEnabled.value = catalogData.trainingEnabled
    catalogDirty.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// --- Catalog Update ---

function markCatalogDirty() {
  catalogDirty.value = true
}

async function saveCatalog() {
  if (!editName.value.trim()) return
  error.value = ''
  try {
    await quiz.updateCatalog(catalogId.value, {
      name: editName.value.trim(),
      description: editDescription.value.trim(),
      trainingEnabled: editTrainingEnabled.value,
    })
    catalogDirty.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// --- Categories ---

function openCategoryModal() {
  newCategoryName.value = ''
  newCategoryDescription.value = ''
  showCategoryModal.value = true
}

async function createCategory() {
  if (!newCategoryName.value.trim()) return
  error.value = ''
  try {
    await quiz.createCategory({ name: newCategoryName.value.trim(), description: newCategoryDescription.value.trim() })
    showCategoryModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function openEditCategoryModal(category: QuizCategory) {
  editCategoryId.value = category.id
  editCategoryName.value = category.name
  editCategoryDescription.value = category.description || ''
  showEditCategoryModal.value = true
}

async function updateCategory() {
  if (!editCategoryId.value || !editCategoryName.value.trim()) return
  error.value = ''
  try {
    await quiz.updateCategory(editCategoryId.value, { name: editCategoryName.value.trim(), description: editCategoryDescription.value.trim() })
    showEditCategoryModal.value = false
    editCategoryId.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function confirmDeleteCategory(category: QuizCategory) {
  categoryToDelete.value = category
  showDeleteCategoryModal.value = true
}

async function deleteCategory() {
  if (!categoryToDelete.value) return
  error.value = ''
  try {
    await quiz.deleteCategory(categoryToDelete.value.id)
    showDeleteCategoryModal.value = false
    categoryToDelete.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

// --- Questions ---

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

const savingQuestion = ref(false)

async function saveQuestion() {
  if (!questionTitle.value.trim() || savingQuestion.value) return
  savingQuestion.value = true
  error.value = ''
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
      const created = await quiz.createQuestion(catalogId.value, data)
      questionId = created.id
    }
    if (questionImageFile.value) {
      await quiz.uploadQuestionImage(questionId, questionImageFile.value)
    }
    expandedQuestion.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
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
  error.value = ''
  try {
    const deletedId = questionToDelete.value.id
    await quiz.deleteQuestion(deletedId)
    showDeleteQuestionModal.value = false
    questionToDelete.value = null
    if (expandedQuestion.value === deletedId) expandedQuestion.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function onCsvImported(_count: number) {
  showCsvImport.value = false
  await loadData()
}

async function copyToStation() {
  error.value = ''
  try {
    await federation.copyQuizCatalog(catalogId.value)
    router.push({ name: 'quiz-catalogs' })
  } catch {
    error.value = t('common.error')
  }
}

function getCategoryName(catId: number | null): string {
  if (!catId || !catalog.value) return t('quiz.questions.noCategory')
  const cat = catalog.value.categories.find(c => c.id === catId)
  return cat ? cat.name : t('quiz.questions.noCategory')
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="router.push({ name: 'quiz-catalogs' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && catalog">
        <!-- Federated: Copy to station button -->
        <NeutralContainer v-if="isFederated">
          <div class="space-y-4">
            <div class="flex items-center gap-3 flex-wrap">
              <PageHeader>{{ catalog.name }}</PageHeader>
              <StationBadge :station-name="''" />
            </div>
            <p v-if="catalog.description" class="text-sm text-(--text-muted)">{{ catalog.description }}</p>
            <div v-if="catalog.questionTypeCounts && Object.keys(catalog.questionTypeCounts).length > 0" class="flex flex-wrap gap-2">
              <InfoBadge v-for="(count, type) in catalog.questionTypeCounts" :key="type">
                {{ t(`quiz.questionTypes.${type}`) }}: {{ count }}
              </InfoBadge>
              <SecondaryBadge>{{ t('quiz.catalogs.questionCount', { count: catalog.questionCount }) }}</SecondaryBadge>
            </div>
            <div class="flex justify-end">
              <PrimaryButton @click="copyToStation">
                <font-awesome-icon :icon="['fas', 'copy']" class="mr-1" />
                {{ t('federation.copyToStation') }}
              </PrimaryButton>
            </div>
          </div>
        </NeutralContainer>

        <!-- Catalog Metadata (editable, local only) -->
        <NeutralContainer v-else>
          <div class="space-y-4">
            <PageHeader>{{ catalog.name }}</PageHeader>
            <div v-if="catalog.questionTypeCounts && Object.keys(catalog.questionTypeCounts).length > 0" class="flex flex-wrap gap-2">
              <InfoBadge v-for="(count, type) in catalog.questionTypeCounts" :key="type">
                {{ t(`quiz.questionTypes.${type}`) }}: {{ count }}
              </InfoBadge>
              <SecondaryBadge>{{ t('quiz.catalogs.questionCount', { count: catalog.questionCount }) }}</SecondaryBadge>
            </div>
            <TextInput v-model="editName" :placeholder="t('quiz.catalogs.name')" @update:model-value="markCatalogDirty" />
            <TextAreaInput v-model="editDescription" :placeholder="t('quiz.catalogs.description')" @update:model-value="markCatalogDirty" />
            <label class="flex items-center gap-2 text-sm">
              <ToggleInput v-model="editTrainingEnabled" @update:model-value="markCatalogDirty" />
              {{ t('quiz.catalogs.trainingEnabled') }}
            </label>
            <div v-if="catalogDirty" class="flex justify-end">
              <PrimaryButton @click="saveCatalog">{{ t('common.save') }}</PrimaryButton>
            </div>
          </div>
        </NeutralContainer>

        <!-- Categories Section (hidden for federated) -->
        <div v-if="!isFederated" class="space-y-3">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <SectionHeader>{{ t('quiz.categories.title') }}</SectionHeader>
            <SecondaryButton @click="openCategoryModal">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
              {{ t('quiz.categories.create') }}
            </SecondaryButton>
          </div>
          <EmptyState compact v-if="catalog.categories.length === 0">{{ t('quiz.categories.noCategories') }}</EmptyState>
          <div class="flex flex-wrap gap-2">
            <NeutralContainer v-for="category in catalog.categories" :key="category.id" class="!p-2">
              <div class="flex items-center gap-2">
                <div>
                  <span class="text-sm">{{ category.name }}</span>
                  <p v-if="category.description" class="text-xs text-(--text-muted)">{{ category.description }}</p>
                </div>
                <EditButton @click="openEditCategoryModal(category)" />
                <DeleteButton @click="confirmDeleteCategory(category)" />
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Questions Section (hidden for federated) -->
        <div v-if="!isFederated" class="space-y-3">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <SectionHeader>{{ t('quiz.questions.title') }}</SectionHeader>
            <div class="flex gap-2 flex-wrap">
              <SecondaryButton @click="router.push({ name: 'quiz-catalog-import', params: { id: catalogId } })">
                <font-awesome-icon :icon="['fas', 'file-import']" class="mr-1" />
                {{ t('quiz.csv.import') }}
              </SecondaryButton>
              <SecondaryButton @click="router.push({ name: 'quiz-catalog-generate', params: { id: catalogId } })">
                <font-awesome-icon :icon="['fas', 'brain']" class="mr-1" />
                {{ t('quiz.ai.generateQuestions') }}
              </SecondaryButton>
              <PrimaryButton @click="expandNewQuestion">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
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
                :categories="catalog?.categories ?? []"
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
                    <SecondaryButton @click="expandEditQuestion(q)">
                      <font-awesome-icon :icon="['fas', 'pen']" class="mr-1" />
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
                  <div class="flex items-center gap-3 shrink-0" @click.stop>
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
                    :categories="catalog?.categories ?? []"
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
      </template>

      <!-- Category Create Modal -->
      <Modal v-model="showCategoryModal">
        <div class="space-y-4">
          <SubHeader>{{ t('quiz.categories.create') }}</SubHeader>
          <TextInput v-model="newCategoryName" :placeholder="t('quiz.categories.name')" />
          <TextAreaInput v-model="newCategoryDescription" :placeholder="t('quiz.categories.description')" />
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!newCategoryName.trim()" @click="createCategory">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Category Edit Modal -->
      <Modal v-model="showEditCategoryModal">
        <div class="space-y-4">
          <SubHeader>{{ t('quiz.categories.edit') }}</SubHeader>
          <TextInput v-model="editCategoryName" :placeholder="t('quiz.categories.name')" />
          <TextAreaInput v-model="editCategoryDescription" :placeholder="t('quiz.categories.description')" />
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showEditCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!editCategoryName.trim()" @click="updateCategory">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Category Delete Modal -->
      <Modal v-model="showDeleteCategoryModal">
        <div class="space-y-4">
          <SubHeader>{{ t('common.delete') }}</SubHeader>
          <p class="text-sm">{{ t('quiz.categories.deleteConfirm') }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteCategoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="deleteCategory">{{ t('common.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>

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
      <!-- CSV Import Dialog -->
      <CsvImportDialog
        :show="showCsvImport"
        :catalog-id="catalogId"
        :categories="catalog?.categories ?? []"
        @update:show="showCsvImport = $event"
        @imported="onCsvImported"
      />
    </div>
  </ViewContent>
</template>
