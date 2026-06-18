/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import BaseInput from '@/components/input/BaseInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {
  type AdminStationUsage,
  type StorageQuotaPreset,
  formatBytes,
  createPreset,
  updatePreset,
  deletePreset,
  applyPreset,
} from '@/api/storageMonitoring'

const props = defineProps<{
  presets: StorageQuotaPreset[]
  stations: AdminStationUsage[]
}>()

const emit = defineEmits<{ reload: [] }>()

const {t} = useI18n()

type SizeUnit = 'MiB' | 'GiB' | 'TiB'
const sizeUnits: SizeUnit[] = ['MiB', 'GiB', 'TiB']
const unitMultiplier: Record<SizeUnit, number> = {MiB: 1024 * 1024, GiB: 1024 * 1024 * 1024, TiB: 1024 * 1024 * 1024 * 1024}

interface SizeField { value: number, unit: SizeUnit }

function bytesToUnit(bytes: number): SizeField {
  if (bytes >= 1024 * 1024 * 1024 * 1024) return {value: Math.round(bytes / (1024 * 1024 * 1024 * 1024) * 100) / 100, unit: 'TiB'}
  if (bytes >= 1024 * 1024 * 1024) return {value: Math.round(bytes / (1024 * 1024 * 1024) * 100) / 100, unit: 'GiB'}
  return {value: Math.round(bytes / (1024 * 1024) * 100) / 100, unit: 'MiB'}
}

function toBytes(field: SizeField): number {
  return Math.round(field.value * unitMultiplier[field.unit])
}

const showPresetModal = ref(false)
const editingPreset = ref<StorageQuotaPreset | null>(null)
const presetName = ref('')
const presetFields = ref(defaultFields())
const showApplyModal = ref(false)
const applyPresetId = ref<number | null>(null)
const selectedStations = ref<string[]>([])
const showDeleteModal = ref(false)
const deletingPresetId = ref<number | null>(null)

function defaultFields() {
  return {
    total: {value: 5, unit: 'GiB'} as SizeField,
    kb: {value: 4, unit: 'GiB'} as SizeField,
    board: {value: 3, unit: 'GiB'} as SizeField,
    images: {value: 1, unit: 'GiB'} as SizeField,
    pages: {value: 500, unit: 'MiB'} as SizeField,
    perFile: {value: 50, unit: 'MiB'} as SizeField,
    perImage: {value: 5, unit: 'MiB'} as SizeField,
  }
}

const fieldLabels: Record<string, string> = {
  total: 'storageMonitoring.total',
  kb: 'storageMonitoring.categories.kbFiles',
  board: 'storageMonitoring.categories.boardAttachments',
  images: 'storageMonitoring.categories.images',
  pages: 'storageMonitoring.categories.pageImages',
  perFile: 'storageMonitoring.perFile',
  perImage: 'storageMonitoring.perImage',
}

function openCreate() {
  editingPreset.value = null
  presetName.value = ''
  presetFields.value = defaultFields()
  showPresetModal.value = true
}

function openEdit(preset: StorageQuotaPreset) {
  editingPreset.value = preset
  presetName.value = preset.name
  presetFields.value = {
    total: bytesToUnit(preset.total), kb: bytesToUnit(preset.kb), board: bytesToUnit(preset.board),
    images: bytesToUnit(preset.images), pages: bytesToUnit(preset.pages),
    perFile: bytesToUnit(preset.perFile), perImage: bytesToUnit(preset.perImage),
  }
  showPresetModal.value = true
}

async function save() {
  const f = presetFields.value
  const payload = {
    name: presetName.value, total: toBytes(f.total), kb: toBytes(f.kb), board: toBytes(f.board),
    images: toBytes(f.images), pages: toBytes(f.pages), perFile: toBytes(f.perFile), perImage: toBytes(f.perImage),
  }
  if (editingPreset.value) await updatePreset(editingPreset.value.id, payload)
  else await createPreset(payload)
  showPresetModal.value = false
  emit('reload')
}

function confirmDelete(id: number) { deletingPresetId.value = id; showDeleteModal.value = true }

