/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {
  type AdminStationUsage,
  type StorageQuotaPreset,
  formatBytes,
  getAdminUsage,
  getPresets,
  recalculateAll,
  recalculateStation,
  createPreset,
  updatePreset,
  deletePreset,
  applyPreset,
  resetStationQuotas,
} from '@/api/storageMonitoring'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import BaseInput from '@/components/input/BaseInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent])

const {t} = useI18n()

const isDark = ref(document.documentElement.classList.contains('dark'))
let observer: MutationObserver | null = null

const loading = ref(true)
const error = ref('')
const stations = ref<AdminStationUsage[]>([])
const presets = ref<StorageQuotaPreset[]>([])
const sortBy = ref<'name' | 'usage' | 'percent'>('percent')
const sortDesc = ref(true)
const reconciling = ref(false)

// Size unit helpers
type SizeUnit = 'MiB' | 'GiB' | 'TiB'
const sizeUnits: SizeUnit[] = ['MiB', 'GiB', 'TiB']
const unitMultiplier: Record<SizeUnit, number> = {MiB: 1024 * 1024, GiB: 1024 * 1024 * 1024, TiB: 1024 * 1024 * 1024 * 1024}

function bytesToUnit(bytes: number): {value: number, unit: SizeUnit} {
  if (bytes >= 1024 * 1024 * 1024 * 1024) return {value: Math.round(bytes / (1024 * 1024 * 1024 * 1024) * 100) / 100, unit: 'TiB'}
  if (bytes >= 1024 * 1024 * 1024) return {value: Math.round(bytes / (1024 * 1024 * 1024) * 100) / 100, unit: 'GiB'}
  return {value: Math.round(bytes / (1024 * 1024) * 100) / 100, unit: 'MiB'}
}

interface SizeField { value: number, unit: SizeUnit }

function toBytes(field: SizeField): number {
  return Math.round(field.value * unitMultiplier[field.unit])
}

function makeSizeField(bytes: number): SizeField {
  const {value, unit} = bytesToUnit(bytes)
  return {value, unit}
}

// Preset modal
const showPresetModal = ref(false)
const editingPreset = ref<StorageQuotaPreset | null>(null)
const presetName = ref('')
const presetFields = ref({
  total: {value: 5, unit: 'GiB'} as SizeField,
  kb: {value: 4, unit: 'GiB'} as SizeField,
  board: {value: 3, unit: 'GiB'} as SizeField,
  images: {value: 1, unit: 'GiB'} as SizeField,
  pages: {value: 500, unit: 'MiB'} as SizeField,
  perFile: {value: 50, unit: 'MiB'} as SizeField,
  perImage: {value: 5, unit: 'MiB'} as SizeField,
})

// Apply preset modal
const showApplyModal = ref(false)
const applyPresetId = ref<number | null>(null)
const selectedStations = ref<string[]>([])

// Delete confirm modal
const showDeleteModal = ref(false)
const deletingPresetId = ref<number | null>(null)

onMounted(() => {
  observer = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']})
  loadData()
})

onUnmounted(() => {
  observer?.disconnect()
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [usageData, presetData] = await Promise.all([getAdminUsage(), getPresets()])
    stations.value = usageData
    presets.value = presetData
  } catch (e: any) {
    error.value = e.message || 'Failed to load storage data'
  } finally {
    loading.value = false
  }
}

const sortedStations = computed(() => {
  return [...stations.value].sort((a, b) => {
    let cmp = 0
    if (sortBy.value === 'name') cmp = a.stationName.localeCompare(b.stationName)
    else if (sortBy.value === 'usage') cmp = a.totalBytes - b.totalBytes
    else cmp = a.quotaUsedPercent - b.quotaUsedPercent
    return sortDesc.value ? -cmp : cmp
  })
})

const totalUsage = computed(() => stations.value.reduce((s, st) => s + st.totalBytes, 0))
const stationsWarning = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 80 && s.quotaUsedPercent < 95).length)
const stationsFull = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 95).length)

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')

