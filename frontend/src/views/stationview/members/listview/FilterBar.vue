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
import MemberFilterBar from '@/components/input/filter/MemberFilterBar.vue'
import type { FilterOption, FilterCriteria } from '@/components/input/filter/MemberFilterBar.vue'
import type { ProfileField } from '@/api/types'
import { useBreakpoint } from '@/composables/useBreakpoint'
import FieldLabel from '@/components/typography/FieldLabel.vue'

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
  roles: FilterOption[]
  groups: FilterOption[]
  tags: FilterOption[]
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
  filter: [criteria: FilterCriteria]
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
    <SecondaryButton
      v-for="(preset, idx) in savedFilters"
      :key="idx"
      @click="emit('applyFilter', preset)"
    >
      {{ preset.name }}
      <span class="text-(--text-muted) hover:text-error ml-1" @click.stop="emit('deleteFilter', idx)">&times;</span>
    </SecondaryButton>
  </div>

  <!-- Role / Group / Tag filter -->
  <MemberFilterBar
      :roles="roles"
      :groups="groups"
      :tags="tags"
      @filter="criteria => emit('filter', criteria)"
  />

  <div class="space-y-2">
    <TextInput :model-value="filterText" :placeholder="t('membersList.filter')" class="w-full" @update:model-value="(v: string | undefined) => emit('update:filterText', v ?? '')" />
    <div class="grid grid-cols-2 sm:flex sm:flex-wrap sm:items-center gap-2">
      <div class="relative">
        <SecondaryButton :icon="['fas', 'table-columns']" :full-width="isMobile" @click="showColumnPicker = !showColumnPicker">
          {{ t('membersList.columns') }}
        </SecondaryButton>
        <div v-if="showColumnPicker" class="absolute right-0 top-full mt-1 z-10 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-3 min-w-48 space-y-1">
          <p class="text-xs font-semibold text-(--text-muted) mb-2">{{ t('membersList.extraColumns') }}</p>
          <div v-if="nonOverviewFields.length === 0" class="text-xs text-(--text-muted)">{{ t('membersList.noExtraColumns') }}</div>
          <FieldLabel inline v-for="field in nonOverviewFields" :key="field.id" class="cursor-pointer py-0.5">
            <CheckboxInput :model-value="extraColumnIds.has(field.id)" @update:model-value="emit('toggleColumn', field.id)" />
            {{ field.name }}
          </FieldLabel>
        </div>
      </div>
      <SecondaryButton :icon="['fas', 'xmark']" :full-width="isMobile" @click="emit('clearFilters')">
        {{ t('membersList.clearFilters') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'star']" :full-width="isMobile" @click="showSaveFilter = !showSaveFilter">
        {{ t('membersList.saveFilter') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'file-export']" :full-width="isMobile" @click="emit('toggleExport')">
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
