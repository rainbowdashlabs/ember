/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { useSession } from '@/composables/useSession'
import type { QuizCategory, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz, ai, util } from '@/api'
import { getItem } from '@/api/storage'
import CsvFileUpload from './csvimportview/CsvFileUpload.vue'
import CsvColumnMapping from './csvimportview/CsvColumnMapping.vue'
import CsvQuestionCard from './csvimportview/CsvQuestionCard.vue'
import type { ImportQuestion } from './csvimportview/CsvQuestionCard.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const { loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))
const categories = ref<QuizCategory[]>([])
const loading = ref(true)
const catalogName = ref('')

// CSV parsing
const csvFile = ref<File | null>(null)
const headers = ref<string[]>([])
const rows = ref<string[][]>([])
const separator = ref(',')

// Column mappings
const questionCol = ref('')
const answerCol = ref('')
const categoryCol = ref('')
const typeCol = ref('')
const pointsCol = ref('')
const answerSeparator = ref(';')
const defaultType = ref<QuizQuestionTypeName>(QuizQuestionTypes.MULTIPLE_CHOICE)

const questions = ref<ImportQuestion[]>([])

// AI wrong answer generation
const generateWrongAnswers = ref(false)
const wrongAnswerCount = ref(3)
const aiPrompt = ref('')
const aiStatus = ref('')
const hasAiKey = computed(() => !!(getItem('ai_api_key')))

// Import state
const importing = ref(false)
const importProgress = ref(0)
const importDone = ref(false)
const importCount = ref(0)
const error = ref('')

const parsed = computed(() => rows.value.length > 0)

const typeOptions: { value: QuizQuestionTypeName; label: string }[] = [
  { value: QuizQuestionTypes.MULTIPLE_CHOICE, label: 'Multiple Choice' },
  { value: QuizQuestionTypes.TRUE_FALSE, label: 'Wahr/Falsch' },
  { value: QuizQuestionTypes.FREE_ANSWER, label: 'Freitext' },
  { value: QuizQuestionTypes.FILL_IN_THE_BLANK, label: 'Lückentext' },
  { value: QuizQuestionTypes.ORDERING, label: 'Reihenfolge' },
  { value: QuizQuestionTypes.ENUMERATION, label: 'Aufzählung' },
]

const splitPresets = [
  { label: ';', value: ';' },
  { label: ',', value: ',' },
  { label: '\\n', value: '\n' },
  { label: '\u2423', value: ' ' },
]