function statusBadge(percent: number) {
  if (percent >= 95) return 'full'
  if (percent >= 80) return 'warning'
  return 'ok'
}

function barColor(percent: number) {
  if (percent >= 95) return 'bg-red-500'
  if (percent >= 80) return 'bg-yellow-500'
  return 'bg-green-500'
}

const categoryColorMap: Record<string, string> = {
  KB_FILES: '#3694FF',
  BOARD_ATTACHMENTS: '#FF6421',
  PAGE_IMAGES: '#00C507',
  IMAGES: '#73CEFF',
  AVATARS: '#9ca3af',
}

async function handleRecalculateAll() {
  reconciling.value = true
  try {
    await recalculateAll()
    setTimeout(() => loadData(), 2000)
  } finally {
    reconciling.value = false
  }
}

async function handleRecalculateStation(uid: string) {
  try {
    await recalculateStation(uid)
    await loadData()
  } catch { /* ignore */ }
}

function openCreatePreset() {
  editingPreset.value = null
  presetName.value = ''
  presetFields.value = {
    total: {value: 5, unit: 'GiB'},
    kb: {value: 4, unit: 'GiB'},
    board: {value: 3, unit: 'GiB'},
    images: {value: 1, unit: 'GiB'},
    pages: {value: 500, unit: 'MiB'},
    perFile: {value: 50, unit: 'MiB'},
    perImage: {value: 5, unit: 'MiB'},
  }
  showPresetModal.value = true
}

function openEditPreset(preset: StorageQuotaPreset) {
  editingPreset.value = preset
  presetName.value = preset.name
  presetFields.value = {
    total: makeSizeField(preset.total),
    kb: makeSizeField(preset.kb),
    board: makeSizeField(preset.board),
    images: makeSizeField(preset.images),
    pages: makeSizeField(preset.pages),
    perFile: makeSizeField(preset.perFile),
    perImage: makeSizeField(preset.perImage),
  }
  showPresetModal.value = true
}

async function savePreset() {
  try {
    const f = presetFields.value
    const payload = {
      name: presetName.value,
      total: toBytes(f.total),
      kb: toBytes(f.kb),
      board: toBytes(f.board),
      images: toBytes(f.images),
      pages: toBytes(f.pages),
      perFile: toBytes(f.perFile),
      perImage: toBytes(f.perImage),
    }
    if (editingPreset.value) {
      await updatePreset(editingPreset.value.id, payload)
    } else {
      await createPreset(payload)
    }
    showPresetModal.value = false
    await loadData()
  } catch { /* ignore */ }
}

function confirmDeletePreset(id: number) {
  deletingPresetId.value = id
  showDeleteModal.value = true
}

async function handleDeletePreset() {
  if (deletingPresetId.value != null) {
    await deletePreset(deletingPresetId.value)
    showDeleteModal.value = false
    await loadData()
  }
}

function openApplyPreset(presetId: number) {
  applyPresetId.value = presetId
  selectedStations.value = []
  showApplyModal.value = true
}

async function handleApplyPreset() {
  if (applyPresetId.value != null && selectedStations.value.length > 0) {
    await applyPreset(applyPresetId.value, selectedStations.value)
    showApplyModal.value = false
    await loadData()
  }
}

async function handleResetQuotas(stationUid: string) {
  await resetStationQuotas(stationUid)
  await loadData()
}

function toggleSort(field: 'name' | 'usage' | 'percent') {
  if (sortBy.value === field) {
    sortDesc.value = !sortDesc.value
  } else {
    sortBy.value = field
    sortDesc.value = true
  }
}

function categoryLabel(cat: string): string {
  const labels: Record<string, string> = {
    KB_FILES: t('storageMonitoring.categories.kbFiles'),
    BOARD_ATTACHMENTS: t('storageMonitoring.categories.boardAttachments'),
    PAGE_IMAGES: t('storageMonitoring.categories.pageImages'),
    AVATARS: t('storageMonitoring.categories.avatars'),
    IMAGES: t('storageMonitoring.categories.images'),
  }
  return labels[cat] || cat
}

