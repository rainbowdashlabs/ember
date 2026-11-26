/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {BatchRow, EventFieldEntry} from '@/api/generated/schema'
import {events} from '@/api'
import {readCsvText} from '@/util/csvText'
import {describeFailure, type Failure} from '@/util/failure'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import CsvDateFormatSelect from '@/components/csv/CsvDateFormatSelect.vue'
import type {CsvDateFormat} from '@/util/csvDate'
import Alert from '@/components/feedback/Alert.vue'
import {
  CsvTarget,
  csvToBatchRows,
  guessColumnMapping,
  readCsvTable,
  type CsvProblem,
  type CsvTable,
} from './csvSchedule'
import ManualRowsEditor from './ManualRowsEditor.vue'
import type {KeyedDraftRow, ScheduleMode} from './draftRows'

const props = defineProps<{
  fieldDefs: EventFieldEntry[]
}>()

const emit = defineEmits<{
  done: [rows: BatchRow[]]
  error: [failure: Failure]
}>()

const {t} = useI18n()

/** The way the dates are entered, kept by the page so going back from the table returns to it. */
const mode = defineModel<ScheduleMode>('mode', {required: true})
/** The rows written by hand, kept by the page so going back from the table shows them as typed. */
const drafts = defineModel<KeyedDraftRow[]>('drafts', {required: true})

const modes = computed<{value: ScheduleMode, label: string}[]>(() => [
  {value: 'schedule', label: t('batchCreate.schedule')},
  {value: 'csv', label: t('batchCreate.csvImport')},
  {value: 'manual', label: t('batchCreate.manualEntry')},
])

const intervalType = ref('RECURRING')
const dayOfWeek = ref(1)
const startDate = ref('')
const endDate = ref('')
const startTime = ref('')
const endTime = ref('')
const ignoreBreaks = ref(false)

const csvFileName = ref('')
const csvEmpty = ref(false)
const csvTable = ref<CsvTable>({columns: [], lines: []})
const csvColumns = computed(() => csvTable.value.columns)
const csvProblems = ref<CsvProblem[]>([])
const columnMapping = ref<Record<string, string>>({})
const csvDateFormat = ref<CsvDateFormat>('auto')
const csvFallbackStartTime = ref('09:00')
const csvFallbackEndTime = ref('17:00')
const csvHasTimeColumns = ref(false)

const needsDow = computed(() => ['RECURRING', 'MONTHLY_FIRST', 'QUARTERLY'].includes(intervalType.value))

const dayOptions = [
  {value: 1, label: t('batchCreate.monday')},
  {value: 2, label: t('batchCreate.tuesday')},
  {value: 3, label: t('batchCreate.wednesday')},
  {value: 4, label: t('batchCreate.thursday')},
  {value: 5, label: t('batchCreate.friday')},
  {value: 6, label: t('batchCreate.saturday')},
  {value: 7, label: t('batchCreate.sunday')},
]

const intervalOptions = [
  {value: 'RECURRING', label: 'Wöchentlich'},
  {value: 'MONTHLY_FIRST', label: 'Monatlich (erster)'},
  {value: 'QUARTERLY', label: 'Vierteljährlich'},
  {value: 'YEARLY', label: 'Jährlich'},
] as const

/** The interval picked, read back from the offered ones so a stray value falls back to weekly. */
function pickedInterval() {
  return intervalOptions.find(option => option.value === intervalType.value)?.value ?? 'RECURRING'
}

/**
 * The zone the times of day are typed in, which is the reader's own, as every other time of an
 * appointment is read.
 *
 * <p>The server used to read a start of seven in the evening as seven in the evening UTC, and every
 * appointment of the batch then stood two hours early in Berlin in summer. The browser names its
 * zone and the server places each date in it.
 */
function readerZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined
  } catch {
    return undefined
  }
}

async function generateScheduleDates() {
  try {
    const generated = await events.generateDates({
      intervalType: pickedInterval(),
      dayOfWeek: dayOfWeek.value,
      startDate: startDate.value,
      endDate: endDate.value,
      startTime: startTime.value || undefined,
      endTime: endTime.value || undefined,
      ignoreBreaks: ignoreBreaks.value,
      timeZone: readerZone(),
    })
    emit('done', generated.map(r => ({...r, fieldValues: {}})))
  } catch (e) {
    emit('error', describeFailure(e, t))
  }
}

async function handleCsvUpload(file: File) {
  csvFileName.value = file.name
  csvProblems.value = []
  const table = readCsvTable(await readCsvText(file))
  csvEmpty.value = table.columns.length === 0 || table.lines.length === 0
  csvTable.value = csvEmpty.value ? {columns: [], lines: []} : table
  columnMapping.value = {}
  if (csvEmpty.value) return
  const mapping = guessColumnMapping(table, props.fieldDefs.flatMap(field => (field.name ? [field.name] : [])))
  columnMapping.value = mapping
  const mapped = Object.values(mapping)
  csvHasTimeColumns.value = mapped.includes(CsvTarget.START_TIME) || mapped.includes(CsvTarget.END_TIME)
  csvDateFormat.value = 'auto'
}

