/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import {useSession} from '@/composables/useSession'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {QuizCategory, QuizQuestionTypeName} from '@/api/types'
import {QuizQuestionTypes} from '@/api/types'
import {quiz, ai, util} from '@/api'
import type {AiProviderConfig} from '@/api/ai'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

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

// Parsed questions for preview/edit
interface ImportQuestion {
  title: string
  answer: string
  category: string
  type: QuizQuestionTypeName
  points: number
  included: boolean
  answerSepOverride: string
  rawRow: string[]
  mcCorrectIndices: Set<number>
  mcPointsPerCorrect: number
  enumRequiredCount: number
  enumOrderRequired: boolean
  // Materialized split items — only recalculated on explicit re-split
  splitItems: string[]
}

const questions = ref<ImportQuestion[]>([])

// AI wrong answer generation
const generateWrongAnswers = ref(false)
const wrongAnswerCount = ref(3)
const aiProviders = ref<AiProviderConfig[]>([])
const selectedProvider = ref('')
const aiStatus = ref('')

// Import state
const importing = ref(false)
const importProgress = ref(0)
const importDone = ref(false)
const importCount = ref(0)
const error = ref('')

const parsed = computed(() => rows.value.length > 0)

async function loadData() {
  loading.value = true
  try {
    const [detail, aiSettings] = await Promise.all([
      quiz.getCatalog(catalogId.value),
      ai.getSettings().catch(() => null),
    ])
    catalogName.value = detail.name
    categories.value = detail.categories
    if (aiSettings?.providers?.length) {
      aiProviders.value = aiSettings.providers
      selectedProvider.value = aiSettings.providers[0].provider
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const parsing = ref(false)

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
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
      title,
      answer,
      category,
      type: typeStr ? parseType(typeStr) : defaultType.value,
      points: pointsStr ? parseInt(pointsStr) || 1 : 1,
      included: title.trim().length > 0,
      answerSepOverride: '',
      rawRow: row,
      mcCorrectIndices: new Set<number>(),
      mcPointsPerCorrect: 0.5,
      enumRequiredCount: 3,
      enumOrderRequired: false,
      splitItems: [],
    }
  })
}

// Rebuild fully when question or answer column changes (these are the core data)
watch([questionCol, answerCol], rebuildQuestions)

// When auxiliary mappings change, update existing questions without wiping edits
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

function removeQuestion(index: number) {
  questions.value[index].included = false
}

function restoreQuestion(index: number) {
  questions.value[index].included = true
}

const includedCount = computed(() => questions.value.filter(q => q.included).length)

const splitPresets = [
  {label: ';', value: ';'},
  {label: ',', value: ','},
  {label: '\\n', value: '\n'},
  {label: '␣', value: ' '},
]

function displayAnswer(text: string): string {
  return text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\n/g, ' ↵ ')
}

const typeOptions: { value: QuizQuestionTypeName; label: string }[] = [
  {value: QuizQuestionTypes.MULTIPLE_CHOICE, label: 'Multiple Choice'},
  {value: QuizQuestionTypes.TRUE_FALSE, label: 'Wahr/Falsch'},
  {value: QuizQuestionTypes.FREE_ANSWER, label: 'Freitext'},
  {value: QuizQuestionTypes.FILL_IN_THE_BLANK, label: 'Lückentext'},
  {value: QuizQuestionTypes.ORDERING, label: 'Reihenfolge'},
  {value: QuizQuestionTypes.ENUMERATION, label: 'Aufzählung'},
]

function getEffectiveSep(q: ImportQuestion): string {
  return q.answerSepOverride || answerSeparator.value
}

function resplit(q: ImportQuestion) {
  q.splitItems = q.answer.split(getEffectiveSep(q)).map(s => s.trim()).filter(Boolean)
  q.mcCorrectIndices = new Set()
}

function splitAnswer(q: ImportQuestion): string[] {
  if (q.splitItems.length === 0 && q.answer) {
    q.splitItems = q.answer.split(getEffectiveSep(q)).map(s => s.trim()).filter(Boolean)
  }
  return q.splitItems
}

function isMcCorrect(q: ImportQuestion, index: number): boolean {
  return q.mcCorrectIndices.size === 0 || q.mcCorrectIndices.has(index)
}

function toggleMcCorrect(q: ImportQuestion, index: number) {
  const next = new Set(q.mcCorrectIndices)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  q.mcCorrectIndices = next
}

function updateSplitItem(q: ImportQuestion, index: number, newValue: string) {
  q.splitItems[index] = newValue
}