// Size input helper: convert bytes to MB for input
function bytesToMb(bytes: number): number {
  return Math.round(bytes / (1024 * 1024))
}

function mbToBytes(mb: number): number {
  return mb * 1024 * 1024
}

// Chart: top stations by usage
const topStationsChart = computed(() => {
  const top = [...stations.value].filter(s => s.totalBytes > 0).sort((a, b) => b.totalBytes - a.totalBytes).slice(0, 15)
  return {
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}, formatter: (params: any) => {
      const p = Array.isArray(params) ? params[0] : params
      return `${p.name}<br/>${formatBytes(p.value)} / ${formatBytes(top[p.dataIndex]?.quotaBytes || 0)}`
    }},
    grid: {left: '3%', right: '4%', bottom: '3%', containLabel: true},
    xAxis: {type: 'category', data: top.map(s => s.stationName), axisLabel: {color: textColor.value, rotate: 30, fontSize: 11}},
    yAxis: {type: 'value', axisLabel: {color: textColor.value, formatter: (v: number) => formatBytes(v)}},
    series: [{
      type: 'bar',
      data: top.map(s => ({
        value: s.totalBytes,
        itemStyle: {color: s.quotaUsedPercent >= 95 ? '#ec2929' : s.quotaUsedPercent >= 80 ? '#ffdd1b' : '#73CEFF'},
      })),
    }],
  }
})

// Chart: category distribution pie
const categoryPieChart = computed(() => {
  const catTotals: Record<string, number> = {}
  for (const station of stations.value) {
    for (const cat of station.categories) {
      if (cat.category === 'AVATARS') continue
      catTotals[cat.category] = (catTotals[cat.category] || 0) + cat.totalBytes
    }
  }
  const colors: Record<string, string> = {
    KB_FILES: '#3694FF',
    BOARD_ATTACHMENTS: '#FF6421',
    PAGE_IMAGES: '#00C507',
    IMAGES: '#73CEFF',
  }
  return {
    tooltip: {trigger: 'item', formatter: (p: any) => `${p.name}: ${formatBytes(p.value)} (${p.percent}%)`},
    legend: {bottom: 0, textStyle: {color: textColor.value}},
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: Object.entries(catTotals).filter(([, bytes]) => bytes > 0).map(([cat, bytes]) => ({name: categoryLabel(cat), value: bytes, itemStyle: {color: colors[cat] || '#9ca3af'}})),
      label: {color: textColor.value},
    }],
  }
})
</script>

