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
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {BatchRow, EventFieldEntry} from '@/api/events'
import {events} from '@/api'

const props = defineProps<{
  fieldDefs: EventFieldEntry[]
}>()

const emit = defineEmits<{
  done: [rows: BatchRow[]]
  error: [msg: string]
}>()

const {t} = useI18n()

const mode = ref<'schedule' | 'csv'>('schedule')

// Schedule
const intervalType = ref('RECURRING')
const dayOfWeek = ref(1)
const startDate = ref('')
const endDate = ref('')
const startTime = ref('')
const endTime = ref('')
const ignoreBreaks = ref(false)

// CSV
const csvColumns = ref<string[]>([])
const csvRows = ref<string[][]>([])
const columnMapping = ref<Record<string, string>>({})
const csvDateFormat = ref<'auto' | 'DD.MM.YYYY' | 'YYYY-MM-DD' | 'MM/DD/YYYY'>('auto')
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
]

async function generateScheduleDates() {
  try {
    const generated = await events.generateDates({
      intervalType: intervalType.value,
      dayOfWeek: dayOfWeek.value,
      startDate: startDate.value,
      endDate: endDate.value,
      startTime: startTime.value || undefined,
      endTime: endTime.value || undefined,
      ignoreBreaks: ignoreBreaks.value,
    })
    emit('done', generated.map(r => ({...r, fieldValues: {}})))
  } catch {
    emit('error', t('common.error'))
  }
}

function handleCsvUpload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => parseCsv(reader.result as string)
  reader.readAsText(file)
}

function parseCsv(text: string) {
  const [header, ...rows] = text.split('\n').map(l => l.trim()).filter(l => l)
  if (!header || rows.length === 0) return
  const separator = header.includes(';') ? ';' : ','
  csvColumns.value = header.split(separator).map(c => c.trim().replace(/^"|"$/g, ''))
  csvRows.value = rows.map(l => l.split(separator).map(c => c.trim().replace(/^"|"$/g, '')))

  const mapping: Record<string, string> = {}
  const fieldNames = props.fieldDefs.map(f => f.name.toLowerCase())
  for (const col of csvColumns.value) {
    const lower = col.toLowerCase()
    if (['datum', 'date', 'tag'].includes(lower)) mapping[col] = '__date__'
    else if (['startzeit', 'start_time', 'starttime', 'von', 'beginn', 'start'].includes(lower)) mapping[col] = '__startTime__'
    else if (['endzeit', 'end_time', 'endtime', 'bis', 'ende', 'end'].includes(lower)) mapping[col] = '__endTime__'
    else if (['name', 'terminname', 'event_name', 'titel', 'title', 'bezeichnung'].includes(lower)) mapping[col] = '__name__'
    else {
      const match = props.fieldDefs[fieldNames.indexOf(lower)]
      if (match) mapping[col] = match.name
    }
  }
  columnMapping.value = mapping
  const mapped = Object.values(mapping)
  csvHasTimeColumns.value = mapped.includes('__startTime__') || mapped.includes('__endTime__')

  // Auto-detect date format
  const dateColName = Object.entries(mapping).find(([, v]) => v === '__date__')?.[0]
  if (dateColName && csvRows.value.length > 0) {
    const sample = csvRows.value[0]?.[csvColumns.value.indexOf(dateColName)]?.trim() ?? ''
    if (/^\d{2}\.\d{2}\.\d{4}$/.test(sample)) csvDateFormat.value = 'DD.MM.YYYY'
    else if (/^\d{4}-\d{2}-\d{2}$/.test(sample)) csvDateFormat.value = 'YYYY-MM-DD'
    else if (/^\d{2}\/\d{2}\/\d{4}$/.test(sample)) csvDateFormat.value = 'MM/DD/YYYY'
    else csvDateFormat.value = 'auto'
  }
}

function parseDateStr(raw: string): string {
  const s = raw.trim()
  const fmt = csvDateFormat.value
  if (fmt === 'DD.MM.YYYY' || (fmt === 'auto' && /^\d{2}\.\d{2}\.\d{4}$/.test(s))) {
    const [d, m, y] = s.split('.'); return `${y}-${m}-${d}`
  }
  if (fmt === 'MM/DD/YYYY' || (fmt === 'auto' && /^\d{2}\/\d{2}\/\d{4}$/.test(s))) {
    const [m, d, y] = s.split('/'); return `${y}-${m}-${d}`
  }
  return s
}