/** One line of the file that cannot become an appointment, with the value that cannot be read. */
function problemText(problem: CsvProblem): string {
  const params = {line: problem.line, value: problem.value}
  if (problem.reason === 'date') return t('batchCreate.unreadableDate', params)
  if (problem.reason === 'startTime') return t('batchCreate.unreadableStartTime', params)
  if (problem.reason === 'endTime') return t('batchCreate.unreadableEndTime', params)
  return t('batchCreate.endNotAfterStart', params)
}

/**
 * Turns the file into the rows of the table, or names the lines that cannot become an appointment.
 *
 * <p>Nothing goes on while a line is left whose day or time cannot be read, so a batch never
 * creates an appointment at a moment the file did not name.
 */
function applyCsvToRows() {
  const result = csvToBatchRows(csvTable.value, {
    mapping: columnMapping.value,
    dateFormat: csvDateFormat.value,
    fallbackStartTime: csvFallbackStartTime.value,
    fallbackEndTime: csvFallbackEndTime.value,
  })
  csvProblems.value = result.problems
  if (result.problems.length === 0) emit('done', result.rows)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('batchCreate.stepSchedule') }}</SubHeader>

    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton
          v-for="option in modes"
          :key="option.value"
          :selected="mode === option.value"
          size="md"
          @toggle="mode = option.value"
      >
        {{ option.label }}
      </SelectionToggleButton>
    </div>

    <ManualRowsEditor v-show="mode === 'manual'" v-model:drafts="drafts" @done="emit('done', $event)"/>

    <template v-if="mode === 'schedule'">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.intervalType') }}</FieldLabel>
          <SelectInput v-model="intervalType">
            <option v-for="opt in intervalOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </SelectInput>
        </div>
        <div v-if="needsDow" class="space-y-1">
          <FieldLabel>{{ t('batchCreate.dayOfWeek') }}</FieldLabel>
          <SelectInput :model-value="String(dayOfWeek)" @update:model-value="dayOfWeek = Number($event)">
            <option v-for="d in dayOptions" :key="d.value" :value="String(d.value)">{{ d.label }}</option>
          </SelectInput>
        </div>
      </div>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.startDate') }}</FieldLabel>
          <DateInput v-model="startDate"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.endDate') }}</FieldLabel>
          <DateInput v-model="endDate"/>
        </div>
      </div>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.startTime') }}</FieldLabel>
          <TimeShortInput v-model="startTime"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.endTime') }}</FieldLabel>
          <TimeShortInput v-model="endTime"/>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <ToggleInput v-model="ignoreBreaks"/>
        <FieldLabel>{{ t('batchCreate.ignoreBreaks') }}</FieldLabel>
      </div>
      <PrimaryButton :disabled="!startDate || !endDate" @click="generateScheduleDates">
        {{ t('batchCreate.generateDates') }}
      </PrimaryButton>
    </template>

    <template v-if="mode === 'csv'">
      <div class="flex items-center gap-4 flex-wrap">
        <FileUploadButton accept=".csv,.txt" @select="handleCsvUpload">
          {{ t('batchCreate.uploadCsv') }}
        </FileUploadButton>
        <span v-if="csvFileName" class="text-sm">
          <font-awesome-icon v-if="!csvEmpty" :icon="['fas', 'check']" class="text-success mr-1"/>
          {{ csvFileName }}
        </span>
      </div>

      <Alert v-if="csvEmpty" variant="error">{{ t('batchCreate.emptyFile') }}</Alert>

      <template v-if="csvColumns.length > 0">
        <SubHeader>{{ t('batchCreate.mapColumns') }}</SubHeader>
        <div class="space-y-2">
          <div v-for="col in csvColumns" :key="col" class="flex items-center gap-3">
            <span class="w-40 text-sm font-medium truncate">{{ col }}</span>
            <SelectInput :model-value="columnMapping[col] ?? ''"
                         class="flex-1" @update:model-value="columnMapping[col] = String($event ?? '')">
              <option value="">{{ t('batchCreate.unmapped') }}</option>
              <option value="__date__">{{ t('batchCreate.date') }}</option>
              <option value="__startTime__">{{ t('batchCreate.startTime') }}</option>
              <option value="__endTime__">{{ t('batchCreate.endTime') }}</option>
              <option value="__name__">{{ t('batchCreate.eventName') }}</option>
              <option v-for="fd in fieldDefs" :key="fd.name" :value="fd.name">{{ fd.name }}</option>
            </SelectInput>
          </div>
        </div>

        <CsvDateFormatSelect v-model="csvDateFormat"/>

        <div v-if="!csvHasTimeColumns" class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('batchCreate.startTime') }}</FieldLabel>
            <TimeShortInput v-model="csvFallbackStartTime"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('batchCreate.endTime') }}</FieldLabel>
            <TimeShortInput v-model="csvFallbackEndTime"/>
          </div>
        </div>

        <Alert v-if="csvProblems.length > 0" variant="error">
          <p>{{ t('batchCreate.unreadableLines') }}</p>
          <ul class="mt-1 list-disc pl-5">
            <li v-for="problem in csvProblems" :key="`${problem.line}-${problem.reason}`">
              {{ problemText(problem) }}
            </li>
          </ul>
        </Alert>

        <PrimaryButton @click="applyCsvToRows">
          {{ t('common.next') }}
        </PrimaryButton>
      </template>
    </template>
  </NeutralContainer>
</template>