async function handleDelete() {
  if (deletingPresetId.value != null) {
    await deletePreset(deletingPresetId.value)
    showDeleteModal.value = false
    emit('reload')
  }
}

function openApply(presetId: number) {
  applyPresetId.value = presetId
  selectedStations.value = props.stations.filter(s => s.presetId === presetId).map(s => s.stationId)
  showApplyModal.value = true
}

async function handleApply() {
  if (applyPresetId.value != null && selectedStations.value.length > 0) {
    await applyPreset(applyPresetId.value, selectedStations.value)
    showApplyModal.value = false
    emit('reload')
  }
}

function toggleStation(id: string) {
  const idx = selectedStations.value.indexOf(id)
  if (idx >= 0) selectedStations.value.splice(idx, 1)
  else selectedStations.value.push(id)
}
</script>

<template>
  <div class="mb-6">
    <SectionHeader>{{ t('storageMonitoring.presets') }}</SectionHeader>
    <div class="flex flex-wrap gap-2 mb-3">
      <PrimaryButton @click="openCreate">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('storageMonitoring.createPreset') }}
      </PrimaryButton>
    </div>
    <div v-if="presets.length > 0" class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
        <tr class="border-b border-(--border)">
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
        <tr v-for="preset in presets" :key="preset.id" class="border-b border-(--border) hover:bg-(--bg-hover)">
          <td class="p-2 font-medium">{{ preset.name }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.total) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.kb) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.board) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.images) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.pages) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.perFile) }}</td>
          <td class="text-right p-2">{{ formatBytes(preset.perImage) }}</td>
          <td class="text-right p-2 flex gap-1 justify-end">
            <EditButton :label="t('storageMonitoring.editPreset')" @click="openEdit(preset)"/>
            <SecondaryButton @click="openApply(preset.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('storageMonitoring.apply') }}
            </SecondaryButton>
            <DeleteButton :label="t('storageMonitoring.deletePreset')" @click="confirmDelete(preset.id)"/>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="text-(--text-muted) text-sm">{{ t('storageMonitoring.noPresets') }}</p>

    <!-- Preset Create/Edit Modal -->
    <Modal v-model="showPresetModal">
      <SubHeader>{{ editingPreset ? t('storageMonitoring.editPreset') : t('storageMonitoring.createPreset') }}</SubHeader>
      <div class="space-y-3 mt-3">
        <div>
          <label class="block text-sm font-medium mb-1">{{ t('storageMonitoring.presetName') }}</label>
          <TextInput v-model="presetName"/>
        </div>
        <div v-for="(field, key) in presetFields" :key="key">
          <label class="block text-sm font-medium mb-1">{{ t(fieldLabels[key]) }}</label>
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
        <PrimaryButton @click="save">{{ t('common.save') }}</PrimaryButton>
      </div>
    </Modal>

    <!-- Apply Preset Modal -->
    <Modal v-model="showApplyModal">
      <SubHeader>{{ t('storageMonitoring.applyPreset') }}</SubHeader>
      <p class="text-sm mb-3 mt-3">{{ t('storageMonitoring.selectStationsToApply') }}</p>
      <div class="max-h-64 overflow-y-auto space-y-1">
        <label v-for="station in stations" :key="station.stationId" class="flex items-center gap-2 p-1 hover:bg-(--bg-hover) rounded cursor-pointer">
          <ToggleInput :model-value="selectedStations.includes(station.stationId)" @update:model-value="toggleStation(station.stationId)"/>
          <span>{{ station.stationName }}</span>
          <span class="text-xs text-(--text-muted)">({{ station.quotaUsedPercent }}%)</span>
        </label>
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showApplyModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="selectedStations.length === 0" @click="handleApply">{{ t('storageMonitoring.apply') }}</PrimaryButton>
      </div>
    </Modal>

    <!-- Delete Confirm Modal -->
    <Modal v-model="showDeleteModal">
      <SubHeader>{{ t('storageMonitoring.confirmDeletePreset') }}</SubHeader>
      <p class="mt-3">{{ t('storageMonitoring.confirmDeletePresetText') }}</p>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <DeleteButton :label="t('common.delete')" @click="handleDelete"/>
      </div>
    </Modal>
  </div>
</template>