function removeSplitItem(q: ImportQuestion, index: number) {
  q.splitItems.splice(index, 1)
  const next = new Set<number>()
  for (const i of q.mcCorrectIndices) {
    if (i < index) next.add(i)
    else if (i > index) next.add(i - 1)
  }
  q.mcCorrectIndices = next
}

function buildConfig(q: ImportQuestion): string {
  switch (q.type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const parts = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      const allCorrect = q.mcCorrectIndices.size === 0
      const options = parts.map((text, i) => ({text, correct: allCorrect || q.mcCorrectIndices.has(i)}))
      return JSON.stringify({options, pointsPerCorrect: q.mcPointsPerCorrect})
    }
    case QuizQuestionTypes.TRUE_FALSE:
      return JSON.stringify({correctAnswer: ['true', 'wahr', 'ja', '1'].includes(q.answer.trim().toLowerCase())})
    case QuizQuestionTypes.FREE_ANSWER:
      return JSON.stringify({lines: 3, answers: q.answer ? [q.answer] : []})
    case QuizQuestionTypes.FILL_IN_THE_BLANK: {
      const answers = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({text: q.title, answers})
    }
    case QuizQuestionTypes.ORDERING: {
      const items = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({items})
    }
    case QuizQuestionTypes.ENUMERATION: {
      const answers = q.splitItems.length > 0 ? q.splitItems : [q.answer]
      return JSON.stringify({answers, requiredCount: q.enumRequiredCount, orderedRequired: q.enumOrderRequired})
    }
    default:
      return '{}'
  }
}

async function findOrCreateCategory(name: string): Promise<number | null> {
  if (!name.trim()) return null
  const existing = categories.value.find(c => c.name.toLowerCase() === name.trim().toLowerCase())
  if (existing) return existing.id
  const created = await quiz.createCategory({name: name.trim(), description: '', position: categories.value.length})
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
        categoryId,
        questionType: q.type,
        title: q.title,
        description: '',
        points: q.points,
        autoPoints,
        config,
        position: i,
      })
      if (q.type === QuizQuestionTypes.MULTIPLE_CHOICE && generateWrongAnswers.value) {
        createdMcQuestions.push({question: q, id: created.id})
      }
      importProgress.value = i + 1
      importCount.value = i + 1
    }

    // Generate wrong answers for MC questions via AI
    if (createdMcQuestions.length > 0 && selectedProvider.value) {
      aiStatus.value = t('quiz.csv.generatingAnswers')
      for (let i = 0; i < createdMcQuestions.length; i++) {
        const {question: q, id: questionId} = createdMcQuestions[i]
        aiStatus.value = `${t('quiz.csv.generatingAnswers')} (${i + 1}/${createdMcQuestions.length})`
        try {
          const existingConfig = JSON.parse(buildConfig(q))
          const options = existingConfig.options || []
          const correctAnswers = options.filter((o: {correct: boolean}) => o.correct).map((o: {text: string}) => o.text)
          const wrongAnswers = await ai.generate({
            provider: selectedProvider.value,
            question: q.title,
            correctAnswer: correctAnswers.join(', '),
            count: wrongAnswerCount.value,
          })
          for (const wrong of wrongAnswers) {
            options.push({text: wrong, correct: false})
          }
          await quiz.updateQuestion(questionId, {config: {...existingConfig, options}})
        } catch {
          // Skip AI errors for individual questions
        }
      }
      aiStatus.value = ''
    }

    importDone.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    importing.value = false
  }
}

function onSeparatorChange() {
  if (csvFile.value) parseCsvFromBackend()
}

