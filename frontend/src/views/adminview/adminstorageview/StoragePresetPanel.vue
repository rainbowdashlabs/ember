/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import QuotaFieldsInput from './QuotaFieldsInput.vue'
import type {QuotaTier, QuotaTierValues, StorageRoomRow} from '@/composables/useStorageQuotas'
import {
  defaultQuotaFields, fieldFromBytes, fieldToBytes, formatBytes, type QuotaFields,
} from '@/util/storage'

/**
 * The tiers an owner keeps and hands to its stations.
 *
 * <p>Writes nothing itself. Which tiers these are and where a change goes is the screen's business, and this
 * panel only says what somebody asked for, so the same table serves the instance and an association.
 */
const props = defineProps<{
  tiers: QuotaTier[]
  stations: StorageRoomRow[]
}>()

const emit = defineEmits<{
  save: [values: QuotaTierValues, tierId: number | null]
  remove: [tierId: number]
  apply: [tierId: number, stationIds: string[]]
}>()

const {t} = useI18n()

const showTierModal = ref(false)
const editingTier = ref<QuotaTier | null>(null)
const tierName = ref('')
const tierFields = ref<QuotaFields>(defaultQuotaFields())
const showApplyModal = ref(false)
const applyTierId = ref<number | null>(null)
const selectedStations = ref<string[]>([])
const showDeleteModal = ref(false)
const deletingTierId = ref<number | null>(null)

function openCreate() {
  editingTier.value = null
  tierName.value = ''
  tierFields.value = defaultQuotaFields()
  showTierModal.value = true
}

function openEdit(tier: QuotaTier) {
  editingTier.value = tier
  tierName.value = tier.name
  tierFields.value = {
    total: fieldFromBytes(tier.total), kb: fieldFromBytes(tier.kb), board: fieldFromBytes(tier.board),
    images: fieldFromBytes(tier.images), pages: fieldFromBytes(tier.pages),
    perFile: fieldFromBytes(tier.perFile), perImage: fieldFromBytes(tier.perImage),
  }
  showTierModal.value = true
}

/** A tier names every dimension, so anything left empty falls back to nothing rather than to a guess. */
function bytesOf(key: keyof QuotaFields): number {
  return fieldToBytes(tierFields.value[key]) ?? 0
}

function save() {
  emit('save', {
    name: tierName.value,
    total: bytesOf('total'),
    kb: bytesOf('kb'),
    board: bytesOf('board'),
    images: bytesOf('images'),
    pages: bytesOf('pages'),
    perFile: bytesOf('perFile'),
    perImage: bytesOf('perImage'),
  }, editingTier.value?.id ?? null)
  showTierModal.value = false
}

function confirmDelete(tierId: number) {
  deletingTierId.value = tierId
  showDeleteModal.value = true
}

function handleDelete() {
  if (deletingTierId.value === null) return
  emit('remove', deletingTierId.value)
  showDeleteModal.value = false
}

function openApply(tierId: number) {
  applyTierId.value = tierId
  selectedStations.value = props.stations.filter(station => station.presetId === tierId).map(s => s.stationId)
  showApplyModal.value = true
}

function handleApply() {
  if (applyTierId.value === null || selectedStations.value.length === 0) return
  emit('apply', applyTierId.value, [...selectedStations.value])
  showApplyModal.value = false
}

function toggleStation(id: string) {
  const index = selectedStations.value.indexOf(id)
  if (index >= 0) selectedStations.value.splice(index, 1)
  else selectedStations.value.push(id)
}
</script>

<template>
  <div class="mb-6">
    <SubHeader>{{ t('storageMonitoring.presets') }}</SubHeader>
    <div class="flex flex-wrap gap-2 mb-3">
      <PrimaryButton data-testid="tier-create" @click="openCreate">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('storageMonitoring.createPreset') }}
      </PrimaryButton>
    </div>
    <div v-if="props.tiers.length > 0" class="overflow-x-auto">
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
        <tr v-for="tier in props.tiers" :key="tier.id" class="border-b border-(--border) hover:bg-(--bg-hover)"
            data-testid="tier-row">
          <td class="p-2 font-medium">{{ tier.name }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.total) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.kb) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.board) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.images) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.pages) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.perFile) }}</td>
          <td class="text-right p-2">{{ formatBytes(tier.perImage) }}</td>
          <td class="text-right p-2 flex gap-1 justify-end">
            <EditButton :label="t('storageMonitoring.editPreset')" @click="openEdit(tier)"/>
            <SecondaryButton @click="openApply(tier.id)">
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('storageMonitoring.apply') }}
            </SecondaryButton>
            <DeleteButton :label="t('storageMonitoring.deletePreset')" @click="confirmDelete(tier.id)"/>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <p v-else class="text-(--text-muted) text-sm">{{ t('storageMonitoring.noPresets') }}</p>

    <Modal v-model="showTierModal">
      <SubHeader>{{ editingTier ? t('storageMonitoring.editPreset') : t('storageMonitoring.createPreset') }}</SubHeader>
      <div class="space-y-3 mt-3">
        <div>
          <label class="block text-sm font-medium mb-1">{{ t('storageMonitoring.presetName') }}</label>
          <TextInput v-model="tierName" data-testid="tier-name"/>
        </div>
        <QuotaFieldsInput :fields="tierFields"/>
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showTierModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton data-testid="tier-save" @click="save">{{ t('common.save') }}</PrimaryButton>
      </div>
    </Modal>

    <Modal v-model="showApplyModal">
      <SubHeader>{{ t('storageMonitoring.applyPreset') }}</SubHeader>
      <p class="text-sm mb-3 mt-3">{{ t('storageMonitoring.selectStationsToApply') }}</p>
      <div class="max-h-64 overflow-y-auto space-y-1">
        <label v-for="station in props.stations" :key="station.stationId"
               class="flex items-center gap-2 p-1 hover:bg-(--bg-hover) rounded cursor-pointer">
          <ToggleInput :model-value="selectedStations.includes(station.stationId)"
                       @update:model-value="toggleStation(station.stationId)"/>
          <span>{{ station.stationName }}</span>
          <span class="text-xs text-(--text-muted)">({{ station.quotaUsedPercent }}%)</span>
        </label>
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showApplyModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="selectedStations.length === 0" data-testid="tier-apply-save" @click="handleApply">
          {{ t('storageMonitoring.apply') }}
        </PrimaryButton>
      </div>
    </Modal>

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
