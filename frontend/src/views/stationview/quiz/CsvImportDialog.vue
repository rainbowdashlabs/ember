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
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { QuizCategory, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import { quiz } from '@/api'
import type { CsvMappings } from '@/api/quiz'
import { useAsyncAction } from '@/composables/useAsyncAction'
import CsvFileUploadBar from '@/views/stationview/quiz/csvimportdialog/CsvFileUploadBar.vue'
import CsvColumnMapping from '@/views/stationview/quiz/csvimportdialog/CsvColumnMapping.vue'
import CsvPreviewTable from '@/views/stationview/quiz/csvimportdialog/CsvPreviewTable.vue'

const { t } = useI18n()

const show = defineModel<boolean>('show', {required: true})

const props = defineProps<{
  catalogId: number
  categories: QuizCategory[]
}>()

const emit = defineEmits<{
  imported: [count: number]
}>()

const csvFile = ref<File | null>(null)
const headers = ref<string[]>([])
const rows = ref<string[][]>([])
const separator = ref(',')
const successCount = ref<number | null>(null)

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
  clearError()
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

const {running: importing, error, run: runImport, clearError} = useAsyncAction(async (file: File) => {
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
  const result = await quiz.importCsv(props.catalogId, file, mappings)
  successCount.value = result.imported
  emit('imported', result.imported)
}, {formatError: () => t('common.error')})

async function doImport() {
  if (!csvFile.value || !questionColumn.value) return
  successCount.value = null
  await runImport(csvFile.value)
}

function close() {
  show.value = false
  csvFile.value = null
  headers.value = []
  rows.value = []
  successCount.value = null
  clearError()
}
</script>

<template>
  <Modal :model-value="show" @update:model-value="(v: boolean) => { if (!v) close() }">
    <div class="space-y-4 max-h-[70vh] overflow-y-auto">
      <SubHeader>{{ t('quiz.csv.import') }}</SubHeader>

      <Alert v-if="successCount !== null" variant="success">
        {{ t('quiz.csv.importSuccess', { count: successCount }) }}
      </Alert>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <CsvFileUploadBar
        v-model:separator="separator"
        @file-selected="onFileSelected"
        @separator-changed="onSeparatorChange"
      />

      <template v-if="parsed">
        <CsvColumnMapping
          v-model:question-column="questionColumn"
          v-model:answer-column="answerColumn"
          v-model:category-column="categoryColumn"
          v-model:type-column="typeColumn"
          v-model:points-column="pointsColumn"
          v-model:default-type="defaultType"
          v-model:answer-separator="answerSeparator"
          :headers="headers"
        />

        <CsvPreviewTable :headers="headers" :rows="rows" />

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
