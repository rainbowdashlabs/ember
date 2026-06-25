/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ReportPresetList from './reportview/ReportPresetList.vue'
import ReportFilters from './reportview/ReportFilters.vue'
import ReportPreview from './reportview/ReportPreview.vue'
import type {MemberGroup} from '@/api/types'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import {attendance, memberGroups} from '@/api'
import type {ReportData, ReportPreset} from '@/api/attendance'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const {loaded} = useSession()

const groups = ref<MemberGroup[]>([])
const presets = ref<ReportPreset[]>([])
const report = ref<ReportData | null>(null)
const previewing = ref(false)
const exporting = ref(false)

const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<string[]>([])

const userTypeOptions = computed(() =>
    Object.values(StationUserType).map(ut => ({value: ut, label: StationUserTypeLabels[ut] ?? ut})),
)

const groupOptions = computed(() =>
    groups.value.map(g => ({value: String(g.id), label: g.name ?? ''})),
)
const selectedPeriod = ref('month')
const selectedYear = ref(new Date().getFullYear())
const selectedMonth = ref(new Date().getMonth())
const selectedWeek = ref(currentIsoWeek())
const selectedRounding = ref('exact')

const periodOptions = [
  {value: 'week', label: 'Woche'},
  {value: 'month', label: 'Monat'},
  {value: 'year', label: 'Jahr'},
]

const roundingOptions = [
  {value: 'exact', label: 'Exakt (2 Dezimalstellen)'},
  {value: 'round', label: 'Gerundet (0.5h)'},
  {value: 'ceil', label: 'Aufgerundet (volle Stunde)'},
]

const yearOptions = computed(() => {
  const current = new Date().getFullYear()
  return Array.from({length: 5}, (_, i) => current - i)
})

const monthOptions = [
  {value: 0, label: 'Januar'}, {value: 1, label: 'Februar'}, {value: 2, label: 'März'},
  {value: 3, label: 'April'}, {value: 4, label: 'Mai'}, {value: 5, label: 'Juni'},
  {value: 6, label: 'Juli'}, {value: 7, label: 'August'}, {value: 8, label: 'September'},
  {value: 9, label: 'Oktober'}, {value: 10, label: 'November'}, {value: 11, label: 'Dezember'},
]

function currentIsoWeek(): number {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() + 3 - ((d.getDay() + 6) % 7))
  const week1 = new Date(d.getFullYear(), 0, 4)
  return 1 + Math.round(((d.getTime() - week1.getTime()) / 86400000 - 3 + ((week1.getDay() + 6) % 7)) / 7)
}

const weekOptions = computed(() => Array.from({length: 53}, (_, i) => i + 1))

const showSavePreset = ref(false)
const presetName = ref('')

const canPreview = computed(() => {
  return selectedUserTypes.value.length > 0 || selectedGroupIds.value.length > 0
})

const timeRange = computed(() => {
  let from: Date
  let to: Date

  if (selectedPeriod.value === 'week') {
    const jan4 = new Date(selectedYear.value, 0, 4)
    const dayOfWeek = (jan4.getDay() + 6) % 7
    const week1Monday = new Date(jan4)
    week1Monday.setDate(jan4.getDate() - dayOfWeek)
    from = new Date(week1Monday)
    from.setDate(week1Monday.getDate() + (selectedWeek.value - 1) * 7)
    from.setHours(0, 0, 0, 0)
    to = new Date(from)
    to.setDate(from.getDate() + 7)
  } else if (selectedPeriod.value === 'year') {
    from = new Date(selectedYear.value, 0, 1)
    to = new Date(selectedYear.value + 1, 0, 1)
  } else {
    from = new Date(selectedYear.value, selectedMonth.value, 1)
    to = new Date(selectedYear.value, selectedMonth.value + 1, 1)
  }

  return {from: from.toISOString(), to: to.toISOString()}
})

function buildParams() {
  const params = new URLSearchParams()
  params.set('from', timeRange.value.from)
  params.set('to', timeRange.value.to)
  params.set('rounding', selectedRounding.value)
  for (const ut of selectedUserTypes.value) params.append('userTypes', ut)
  for (const gid of selectedGroupIds.value) params.append('groupIds', gid)
  return params
}

const {loading, error, reload} = useAsyncLoader(async () => {
  const [allGroups, allPresets] = await Promise.all([
    memberGroups.listGroups(),
    attendance.listPresets(),
  ])
  groups.value = allGroups
  presets.value = allPresets
}, {autoLoad: false})

async function preview() {
  if (!canPreview.value) return
  previewing.value = true
  error.value = ''
  report.value = null
  try {
    report.value = await attendance.reportPreview(buildParams())
  } catch {
    error.value = t('common.error')
  } finally {
    previewing.value = false
  }
}

async function exportPdf() {
  if (!canPreview.value) return
  exporting.value = true
  error.value = ''
  try {
    const params = buildParams()
    params.set('period', selectedPeriod.value)
    const blob = await attendance.reportExport(params)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'attendance-report.pdf'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    error.value = t('common.error')
  } finally {
    exporting.value = false
  }
}

async function savePreset() {
  if (!presetName.value || !canPreview.value) return
  error.value = ''
  try {
    await attendance.createPreset({
      name: presetName.value,
      roleName: selectedUserTypes.value.join(',') || undefined,
      groupId: selectedGroupIds.value.length > 0 ? Number(selectedGroupIds.value[0]) : null,
      period: selectedPeriod.value,
      rounding: selectedRounding.value,
    })
    presets.value = await attendance.listPresets()
    showSavePreset.value = false
    presetName.value = ''
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function applyPreset(preset: ReportPreset) {
  selectedUserTypes.value = preset.roleName ? preset.roleName.split(',').filter(Boolean) : []
  selectedGroupIds.value = preset.groupId ? [String(preset.groupId)] : []
  selectedPeriod.value = preset.period
  selectedRounding.value = preset.rounding
}

async function removePreset(id: number) {
  try {
    await attendance.deletePreset(id)
    presets.value = await attendance.listPresets()
  } catch {
    error.value = t('common.error')
  }
}

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent>
    <SectionHeader>{{ t('attendanceReport.title') }}</SectionHeader>
    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <template v-if="!loading">
      <ReportPresetList
          :presets="presets"
          :groups="groups"
          @apply="applyPreset"
          @remove="removePreset"
      />
      <ReportFilters
          v-model:selected-user-types="selectedUserTypes"
          v-model:selected-group-ids="selectedGroupIds"
          v-model:selected-rounding="selectedRounding"
          v-model:selected-period="selectedPeriod"
          v-model:selected-year="selectedYear"
          v-model:selected-month="selectedMonth"
          v-model:selected-week="selectedWeek"
          v-model:show-save-preset="showSavePreset"
          v-model:preset-name="presetName"
          :user-type-options="userTypeOptions"
          :group-options="groupOptions"
          :rounding-options="roundingOptions"
          :period-options="periodOptions"
          :year-options="yearOptions"
          :month-options="monthOptions"
          :week-options="weekOptions"
          :can-preview="canPreview"
          :previewing="previewing"
          :save-preset="savePreset"
          @preview="preview"
      />
      <ReportPreview
          v-if="report"
          :report="report"
          :exporting="exporting"
          @export="exportPdf"
      />
    </template>
  </ViewContent>
</template>
