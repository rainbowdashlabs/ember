/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type { QuizCategory, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import type { CsvMappings } from '@/api/quiz'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()

const props = defineProps<{
  show: boolean
  catalogId: number
  categories: QuizCategory[]
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  imported: [count: number]
}>()

const csvFile = ref<File | null>(null)
const headers = ref<string[]>([])
const rows = ref<string[][]>([])
const separator = ref(',')
const importing = ref(false)
const successCount = ref<number | null>(null)
const error = ref('')

// Column mappings
const questionColumn = ref('')
const answerColumn = ref('')
const categoryColumn = ref('')
const typeColumn = ref('')
const pointsColumn = ref('')
const defaultType = ref<QuizQuestionTypeName>(QuizQuestionTypes.MULTIPLE_CHOICE)
const answerSeparator = ref(';')

const parsed = computed(() => rows.value.length > 0)

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  csvFile.value = file
  successCount.value = null
  error.value = ''
  const reader = new FileReader()
  reader.onload = (e) => {
    parseCSV(e.target?.result as string)
  }
  reader.readAsText(file)
}

function parseCSV(text: string) {
  const lines = text.trim().split('\n')
  if (lines.length < 2) return
  const sep = separator.value
  headers.value = parseCsvLine(lines[0], sep)
  rows.value = lines.slice(1).filter(l => l.trim()).map(line => parseCsvLine(line, sep))
  if (headers.value.length > 0) {
    questionColumn.value = headers.value[0]
    answerColumn.value = headers.value.length > 1 ? headers.value[1] : ''
    categoryColumn.value = ''
    typeColumn.value = ''
    pointsColumn.value = ''
  }
}

function parseCsvLine(line: string, sep: string): string[] {
  const cells: string[] = []
  let current = ''
  let inQuotes = false
  for (const char of line) {
    if (char === '"') { inQuotes = !inQuotes; continue }
    if (char === sep.charAt(0) && !inQuotes) { cells.push(current.trim()); current = ''; continue }
    current += char
  }
  cells.push(current.trim())
  return cells
}

function onSeparatorChange() {
  if (csvFile.value) {
    const reader = new FileReader()
    reader.onload = (e) => {
      parseCSV(e.target?.result as string)
    }
    reader.readAsText(csvFile.value)
  }
}

async function doImport() {
  if (!csvFile.value || !questionColumn.value) return
  importing.value = true
  error.value = ''
  successCount.value = null
  try {
    const mappings: CsvMappings = {
      questionColumn: questionColumn.value,
      answerColumn: answerColumn.value,
      categoryColumn: categoryColumn.value,
      typeColumn: typeColumn.value,
      pointsColumn: pointsColumn.value,
      separator: separator.value,
      answerSeparator: answerSeparator.value,
      defaultType: defaultType.value,
    }
    const result = await quiz.importCsv(props.catalogId, csvFile.value, mappings)
    successCount.value = result.imported
    emit('imported', result.imported)
  } catch {
    error.value = t('common.error')
  } finally {
    importing.value = false
  }
}

function close() {
  emit('update:show', false)
  csvFile.value = null
  headers.value = []
  rows.value = []
  successCount.value = null
  error.value = ''
}

const previewRows = computed(() => rows.value.slice(0, 5))
</script>

<template>
  <Modal :model-value="show" @update:model-value="(v: boolean) => { if (!v) close() }">
    <div class="space-y-4 max-h-[70vh] overflow-y-auto">
      <SubHeader>{{ t('quiz.csv.import') }}</SubHeader>

      <Alert v-if="successCount !== null" variant="success">
        {{ t('quiz.csv.importSuccess', { count: successCount }) }}
      </Alert>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <!-- File upload -->
      <div class="flex items-center gap-2">
        <label class="inline-flex items-center gap-2 px-3 py-1.5 text-sm rounded-lg font-medium cursor-pointer bg-bg-light-accent dark:bg-bg-dark-accent hover:brightness-110 transition-all">
          <font-awesome-icon :icon="['fas', 'upload']" />
          {{ t('quiz.csv.selectFile') }}
          <input type="file" accept=".csv,.tsv,.txt" class="hidden" @change="onFileSelected" />
        </label>
        <div class="flex items-center gap-2">
          <FieldHint>{{ t('quiz.csv.separator') }}</FieldHint>
          <SelectInput v-model="separator" class="w-24" @update:model-value="onSeparatorChange">
            <option value=",">,</option>
            <option value=";">;</option>
            <option value="&#9">Tab</option>
          </SelectInput>
        </div>
      </div>

      <template v-if="parsed">
        <!-- Column mapping -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.csv.questionColumn') }}</FieldLabel>
            <SelectInput v-model="questionColumn" class="w-full">
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.csv.answerColumn') }}</FieldLabel>
            <SelectInput v-model="answerColumn" class="w-full">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.csv.categoryColumn') }}</FieldLabel>
            <SelectInput v-model="categoryColumn" class="w-full">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.csv.typeColumn') }}</FieldLabel>
            <SelectInput v-model="typeColumn" class="w-full">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.csv.pointsColumn') }}</FieldLabel>
            <SelectInput v-model="pointsColumn" class="w-full">
              <option value="">–</option>
              <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
            </SelectInput>
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.questions.type') }} ({{ t('quiz.csv.defaultType') }})</FieldLabel>
            <SelectInput v-model="defaultType" class="w-full">
              <option :value="QuizQuestionTypes.MULTIPLE_CHOICE">{{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</option>
              <option :value="QuizQuestionTypes.FREE_ANSWER">{{ t('quiz.questionTypes.FREE_ANSWER') }}</option>
              <option :value="QuizQuestionTypes.TRUE_FALSE">{{ t('quiz.questionTypes.TRUE_FALSE') }}</option>
              <option :value="QuizQuestionTypes.FILL_IN_THE_BLANK">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</option>
              <option :value="QuizQuestionTypes.ORDERING">{{ t('quiz.questionTypes.ORDERING') }}</option>
              <option :value="QuizQuestionTypes.CONNECT">{{ t('quiz.questionTypes.CONNECT') }}</option>
            </SelectInput>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <FieldHint>{{ t('quiz.csv.answerSeparator') }}</FieldHint>
          <TextInput v-model="answerSeparator" class="w-16" />
        </div>

        <!-- Preview -->
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.csv.preview') }} ({{ rows.length }} {{ t('quiz.csv.rows') }})</label>
          <div class="overflow-x-auto">
            <table class="text-xs w-full">
              <thead>
                <tr>
                  <th v-for="h in headers" :key="h" class="text-left px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in previewRows" :key="ri">
                  <td v-for="(cell, ci) in row" :key="ci" class="px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent truncate max-w-48">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'file-import']" :disabled="!questionColumn || importing" @click="doImport">
            <template v-if="importing">{{ t('common.loading') }}...</template>
            <template v-else>{{ t('quiz.csv.importButton') }} ({{ rows.length }})</template>
          </PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>