async function loadData() {
  loading.value = true
  try {
    const detail = await quiz.getCatalog(catalogId.value)
    catalogName.value = detail.name
    categories.value = detail.categories
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const parsing = ref(false)

async function onFileSelected(file: File) {
  csvFile.value = file
  error.value = ''
  importDone.value = false
  await parseCsvFromBackend()
}

async function parseCsvFromBackend() {
  if (!csvFile.value) return
  parsing.value = true
  error.value = ''
  try {
    const result = await util.parseCsv(csvFile.value, separator.value)
    headers.value = result.headers
    rows.value = result.rows
    if (headers.value.length > 0) {
      questionCol.value = headers.value[0]
      answerCol.value = headers.value.length > 1 ? headers.value[1] : ''
      categoryCol.value = ''
      typeCol.value = ''
      pointsCol.value = ''
    }
    rebuildQuestions()
  } catch {
    error.value = 'CSV konnte nicht gelesen werden.'
  } finally {
    parsing.value = false
  }
}

function colIndex(name: string): number {
  return headers.value.indexOf(name)
}

function cellValue(row: string[], col: string): string {
  const idx = colIndex(col)
  return idx >= 0 && idx < row.length ? row[idx] : ''
}

function parseType(val: string): QuizQuestionTypeName {
  const v = val.trim().toUpperCase().replace(/[\s-]/g, '_')
  if (v === 'MC' || v.includes('MULTIPLE')) return QuizQuestionTypes.MULTIPLE_CHOICE
  if (v === 'TF' || v.includes('TRUE') || v.includes('WAHR')) return QuizQuestionTypes.TRUE_FALSE
  if (v.includes('FREE') || v.includes('FREI')) return QuizQuestionTypes.FREE_ANSWER
  if (v.includes('FILL') || v.includes('LÜCKE') || v.includes('LUECKE')) return QuizQuestionTypes.FILL_IN_THE_BLANK
  if (v.includes('ORDER') || v.includes('REIHEN')) return QuizQuestionTypes.ORDERING
  if (v.includes('CONNECT') || v.includes('ZUORDN')) return QuizQuestionTypes.CONNECT
  return defaultType.value
}

function rebuildQuestions() {
  questions.value = rows.value.map(row => {
    const title = cellValue(row, questionCol.value)
    const answer = answerCol.value ? cellValue(row, answerCol.value) : ''
    const category = categoryCol.value ? cellValue(row, categoryCol.value) : ''
    const typeStr = typeCol.value ? cellValue(row, typeCol.value) : ''
    const pointsStr = pointsCol.value ? cellValue(row, pointsCol.value) : ''
    return {
      title, answer, category,
      type: typeStr ? parseType(typeStr) : defaultType.value,
      points: pointsStr ? parseInt(pointsStr) || 1 : 1,
      included: title.trim().length > 0,
      answerSepOverride: '',
      rawRow: row,
      mcCorrectIndices: new Set<number>(),
      mcPointsPerCorrect: 1,
      enumRequiredCount: 3,
      enumOrderRequired: false,
      splitItems: [],
    }
  })
}

watch([questionCol, answerCol], rebuildQuestions)

watch([categoryCol, typeCol, pointsCol, defaultType], () => {
  for (let i = 0; i < questions.value.length && i < rows.value.length; i++) {
    const row = rows.value[i]
    const q = questions.value[i]
    if (categoryCol.value) q.category = cellValue(row, categoryCol.value)
    if (pointsCol.value) q.points = parseInt(cellValue(row, pointsCol.value)) || 1
    if (typeCol.value) {
      const typeStr = cellValue(row, typeCol.value)
      q.type = typeStr ? parseType(typeStr) : defaultType.value
    } else {
      q.type = defaultType.value
    }
  }
})

function getEffectiveSep(q: ImportQuestion): string {
  return q.answerSepOverride || answerSeparator.value
}

function resplit(index: number) {
  const q = questions.value[index]
  q.splitItems = q.answer.split(getEffectiveSep(q)).map(s => s.trim()).filter(Boolean)
  q.mcCorrectIndices = new Set()
}

function resplitAll() {
  questions.value.forEach((q, i) => {
    if (!q.answerSepOverride) resplit(i)
  })
}

function setSeparatorOverride(index: number, value: string) {
  questions.value[index].answerSepOverride = value
}

function toggleMcCorrect(index: number, optionIndex: number) {
  const q = questions.value[index]
  const next = new Set(q.mcCorrectIndices)
  if (next.has(optionIndex)) next.delete(optionIndex)
  else next.add(optionIndex)
  q.mcCorrectIndices = next
}

function updateSplitItem(index: number, optionIndex: number, newValue: string) {
  questions.value[index].splitItems[optionIndex] = newValue
}

function removeSplitItem(index: number, optionIndex: number) {
  const q = questions.value[index]
  q.splitItems.splice(optionIndex, 1)
  const next = new Set<number>()
  for (const i of q.mcCorrectIndices) {
    if (i < optionIndex) next.add(i)
    else if (i > optionIndex) next.add(i - 1)
  }
  q.mcCorrectIndices = next
}

const includedCount = computed(() => questions.value.filter(q => q.included).length)

function buildConfig(q: ImportQuestion): string {
  switch (q.type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const parts = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      const allCorrect = q.mcCorrectIndices.size === 0
      const options = parts.map((text, i) => ({ text, correct: allCorrect || q.mcCorrectIndices.has(i) }))
      return JSON.stringify({ options, pointsPerCorrect: q.mcPointsPerCorrect })
    }
    case QuizQuestionTypes.TRUE_FALSE:
      return JSON.stringify({ correctAnswer: ['true', 'wahr', 'ja', '1'].includes(q.answer.trim().toLowerCase()) })
    case QuizQuestionTypes.FREE_ANSWER:
      return JSON.stringify({ lines: 3, answers: q.answer ? [q.answer] : [] })
    case QuizQuestionTypes.FILL_IN_THE_BLANK: {
      const answers = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({ text: q.title, answers })
    }
    case QuizQuestionTypes.ORDERING: {
      const items = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({ items })
    }
    case QuizQuestionTypes.ENUMERATION: {
      const answers = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({ answers, requiredCount: q.enumRequiredCount, orderedRequired: q.enumOrderRequired })
    }
    default:
      return '{}'
  }
}

async function findOrCreateCategory(name: string): Promise<number | null> {
  if (!name.trim()) return null
  const existing = categories.value.find(c => c.name.toLowerCase() === name.trim().toLowerCase())
  if (existing) return existing.id
  const created = await quiz.createCategory({ name: name.trim(), description: '', position: categories.value.length })
  categories.value.push(created)
  return created.id
}

async function doImport() {
  importing.value = true
  importProgress.value = 0
  importCount.value = 0
  aiStatus.value = ''
  error.value = ''
  const toImport = questions.value.filter(q => q.included)
  try {
    const createdMcQuestions: { question: typeof toImport[0]; id: number }[] = []
    for (let i = 0; i < toImport.length; i++) {
      const q = toImport[i]
      const categoryId = await findOrCreateCategory(q.category)
      const config = buildConfig(q)
      const autoPoints = q.type !== QuizQuestionTypes.FREE_ANSWER && q.type !== QuizQuestionTypes.IMAGE_TEXT
      const created = await quiz.createQuestion(catalogId.value, {
        categoryId, questionType: q.type, title: q.title,
        description: '', points: q.points, autoPoints, config, position: i,
      })
      if (q.type === QuizQuestionTypes.MULTIPLE_CHOICE && generateWrongAnswers.value) {
        createdMcQuestions.push({ question: q, id: created.id })
      }
      importProgress.value = i + 1
      importCount.value = i + 1
    }

    if (createdMcQuestions.length > 0 && generateWrongAnswers.value) {
      const provider = getItem('ai_provider') || 'openai'
      const apiKey = getItem('ai_api_key') || ''
      const model = getItem('ai_model') || ''
      if (apiKey) {
        aiStatus.value = t('quiz.csv.generatingAnswers')
        for (let i = 0; i < createdMcQuestions.length; i++) {
          const { question: q, id: questionId } = createdMcQuestions[i]
          aiStatus.value = `${t('quiz.csv.generatingAnswers')} (${i + 1}/${createdMcQuestions.length})`
          try {
            const existingConfig = JSON.parse(buildConfig(q))
            const options = existingConfig.options || []
            const correctAnswers = options.filter((o: { correct: boolean }) => o.correct).map((o: { text: string }) => o.text)
            const context = aiPrompt.value
                ? `${aiPrompt.value}\nKatalog: ${catalogName.value}`
                : `Katalog: ${catalogName.value}`
            const wrongAnswers = await ai.generate({
              provider, apiKey, model: model || null,
              question: `${context}\n\n${q.title}`,
              correctAnswer: correctAnswers.join(', '), count: wrongAnswerCount.value,
            })
            for (const wrong of wrongAnswers) {
              options.push({ text: wrong, correct: false })
            }
            await quiz.updateQuestion(questionId, { config: { ...existingConfig, options } })
          } catch { /* skip AI errors */ }
        }
        aiStatus.value = ''
      }
    }

    importDone.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    importing.value = false
  }
}

function onSeparatorChange(value: string) {
  separator.value = value
  if (csvFile.value) parseCsvFromBackend()
}

watch(loaded, (isLoaded) => {
  if (isLoaded) loadData()
}, { immediate: true })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'quiz-catalog-detail', params: { id: catalogId } })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
        {{ catalogName || t('common.back') }}
      </SecondaryButton>
      <SubHeader>{{ t('quiz.csv.import') }}</SubHeader>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
    <Alert v-if="importDone" variant="success" class="mb-4">
      {{ t('quiz.csv.importSuccess', { count: importCount }) }}
    </Alert>

    <!-- Step 1: File Upload -->
    <CsvFileUpload
      :file-name="csvFile?.name ?? null"
      :separator="separator"
      @file-selected="onFileSelected"
      @update:separator="onSeparatorChange"
    />

    <template v-if="parsed">
      <!-- Step 2: Column Mapping -->
      <CsvColumnMapping
        :headers="headers"
        :question-col="questionCol"
        :answer-col="answerCol"
        :category-col="categoryCol"
        :type-col="typeCol"
        :points-col="pointsCol"
        :answer-separator="answerSeparator"
        :default-type="defaultType"
        :type-options="typeOptions"
        :split-presets="splitPresets"
        @update:question-col="questionCol = $event"
        @update:answer-col="answerCol = $event"
        @update:category-col="categoryCol = $event"
        @update:type-col="typeCol = $event"
        @update:points-col="pointsCol = $event"
        @update:answer-separator="answerSeparator = $event"
        @update:default-type="defaultType = $event"
        @resplit-all="resplitAll"
      />

      <!-- AI Wrong Answers -->
      <NeutralContainer v-if="hasAiKey" class="space-y-3 mb-4">
        <div class="flex items-center gap-2">
          <ToggleInput v-model="generateWrongAnswers" />
          <span class="text-sm font-medium">{{ t('quiz.csv.generateWrongAnswers') }}</span>
        </div>
        <template v-if="generateWrongAnswers">
          <div class="flex items-center gap-4 flex-wrap">
            <div>
              <FieldLabel hint class="mb-1">{{ t('quiz.csv.wrongAnswerCount') }}</FieldLabel>
              <NumberInput v-model="wrongAnswerCount" :min="1" :max="10" class="w-20" />
            </div>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.ai.additionalPrompt') }}</FieldLabel>
            <TextInput v-model="aiPrompt" :placeholder="t('quiz.ai.additionalPromptPlaceholder')" />
          </div>
        </template>
        <p class="text-xs text-(--text-muted)">{{ t('quiz.csv.generateWrongAnswersHint') }}</p>
      </NeutralContainer>

      <!-- Step 3: Preview -->
      <NeutralContainer class="space-y-4 mb-4">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <SectionHeader>3. {{ t('quiz.csv.preview') }} ({{ includedCount }} / {{ questions.length }})</SectionHeader>
          <div class="flex items-center gap-2">
            <span v-if="aiStatus" class="text-xs text-(--text-muted)">
              <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1" />
              {{ aiStatus }}
            </span>
            <PrimaryButton :disabled="importing || includedCount === 0 || importDone" @click="doImport">
              <font-awesome-icon :icon="['fas', importing ? 'spinner' : 'file-import']" :spin="importing" />
              <template v-if="importing">{{ importProgress }} / {{ includedCount }}</template>
              <template v-else>{{ t('quiz.csv.importButton') }} ({{ includedCount }})</template>
            </PrimaryButton>
          </div>
        </div>

        <div class="space-y-3">
          <CsvQuestionCard
            v-for="(q, idx) in questions"
            :key="idx"
            :question="q"
            :index="idx"
            :headers="headers"
            :answer-separator="answerSeparator"
            :type-options="typeOptions"
            :split-presets="splitPresets"
            @remove="questions[$event].included = false"
            @restore="questions[$event].included = true"
            @resplit="resplit($event)"
            @set-separator-override="setSeparatorOverride"
            @toggle-mc-correct="toggleMcCorrect"
            @update-split-item="updateSplitItem"
            @remove-split-item="removeSplitItem"
          />
        </div>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
