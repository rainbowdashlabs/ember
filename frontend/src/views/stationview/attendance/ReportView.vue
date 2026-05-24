/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {MemberGroup, Role} from '@/api/types'
import {attendance, memberGroups, stationMembers} from '@/api'
import type {ReportData, ReportPreset} from '@/api/attendance'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {loaded} = useSession()

const roles = ref<Role[]>([])
const groups = ref<MemberGroup[]>([])
const presets = ref<ReportPreset[]>([])
const report = ref<ReportData | null>(null)
const loading = ref(true)
const previewing = ref(false)
const exporting = ref(false)
const error = ref('')

// Filter state
const filterType = ref<'role' | 'group'>('role')
const selectedRole = ref('')
const selectedGroupId = ref('')
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
  if (filterType.value === 'role') return !!selectedRole.value
  return !!selectedGroupId.value
})

const timeRange = computed(() => {
  let from: Date
  let to: Date

  if (selectedPeriod.value === 'week') {
    // ISO week: Monday-based. Find the Monday of the selected week in selectedYear.
    const jan4 = new Date(selectedYear.value, 0, 4)
    const dayOfWeek = (jan4.getDay() + 6) % 7 // 0=Mon
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
  const params: Record<string, string | number> = {
    from: timeRange.value.from,
    to: timeRange.value.to,
    rounding: selectedRounding.value,
  }
  if (filterType.value === 'role') {
    params.role = selectedRole.value
  } else {
    params.groupId = Number(selectedGroupId.value)
  }
  return params
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [allRoles, allGroups, allPresets] = await Promise.all([
      stationMembers.listAllRoles(),
      memberGroups.listGroups(),
      attendance.listPresets(),
    ])
    roles.value = allRoles
    groups.value = allGroups
    presets.value = allPresets
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function preview() {
  if (!canPreview.value) return
  previewing.value = true
  error.value = ''
  report.value = null
  try {
    report.value = await attendance.reportPreview(buildParams() as any)
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
    const blob = await attendance.reportExport({...buildParams(), period: selectedPeriod.value} as any)
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
      roleName: filterType.value === 'role' ? selectedRole.value : undefined,
      groupId: filterType.value === 'group' ? Number(selectedGroupId.value) : null,
      period: selectedPeriod.value,
      rounding: selectedRounding.value,
    })
    presets.value = await attendance.listPresets()
    showSavePreset.value = false
    presetName.value = ''
  } catch {
    error.value = t('common.error')
  }
}

function applyPreset(preset: ReportPreset) {
  if (preset.groupId) {
    filterType.value = 'group'
    selectedGroupId.value = String(preset.groupId)
    selectedRole.value = ''
  } else {
    filterType.value = 'role'
    selectedRole.value = preset.roleName ?? ''
    selectedGroupId.value = ''
  }
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

function presetLabel(preset: ReportPreset): string {
  if (preset.groupId) {
    const g = groups.value.find(g => g.id === preset.groupId)
    return `${preset.name} (${g?.name ?? 'Gruppe'})`
  }
  return `${preset.name} (${preset.roleName})`
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
      <SectionHeader>{{ t('attendanceReport.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <!-- Saved presets -->
        <div v-if="presets.length > 0" class="flex flex-wrap gap-2">
          <button
              v-for="preset in presets"
              :key="preset.id"
              class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all border-bg-light-accent dark:border-bg-dark-accent hover:border-primary flex items-center gap-2"
              @click="applyPreset(preset)"
          >
            {{ presetLabel(preset) }}
            <span class="text-(--text-muted) hover:text-error cursor-pointer" @click.stop="removePreset(preset.id)">
              <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
            </span>
          </button>
        </div>

        <!-- Filters -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('attendanceReport.filters') }}</SubHeader>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.filterBy') }}</FieldLabel>
              <SelectInput v-model="filterType">
                <option value="role">{{ t('attendanceReport.byRole') }}</option>
                <option value="group">{{ t('attendanceReport.byGroup') }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{
                  filterType === 'role' ? t('attendanceReport.role') : t('attendanceReport.group')
                }}</FieldLabel>
              <SelectInput v-if="filterType === 'role'" v-model="selectedRole">
                <option disabled value="">{{ t('attendanceReport.selectRole') }}</option>
                <option v-for="role in roles" :key="role.id" :value="role.role">{{ role.role }}</option>
              </SelectInput>
              <SelectInput v-else v-model="selectedGroupId">
                <option disabled value="">{{ t('attendanceReport.selectGroup') }}</option>
                <option v-for="group in groups" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.rounding') }}</FieldLabel>
              <SelectInput v-model="selectedRounding">
                <option v-for="opt in roundingOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </SelectInput>
            </div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.period') }}</FieldLabel>
              <SelectInput v-model="selectedPeriod">
                <option v-for="opt in periodOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.year') }}</FieldLabel>
              <SelectInput :model-value="String(selectedYear)" @update:model-value="selectedYear = Number($event)">
                <option v-for="y in yearOptions" :key="y" :value="String(y)">{{ y }}</option>
              </SelectInput>
            </div>
            <div v-if="selectedPeriod === 'month'" class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.month') }}</FieldLabel>
              <SelectInput :model-value="String(selectedMonth)" @update:model-value="selectedMonth = Number($event)">
                <option v-for="m in monthOptions" :key="m.value" :value="String(m.value)">{{ m.label }}</option>
              </SelectInput>
            </div>
            <div v-if="selectedPeriod === 'week'" class="space-y-1">
              <FieldLabel>{{ t('attendanceReport.week') }}</FieldLabel>
              <SelectInput :model-value="String(selectedWeek)" @update:model-value="selectedWeek = Number($event)">
                <option v-for="w in weekOptions" :key="w" :value="String(w)">KW {{ w }}</option>
              </SelectInput>
            </div>
          </div>
          <div class="flex items-center gap-3 flex-wrap">
            <PrimaryButton :disabled="!canPreview || previewing" @click="preview">
              <font-awesome-icon :icon="['fas', 'eye']" class="mr-1"/>
              {{ previewing ? t('common.loading') : t('attendanceReport.preview') }}
            </PrimaryButton>
            <SecondaryButton v-if="!showSavePreset" :disabled="!canPreview" @click="showSavePreset = true">
              <font-awesome-icon :icon="['fas', 'copy']" class="mr-1"/>
              {{ t('attendanceReport.savePreset') }}
            </SecondaryButton>
            <template v-if="showSavePreset">
              <TextInput v-model="presetName" :placeholder="t('attendanceReport.presetName')" class="w-48"/>
              <PrimaryButton :disabled="!presetName" @click="savePreset">{{ t('common.save') }}</PrimaryButton>
              <SecondaryButton @click="showSavePreset = false">{{ t('common.cancel') }}</SecondaryButton>
            </template>
          </div>
        </NeutralContainer>

        <!-- Preview -->
        <template v-if="report">
          <div class="flex justify-end">
            <PrimaryButton :disabled="exporting" @click="exportPdf">
              <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/>
              {{ exporting ? t('common.loading') : t('attendanceReport.exportPdf') }}
            </PrimaryButton>
          </div>

          <!-- Overall summary -->
          <NeutralContainer class="space-y-3">
            <SubHeader>{{ t('attendanceReport.summary') }} – {{ report.filterLabel }}</SubHeader>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                  <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
                  <th class="text-center py-2 px-3">{{ t('attendanceReport.sessions') }}</th>
                  <th class="text-center py-2 px-3">{{ t('attendanceReport.present') }}</th>
                  <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</th>
                </tr>
                </thead>
                <tbody>
                <tr v-for="m in report.members" :key="m.memberId"
                    class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="py-2 px-3">{{ m.name }}</td>
                  <td class="text-center py-2 px-3">{{ m.sessionCount }}</td>
                  <td class="text-center py-2 px-3">{{ m.presentCount }}</td>
                  <td class="text-right py-2 px-3 font-mono">{{ m.totalHours.toFixed(1) }}</td>
                </tr>
                </tbody>
              </table>
            </div>
          </NeutralContainer>

          <!-- Year report: monthly breakdown with sessions per month -->
          <template v-if="report.monthlySummaries.length > 1">
            <template v-for="ms in report.monthlySummaries" :key="ms.month">
              <SectionHeader>{{ t('attendanceReport.monthReport', {month: ms.month}) }}</SectionHeader>

              <NeutralContainer class="space-y-3">
                <SubHeader>{{ t('attendanceReport.monthlySummary') }}</SubHeader>
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                    <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                      <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
                      <th class="text-center py-2 px-3">{{ t('attendanceReport.present') }}</th>
                      <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="m in ms.members" :key="m.memberId"
                        class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                      <td class="py-2 px-3">{{ m.name }}</td>
                      <td class="text-center py-2 px-3">{{ m.presentCount }}</td>
                      <td class="text-right py-2 px-3 font-mono">{{ m.totalHours.toFixed(1) }}</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </NeutralContainer>

              <NeutralContainer v-for="session in ms.sessions" :key="session.sessionId" class="space-y-2">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ session.title }}</span>
                  <span class="text-sm text-(--text-muted)">{{ session.date }} · {{
                      session.startTime
                    }} – {{ session.endTime }}</span>
                </div>
                <div class="text-xs text-(--text-muted)">
                  {{
                    t('attendanceReport.presentOfExpected', {
                      present: session.presentCount,
                      expected: session.expectedCount
                    })
                  }}
                </div>
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                    <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                      <th class="text-left py-1.5 px-2">{{ t('attendanceReport.name') }}</th>
                      <th class="text-center py-1.5 px-2">{{ t('attendanceReport.from') }}</th>
                      <th class="text-center py-1.5 px-2">{{ t('attendanceReport.to') }}</th>
                      <th class="text-right py-1.5 px-2">{{ t('attendanceReport.hours') }}</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="entry in session.entries" :key="entry.memberId"
                        class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                      <td class="py-1.5 px-2">{{ entry.name }}</td>
                      <td class="text-center py-1.5 px-2">{{ entry.checkIn }}</td>
                      <td class="text-center py-1.5 px-2">{{ entry.checkOut }}</td>
                      <td class="text-right py-1.5 px-2 font-mono">{{ entry.hours.toFixed(1) }}</td>
                    </tr>
                    </tbody>
                  </table>
                </div>
              </NeutralContainer>
            </template>
          </template>

          <!-- Week/month report: sessions directly -->
          <template v-else>
            <NeutralContainer v-for="session in report.sessions" :key="session.sessionId" class="space-y-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ session.title }}</span>
                <span class="text-sm text-(--text-muted)">{{ session.date }} · {{
                    session.startTime
                  }} – {{ session.endTime }}</span>
              </div>
              <div class="text-xs text-(--text-muted)">
                {{
                  t('attendanceReport.presentOfExpected', {
                    present: session.presentCount,
                    expected: session.expectedCount
                  })
                }}
              </div>
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                  <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
                    <th class="text-left py-1.5 px-2">{{ t('attendanceReport.name') }}</th>
                    <th class="text-center py-1.5 px-2">{{ t('attendanceReport.from') }}</th>
                    <th class="text-center py-1.5 px-2">{{ t('attendanceReport.to') }}</th>
                    <th class="text-right py-1.5 px-2">{{ t('attendanceReport.hours') }}</th>
                  </tr>
                  </thead>
                  <tbody>
                  <tr v-for="entry in session.entries" :key="entry.memberId"
                      class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                    <td class="py-1.5 px-2">{{ entry.name }}</td>
                    <td class="text-center py-1.5 px-2">{{ entry.checkIn }}</td>
                    <td class="text-center py-1.5 px-2">{{ entry.checkOut }}</td>
                    <td class="text-right py-1.5 px-2 font-mono">{{ entry.hours.toFixed(1) }}</td>
                  </tr>
                  </tbody>
                </table>
              </div>
            </NeutralContainer>
          </template>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
