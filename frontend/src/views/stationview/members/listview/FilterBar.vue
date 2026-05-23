/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { ProfileField } from '@/api/types'
import { useBreakpoint } from '@/composables/useBreakpoint'

const { t } = useI18n()
const { isMobile } = useBreakpoint()

interface SavedFilter {
  id?: number
  name: string
  tab: string
  textFilters: Record<string, string>
  multiFilters: Record<string, string[]>
}

defineProps<{
  filterText: string
  savedFilters: SavedFilter[]
  nonOverviewFields: ProfileField[]
  extraColumnIds: Set<number>
  exportMode: boolean
  selectedCount: number
}>()

const emit = defineEmits<{
  'update:filterText': [value: string]
  clearFilters: []
  applyFilter: [preset: SavedFilter]
  deleteFilter: [index: number]
  saveFilter: [name: string]
  toggleColumn: [fieldId: number]
  toggleExport: []
  exportContinue: []
}>()

const showColumnPicker = ref(false)
const showSaveFilter = ref(false)
const filterPresetName = ref('')

function submitSaveFilter() {
  if (!filterPresetName.value.trim()) return
  emit('saveFilter', filterPresetName.value.trim())
  filterPresetName.value = ''
  showSaveFilter.value = false
}
</script>

<template>
  <!-- Saved filters -->
  <div v-if="savedFilters.length > 0" class="flex flex-wrap items-center gap-2">
    <span class="text-xs text-(--text-muted)">{{ t('membersList.savedFilters') }}:</span>
    <button
      v-for="(preset, idx) in savedFilters"
      :key="idx"
      class="inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent hover:border-primary transition-colors"
      @click="emit('applyFilter', preset)"
    >
      {{ preset.name }}
      <span class="text-(--text-muted) hover:text-error ml-1" @click.stop="emit('deleteFilter', idx)">&times;</span>
    </button>
  </div>

  <div class="space-y-2">
    <TextInput :model-value="filterText" :placeholder="t('membersList.filter')" class="w-full" @update:model-value="(v: string | undefined) => emit('update:filterText', v ?? '')" />
    <div class="grid grid-cols-2 sm:flex sm:flex-wrap sm:items-center gap-2">
      <div class="relative">
        <SecondaryButton :full-width="isMobile" @click="showColumnPicker = !showColumnPicker">
          <font-awesome-icon :icon="['fas', 'table-columns']" class="mr-1" />
          {{ t('membersList.columns') }}
        </SecondaryButton>
        <div v-if="showColumnPicker" class="absolute right-0 top-full mt-1 z-10 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-3 min-w-48 space-y-1">
          <p class="text-xs font-semibold text-(--text-muted) mb-2">{{ t('membersList.extraColumns') }}</p>
          <div v-if="nonOverviewFields.length === 0" class="text-xs text-(--text-muted)">{{ t('membersList.noExtraColumns') }}</div>
          <label v-for="field in nonOverviewFields" :key="field.id" class="flex items-center gap-2 cursor-pointer text-sm py-0.5">
            <CheckboxInput :model-value="extraColumnIds.has(field.id)" @update:model-value="emit('toggleColumn', field.id)" />
            {{ field.name }}
          </label>
        </div>
      </div>
      <SecondaryButton :full-width="isMobile" @click="emit('clearFilters')">
        <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" />
        {{ t('membersList.clearFilters') }}
      </SecondaryButton>
      <SecondaryButton :full-width="isMobile" @click="showSaveFilter = !showSaveFilter">
        <font-awesome-icon :icon="['fas', 'star']" class="mr-1" />
        {{ t('membersList.saveFilter') }}
      </SecondaryButton>
      <SecondaryButton :full-width="isMobile" @click="emit('toggleExport')">
        <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1" />
        {{ exportMode ? t('membersList.export.cancel') : t('membersList.export.button') }}
      </SecondaryButton>
      <template v-if="exportMode">
        <span class="col-span-2 sm:col-span-1 text-xs text-(--text-muted)">{{ t('membersList.export.selected', { count: selectedCount }) }}</span>
        <PrimaryButton :full-width="isMobile" class="col-span-2 sm:col-span-1" :disabled="selectedCount === 0" @click="emit('exportContinue')">
          {{ t('membersList.export.continue') }}
        </PrimaryButton>
      </template>
    </div>
  </div>

  <!-- Save filter input -->
  <div v-if="showSaveFilter" class="flex items-center gap-2">
    <TextInput v-model="filterPresetName" :placeholder="t('membersList.filterName')" class="flex-1" />
    <SecondaryButton :disabled="!filterPresetName.trim()" @click="submitSaveFilter">{{ t('membersList.saveFilterSubmit') }}</SecondaryButton>
  </div>
</template>