watch(loaded, (isLoaded) => {
  if (isLoaded) loadData()
}, {immediate: true})
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-3 mb-4">
      <SecondaryButton @click="router.push({name: 'quiz-catalog-detail', params: {id: catalogId}})">
        <font-awesome-icon :icon="['fas', 'chevron-left']"/>
        {{ catalogName || t('common.back') }}
      </SecondaryButton>
      <h1 class="text-xl font-bold">{{ t('quiz.csv.import') }}</h1>
    </div>

    <Spinner v-if="loading"/>
    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <Alert v-if="importDone" variant="success" class="mb-4">
      {{ t('quiz.csv.importSuccess', {count: importCount}) }}
    </Alert>

    <!-- Step 1: File Upload & Separator -->
    <NeutralContainer class="space-y-4 mb-4">
      <h2 class="font-semibold">1. {{ t('quiz.csv.selectFile') }}</h2>
      <div class="flex items-center gap-4 flex-wrap">
        <label class="inline-flex items-center gap-2 px-4 py-2 text-sm rounded-lg font-medium cursor-pointer bg-[var(--primary)] text-white hover:brightness-110 transition-all">
          <font-awesome-icon :icon="['fas', 'upload']"/>
          {{ t('quiz.csv.selectFile') }}
          <input type="file" accept=".csv,.tsv,.txt" class="hidden" @change="onFileSelected"/>
        </label>
        <span v-if="csvFile" class="text-sm text-(--text-muted)">{{ csvFile.name }}</span>
        <div class="flex items-center gap-2">
          <label class="text-xs text-(--text-muted)">{{ t('quiz.csv.separator') }}</label>
          <SelectInput v-model="separator" class="w-24" @update:model-value="onSeparatorChange">
            <option value=",">,</option>
            <option value=";">;</option>
            <option value="&#9">Tab</option>
          </SelectInput>
        </div>
      </div>
    </NeutralContainer>

    <template v-if="parsed">
      <!-- Step 2: Column Mapping -->
      <NeutralContainer class="space-y-4 mb-4">
        <h2 class="font-semibold">2. {{ t('quiz.csv.columnMapping') }}</h2>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.questionColumn') }} *</label>
            <SelectInput v-model="questionCol">
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.answerColumn') }}</label>
            <SelectInput v-model="answerCol">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.categoryColumn') }}</label>
            <SelectInput v-model="categoryCol">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.typeColumn') }}</label>
            <SelectInput v-model="typeCol">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.pointsColumn') }}</label>
            <SelectInput v-model="pointsCol">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.answerSeparator') }}</label>
            <div class="flex items-center gap-1">
              <button v-for="sep in splitPresets" :key="sep.value" type="button"
                :class="[
                  'px-2 py-1 text-xs rounded border transition-colors cursor-pointer',
                  answerSeparator === sep.value
                    ? 'border-[var(--primary)] bg-[var(--primary)]/15 text-[var(--primary)] font-semibold'
                    : 'border-[var(--border)] text-(--text-muted) hover:border-[var(--primary)]',
                ]"
                @click="answerSeparator = sep.value; questions.forEach(q => { if (!q.answerSepOverride) resplit(q) })"
              >{{ sep.label }}</button>
            </div>
          </div>
        </div>
        <div v-if="!typeCol">
          <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.defaultType') }}</label>
          <SelectInput v-model="defaultType" class="w-64">
            <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </SelectInput>
        </div>
      </NeutralContainer>

      <!-- AI Wrong Answers (optional) -->
      <NeutralContainer v-if="aiProviders.length > 0" class="space-y-3 mb-4">
        <div class="flex items-center gap-3">
          <ToggleInput v-model="generateWrongAnswers"/>
          <span class="text-sm font-medium">{{ t('quiz.csv.generateWrongAnswers') }}</span>
        </div>
        <div v-if="generateWrongAnswers" class="flex items-center gap-4 flex-wrap">
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.csv.wrongAnswerCount') }}</label>
            <NumberInput v-model="wrongAnswerCount" :min="1" :max="10" class="w-20"/>
          </div>
          <div>
            <label class="text-xs text-(--text-muted) block mb-1">AI Provider</label>
            <SelectInput v-model="selectedProvider" class="w-48">
              <option v-for="p in aiProviders" :key="p.provider" :value="p.provider">{{ p.provider }}</option>
            </SelectInput>
          </div>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('quiz.csv.generateWrongAnswersHint') }}</p>
      </NeutralContainer>

      <!-- Step 3: Preview & Edit -->
      <NeutralContainer class="space-y-4 mb-4">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <h2 class="font-semibold">3. {{ t('quiz.csv.preview') }} ({{ includedCount }} / {{ questions.length }})</h2>
          <div class="flex items-center gap-3">
            <span v-if="aiStatus" class="text-xs text-(--text-muted)">
              <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1"/>
              {{ aiStatus }}
            </span>
            <PrimaryButton :disabled="importing || includedCount === 0 || importDone" @click="doImport">
              <font-awesome-icon :icon="['fas', importing ? 'spinner' : 'file-import']" :spin="importing"/>
              <template v-if="importing">{{ importProgress }} / {{ includedCount }}</template>
              <template v-else>{{ t('quiz.csv.importButton') }} ({{ includedCount }})</template>
            </PrimaryButton>
          </div>
        </div>

        <div class="space-y-3">
          <div
            v-for="(q, idx) in questions"
            :key="idx"
            :class="[
              'rounded-lg border p-3 transition-all',
              q.included
                ? 'border-[var(--border)] bg-[var(--bg)]'
                : 'border-[var(--border)] bg-[var(--bg-accent)] opacity-50',
            ]"
          >
            <div class="flex items-start gap-3">
              <span class="text-xs text-(--text-muted) font-mono mt-1 shrink-0">{{ idx + 1 }}</span>

              <div class="flex-1 min-w-0 space-y-2">
                <!-- Row 1: Type, Category, Points, Separator -->
                <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  <div>
                    <label class="text-[10px] text-(--text-muted) block">Typ</label>
                    <SelectInput v-model="q.type" :disabled="!q.included" class="!text-xs">
                      <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                    </SelectInput>
                  </div>
                  <div>
                    <label class="text-[10px] text-(--text-muted) block">Kategorie</label>
                    <TextInput v-model="q.category" :disabled="!q.included" class="!text-xs"/>
                  </div>
                  <div>
                    <label class="text-[10px] text-(--text-muted) block">Punkte</label>
                    <TextInput
                      :model-value="String(q.points)"
                      :disabled="!q.included"
                      class="!text-xs"
                      @update:model-value="q.points = parseInt($event ?? '1') || 1"
                    />
                  </div>
                  <div v-if="q.type === QuizQuestionTypes.ENUMERATION || q.type === QuizQuestionTypes.ORDERING || q.type === QuizQuestionTypes.MULTIPLE_CHOICE || q.type === QuizQuestionTypes.CONNECT">
                    <label class="text-[10px] text-(--text-muted) block">Trennzeichen</label>
                    <div class="flex items-center gap-1">
                      <button v-for="sep in splitPresets" :key="sep.value" type="button"
                        :class="[
                          'px-1.5 py-0.5 text-[10px] rounded border transition-colors cursor-pointer',
                          q.answerSepOverride === sep.value || (!q.answerSepOverride && answerSeparator === sep.value)
                            ? 'border-[var(--primary)] bg-[var(--primary)]/15 text-[var(--primary)]'
                            : 'border-[var(--border)] text-(--text-muted) hover:border-[var(--primary)]',
                        ]"
                        :disabled="!q.included"
                        @click="q.answerSepOverride = sep.value; resplit(q)"
                      >{{ sep.label }}</button>
                    </div>
                  </div>
                </div>

                <!-- Row 2: Title -->
                <div>
                  <label class="text-[10px] text-(--text-muted) block">Frage</label>
                  <TextAreaInput
                    v-model="q.title"
                    :disabled="!q.included"
                    class="!text-sm !min-h-0"
                    :rows="1"
                  />
                </div>

                <!-- Row 3: Answer (editable raw + display) -->
                <div>
                  <label class="text-[10px] text-(--text-muted) block">Antwort</label>
                  <div v-if="q.answer.includes('\n')" class="text-xs bg-[var(--bg-accent)] rounded px-2 py-1 mb-1 font-mono whitespace-pre-wrap">{{ displayAnswer(q.answer) }}</div>
                  <TextAreaInput v-model="q.answer" :disabled="!q.included" class="!text-xs !min-h-0" :rows="q.answer.includes('\n') ? 2 : 1"/>
                </div>

                <!-- Row 4: Type-specific controls & preview -->
                <div v-if="q.included && q.answer" class="text-xs text-(--text-muted) bg-(--bg-accent) rounded p-2 space-y-2">

                  <!-- Multiple Choice: click answers to toggle correct, set points per correct -->
                  <template v-if="q.type === QuizQuestionTypes.MULTIPLE_CHOICE">
                    <div class="flex items-center gap-2 mb-1">
                      <span class="text-[10px]">Punkte pro richtige Antwort:</span>
                      <TextInput
                        :model-value="String(q.mcPointsPerCorrect)"
                        class="!text-xs w-16"
                        @update:model-value="q.mcPointsPerCorrect = parseFloat($event ?? '0.5') || 0.5"
                      />
                    </div>
                    <div v-for="(opt, oi) in splitAnswer(q)" :key="oi" class="flex items-center gap-1">
                      <button type="button" class="shrink-0 cursor-pointer" @click="toggleMcCorrect(q, oi)">
                        <span :class="isMcCorrect(q, oi) ? 'text-(--success) font-semibold' : 'text-(--error)'">
                          {{ isMcCorrect(q, oi) ? '✓' : '✗' }}
                        </span>
                      </button>
                      <input
                        :value="opt"
                        class="flex-1 bg-transparent border-b border-(--border) text-xs px-1 py-0.5 focus:outline-none focus:border-(--primary)"
                        @change="updateSplitItem(q, oi, ($event.target as HTMLInputElement).value)"
                      />
                      <button type="button" class="text-(--error) hover:text-(--error)/80 cursor-pointer text-[10px] shrink-0" @click="removeSplitItem(q, oi)">
                        <font-awesome-icon :icon="['fas', 'xmark']"/>
                      </button>
                    </div>
                    <div v-if="q.mcCorrectIndices.size === 0" class="text-[10px] italic">Alle Antworten als richtig markiert</div>
                  </template>

                  <!-- Enumeration: set required count -->
                  <template v-else-if="q.type === QuizQuestionTypes.ENUMERATION">
                    <div class="flex items-center gap-2 mb-1 flex-wrap">
                      <span class="text-[10px]">Geforderte Antworten:</span>
                      <TextInput
                        :model-value="String(q.enumRequiredCount)"
                        class="!text-xs w-16"
                        @update:model-value="q.enumRequiredCount = parseInt($event ?? '3') || 3"
                      />
                      <span class="text-[10px]">von {{ splitAnswer(q).length }} möglichen</span>
                      <label class="flex items-center gap-1 text-[10px] cursor-pointer ml-2">
                        <input type="checkbox" v-model="q.enumOrderRequired" class="accent-(--primary)"/>
                        Reihenfolge relevant
                      </label>
                    </div>
                    <div v-for="(item, ii) in splitAnswer(q)" :key="ii" class="flex items-center gap-1">
                      <span class="text-[10px] w-4 shrink-0">{{ ii + 1 }}.</span>
                      <input
                        :value="item"
                        class="flex-1 bg-transparent border-b border-(--border) text-xs px-1 py-0.5 focus:outline-none focus:border-(--primary)"
                        @change="updateSplitItem(q, ii, ($event.target as HTMLInputElement).value)"
                      />
                      <button type="button" class="text-(--error) hover:text-(--error)/80 cursor-pointer text-[10px] shrink-0" @click="removeSplitItem(q, ii)">
                        <font-awesome-icon :icon="['fas', 'xmark']"/>
                      </button>
                    </div>
                  </template>

                  <template v-else-if="q.type === QuizQuestionTypes.ORDERING">
                    <div v-for="(item, ii) in splitAnswer(q)" :key="ii" class="flex items-center gap-1">
                      <span class="text-[10px] w-4 shrink-0">{{ ii + 1 }}.</span>
                      <input
                        :value="item"
                        class="flex-1 bg-transparent border-b border-(--border) text-xs px-1 py-0.5 focus:outline-none focus:border-(--primary)"
                        @change="updateSplitItem(q, ii, ($event.target as HTMLInputElement).value)"
                      />
                      <button type="button" class="text-(--error) hover:text-(--error)/80 cursor-pointer text-[10px] shrink-0" @click="removeSplitItem(q, ii)">
                        <font-awesome-icon :icon="['fas', 'xmark']"/>
                      </button>
                    </div>
                  </template>
                  <template v-else-if="q.type === QuizQuestionTypes.TRUE_FALSE">
                    {{ ['true','wahr','ja','1'].includes(q.answer.trim().toLowerCase()) ? 'Wahr' : 'Falsch' }}
                  </template>
                  <template v-else>
                    {{ q.answer }}
                  </template>
                </div>

                <!-- Raw CSV data -->
                <details v-if="q.rawRow.length > 0" class="text-[10px] text-(--text-muted)">
                  <summary class="cursor-pointer">CSV-Rohdaten</summary>
                  <div class="flex gap-2 flex-wrap mt-1">
                    <span v-for="(cell, ci) in q.rawRow" :key="ci" class="bg-[var(--bg-accent)] px-1 rounded">
                      {{ headers[ci] }}: {{ displayAnswer(cell) }}
                    </span>
                  </div>
                </details>
              </div>

              <div class="shrink-0">
                <DeleteButton
                  v-if="q.included"
                  :label="t('common.delete')"
                  @click="removeQuestion(idx)"
                />
                <SecondaryButton
                  v-else
                  @click="restoreQuestion(idx)"
                >
                  <font-awesome-icon :icon="['fas', 'rotate']"/>
                </SecondaryButton>
              </div>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