function applyCsvToRows() {
  const find = (target: string) => Object.entries(columnMapping.value).find(([, v]) => v === target)?.[0]
  const dateCol = find('__date__')
  const stCol = find('__startTime__')
  const etCol = find('__endTime__')
  const nameCol = find('__name__')

  const result: BatchRow[] = csvRows.value.map(csvRow => {
    const fieldValues: Record<string, string> = {}
    for (const [col, target] of Object.entries(columnMapping.value)) {
      if (target.startsWith('__')) continue
      const i = csvColumns.value.indexOf(col)
      if (i >= 0) fieldValues[target] = csvRow[i] ?? ''
    }
    const idx = (col?: string) => col ? csvColumns.value.indexOf(col) : -1
    const rawDate = idx(dateCol) >= 0 ? csvRow[idx(dateCol)] ?? '' : ''
    const isoDate = rawDate ? parseDateStr(rawDate) : ''
    const st = idx(stCol) >= 0 ? csvRow[idx(stCol)]?.trim() : csvFallbackStartTime.value
    const et = idx(etCol) >= 0 ? csvRow[idx(etCol)]?.trim() : csvFallbackEndTime.value
    const n = idx(nameCol) >= 0 ? csvRow[idx(nameCol)] : undefined
    return {
      name: n,
      startTime: isoDate ? `${isoDate}T${st || '00:00'}:00Z` : new Date().toISOString(),
      endTime: isoDate ? `${isoDate}T${et || '23:59'}:00Z` : new Date().toISOString(),
      fieldValues,
    }
  })
  emit('done', result)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('batchCreate.stepSchedule') }}</SubHeader>

    <ToggleSwitch
        :model-value="mode"
        option-a="schedule"
        option-b="csv"
        :label-a="t('batchCreate.schedule')"
        :label-b="t('batchCreate.csvImport')"
        @update:model-value="mode = $event as 'schedule' | 'csv'"
    />

    <!-- Schedule mode -->
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

    <!-- CSV mode -->
    <template v-if="mode === 'csv'">
      <div class="space-y-2">
        <FieldLabel>{{ t('batchCreate.uploadCsv') }}</FieldLabel>
        <input type="file" accept=".csv,.txt" class="text-sm" @change="handleCsvUpload"/>
      </div>

      <template v-if="csvColumns.length > 0">
        <SubHeader>{{ t('batchCreate.mapColumns') }}</SubHeader>
        <div class="space-y-2">
          <div v-for="col in csvColumns" :key="col" class="flex items-center gap-3">
            <span class="w-40 text-sm font-medium truncate">{{ col }}</span>
            <SelectInput :model-value="columnMapping[col] ?? ''"
                         @update:model-value="columnMapping[col] = String($event ?? '')" class="flex-1">
              <option value="">{{ t('batchCreate.unmapped') }}</option>
              <option value="__date__">{{ t('batchCreate.date') }}</option>
              <option value="__startTime__">{{ t('batchCreate.startTime') }}</option>
              <option value="__endTime__">{{ t('batchCreate.endTime') }}</option>
              <option value="__name__">{{ t('batchCreate.eventName') }}</option>
              <option v-for="fd in fieldDefs" :key="fd.name" :value="fd.name">{{ fd.name }}</option>
            </SelectInput>
          </div>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('batchCreate.dateFormat') }}</FieldLabel>
          <SelectInput v-model="csvDateFormat">
            <option value="auto">{{ t('batchCreate.dateFormatAuto') }}</option>
            <option value="DD.MM.YYYY">DD.MM.YYYY (05.01.2026)</option>
            <option value="YYYY-MM-DD">YYYY-MM-DD (2026-01-05)</option>
            <option value="MM/DD/YYYY">MM/DD/YYYY (01/05/2026)</option>
          </SelectInput>
        </div>

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

        <PrimaryButton @click="applyCsvToRows">
          {{ t('common.next') }}
        </PrimaryButton>
      </template>
    </template>
  </NeutralContainer>
</template>