<template>
  <ViewContent>
    <Spinner v-if="loading" size="lg"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>
    <template v-else>
      <!-- Summary -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <NeutralContainer class="text-center">
          <StatValue>{{ formatBytes(totalUsage) }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalUsage') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue>{{ stations.length }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalStations') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="error">{{ stationsWarning }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.stationsWarning') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="error">{{ stationsFull }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.stationsFull') }}</p>
        </NeutralContainer>
      </div>

      <!-- Charts -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <NeutralContainer>
          <SectionHeader>{{ t('storageMonitoring.stationOverview') }}</SectionHeader>
          <VChart v-if="stations.length > 0" :option="topStationsChart" autoresize style="height: 300px"/>
        </NeutralContainer>
        <NeutralContainer>
          <SectionHeader>{{ t('storageMonitoring.categoryBreakdown') }}</SectionHeader>
          <VChart v-if="stations.length > 0" :option="categoryPieChart" autoresize style="height: 300px"/>
        </NeutralContainer>
      </div>

      <!-- Actions -->
      <div class="flex flex-wrap gap-2 mb-6">
        <PrimaryButton :disabled="reconciling" @click="handleRecalculateAll">
          <font-awesome-icon :icon="['fas', 'arrows-rotate']" class="mr-1"/>
          {{ t('storageMonitoring.recalculateAll') }}
        </PrimaryButton>
      </div>

      <!-- Presets -->
      <SectionHeader>{{ t('storageMonitoring.presets') }}</SectionHeader>
      <div class="mb-6">
        <div class="flex flex-wrap gap-2 mb-3">
          <PrimaryButton @click="openCreatePreset">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
            {{ t('storageMonitoring.createPreset') }}
          </PrimaryButton>
        </div>
        <div v-if="presets.length > 0" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
            <tr class="border-b border-[var(--border)]">
              <th class="text-left p-2">{{ t('storageMonitoring.presetName') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.total') }}</th>
              <th class="text-right p-2">KB</th>
              <th class="text-right p-2">{{ t('storageMonitoring.board') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.images') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.pages') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.perFile') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.perImage') }}</th>
              <th class="text-right p-2">{{ t('storageMonitoring.actions') }}</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="preset in presets" :key="preset.id" class="border-b border-[var(--border)] hover:bg-[var(--bg-hover)]">
              <td class="p-2 font-medium">{{ preset.name }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.total) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.kb) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.board) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.images) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.pages) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.perFile) }}</td>
              <td class="text-right p-2">{{ formatBytes(preset.perImage) }}</td>
              <td class="text-right p-2 flex gap-1 justify-end">
                <EditButton :label="t('storageMonitoring.editPreset')" @click="openEditPreset(preset)"/>
                <SecondaryButton @click="openApplyPreset(preset.id)">
                  <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                  {{ t('storageMonitoring.apply') }}
                </SecondaryButton>
                <DeleteButton :label="t('storageMonitoring.deletePreset')" @click="confirmDeletePreset(preset.id)"/>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
        <p v-else class="text-[var(--text-muted)] text-sm">{{ t('storageMonitoring.noPresets') }}</p>
      </div>

      <!-- Station table -->
      <SectionHeader>{{ t('storageMonitoring.stationOverview') }}</SectionHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
          <tr class="border-b border-[var(--border)]">
            <th class="text-left p-2 cursor-pointer" @click="toggleSort('name')">
              {{ t('storageMonitoring.stationName') }}
              <font-awesome-icon v-if="sortBy === 'name'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
            </th>
            <th class="text-left p-2 cursor-pointer min-w-[200px]" @click="toggleSort('usage')">
              {{ t('storageMonitoring.usage') }}
              <font-awesome-icon v-if="sortBy === 'usage'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
            </th>
            <th class="text-right p-2 cursor-pointer" @click="toggleSort('percent')">
              {{ t('storageMonitoring.quota') }}
              <font-awesome-icon v-if="sortBy === 'percent'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
            </th>
            <th class="text-center p-2">{{ t('storageMonitoring.status') }}</th>
            <th class="text-left p-2">{{ t('storageMonitoring.preset') }}</th>
            <th class="text-right p-2">{{ t('storageMonitoring.actions') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="station in sortedStations" :key="station.stationId" class="border-b border-[var(--border)] hover:bg-[var(--bg-hover)]">
            <td class="p-2 font-medium">{{ station.stationName }}</td>
            <td class="p-2">
              <div class="flex items-center gap-2">
                <div class="flex-1 bg-[var(--bg-muted)] rounded-full h-3 overflow-hidden flex">
                  <div v-for="cat in station.categories.filter(c => c.category !== 'AVATARS' && c.totalBytes > 0)" :key="cat.category"
                       :style="{width: (station.quotaBytes > 0 ? cat.totalBytes / station.quotaBytes * 100 : 0) + '%', backgroundColor: categoryColorMap[cat.category] || '#9ca3af'}"
                       :title="categoryLabel(cat.category) + ': ' + formatBytes(cat.totalBytes)"
                       class="h-full first:rounded-l-full last:rounded-r-full"/>
                </div>
                <span class="text-xs whitespace-nowrap text-[var(--text-muted)]">{{ formatBytes(station.totalBytes) }}</span>
              </div>
            </td>
            <td class="text-right p-2 whitespace-nowrap">{{ station.quotaUsedPercent }}% / {{ formatBytes(station.quotaBytes) }}</td>
            <td class="text-center p-2">
              <SuccessBadge v-if="statusBadge(station.quotaUsedPercent) === 'ok'">OK</SuccessBadge>
              <InfoBadge v-else-if="statusBadge(station.quotaUsedPercent) === 'warning'">{{ t('storageMonitoring.warning') }}</InfoBadge>
              <ErrorBadge v-else>{{ t('storageMonitoring.full') }}</ErrorBadge>
            </td>
            <td class="p-2 text-sm">
              <span v-if="station.presetName" class="text-[var(--text)]">{{ station.presetName }}</span>
              <span v-else class="text-[var(--text-muted)]">{{ t('storageMonitoring.defaultQuota') }}</span>
            </td>
            <td class="text-right p-2">
              <div class="flex gap-1 justify-end">
                <SecondaryButton @click="handleRecalculateStation(station.stationId)">
                  <font-awesome-icon :icon="['fas', 'arrows-rotate']"/>
                </SecondaryButton>
                <SecondaryButton @click="handleResetQuotas(station.stationId)">
                  <font-awesome-icon :icon="['fas', 'rotate-left']"/>
                </SecondaryButton>
              </div>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <!-- Preset Create/Edit Modal -->
      <Modal v-model="showPresetModal">
        <h3 class="text-lg font-semibold mb-4">{{ editingPreset ? t('storageMonitoring.editPreset') : t('storageMonitoring.createPreset') }}</h3>
        <div class="space-y-3">
          <div>
            <label class="block text-sm font-medium mb-1">{{ t('storageMonitoring.presetName') }}</label>
            <TextInput v-model="presetName"/>
          </div>
          <div v-for="(field, key) in presetFields" :key="key">
            <label class="block text-sm font-medium mb-1">{{ {total: t('storageMonitoring.total'), kb: t('storageMonitoring.categories.kbFiles'), board: t('storageMonitoring.categories.boardAttachments'), images: t('storageMonitoring.categories.images'), pages: t('storageMonitoring.categories.pageImages'), perFile: t('storageMonitoring.perFile'), perImage: t('storageMonitoring.perImage')}[key] }}</label>
            <div class="flex gap-2">
              <BaseInput v-model="field.value" class="flex-1" placeholder="0" step="0.01" type="number"/>
              <SelectInput v-model="field.unit" class="w-24">
                <option v-for="u in sizeUnits" :key="u" :value="u">{{ u }}</option>
              </SelectInput>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <SecondaryButton @click="showPresetModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="savePreset">{{ t('common.save') }}</PrimaryButton>
        </div>
      </Modal>

      <!-- Apply Preset Modal -->
      <Modal v-model="showApplyModal">
        <h3 class="text-lg font-semibold mb-4">{{ t('storageMonitoring.applyPreset') }}</h3>
        <p class="text-sm mb-3">{{ t('storageMonitoring.selectStationsToApply') }}</p>
        <div class="max-h-64 overflow-y-auto space-y-1">
          <label v-for="station in stations" :key="station.stationId" class="flex items-center gap-2 p-1 hover:bg-[var(--bg-hover)] rounded cursor-pointer">
            <input v-model="selectedStations" :value="station.stationId" type="checkbox"/>
            <span>{{ station.stationName }}</span>
            <span class="text-xs text-[var(--text-muted)]">({{ station.quotaUsedPercent }}%)</span>
          </label>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <SecondaryButton @click="showApplyModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="selectedStations.length === 0" @click="handleApplyPreset">{{ t('storageMonitoring.apply') }}</PrimaryButton>
        </div>
      </Modal>

      <!-- Delete Confirm Modal -->
      <Modal v-model="showDeleteModal">
        <h3 class="text-lg font-semibold mb-4">{{ t('storageMonitoring.confirmDeletePreset') }}</h3>
        <p>{{ t('storageMonitoring.confirmDeletePresetText') }}</p>
        <div class="flex justify-end gap-2 mt-4">
          <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <DeleteButton :label="t('common.delete')" @click="handleDeletePreset"/>
        </div>
      </Modal>
    </template>
  </ViewContent>
</template>
