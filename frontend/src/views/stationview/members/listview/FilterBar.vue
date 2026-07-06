/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'
import type { FilterCriteria } from '@/composables/useMemberFilter'
import type { ProfileField, MemberGroup, UserTag } from '@/api/types'
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

const filterText = defineModel<string>('filterText', {required: true})

const props = defineProps<{
  savedFilters: SavedFilter[]
  overviewFields: ProfileField[]
  nonOverviewFields: ProfileField[]
  extraColumnIds: Set<number>
  hiddenColumnIds: Set<number>
  exportMode: boolean
  selectedCount: number
  canExport: boolean
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const sortedColumnFields = computed(() =>
  [...props.overviewFields, ...props.nonOverviewFields].sort((a, b) =>
    a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }),
  ),
)
const overviewIds = computed(() => new Set(props.overviewFields.map(f => f.id)))
function isColumnVisible(fieldId: number): boolean {
  return overviewIds.value.has(fieldId)
    ? !props.hiddenColumnIds.has(fieldId)
    : props.extraColumnIds.has(fieldId)
}

const emit = defineEmits<{
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

const restriction = ref<RestrictionSelection>(emptyRestriction())

function emitFilter() {
  emit('filter', {
    userTypes: restriction.value.userTypes,
    groupIds: restriction.value.groupIds,
    tagIds: restriction.value.tagIds,
    mode: restriction.value.mode,
  })
}

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

  <!-- Restriction-based filter -->
  <RestrictionPicker
      :groups="groups"
      :tags="tags"
      v-model="restriction"
      @update:model-value="emitFilter"
  />

  <div class="space-y-2">
    <SearchInput v-model="filterText" :placeholder="t('membersList.filter')" autofocus />
    <div class="grid grid-cols-2 sm:flex sm:flex-wrap sm:items-center gap-2">
      <div class="relative">
        <SecondaryButton :icon="['fas', 'table-columns']" :full-width="isMobile" @click="showColumnPicker = !showColumnPicker">
          {{ t('membersList.columns') }}
        </SecondaryButton>
        <div v-if="showColumnPicker" class="absolute right-0 top-full mt-1 z-10 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-3 min-w-48 space-y-1">
          <p class="text-xs font-semibold text-(--text-muted) mb-2">{{ t('membersList.columns') }}</p>
          <div v-if="sortedColumnFields.length === 0" class="text-xs text-(--text-muted)">{{ t('membersList.noExtraColumns') }}</div>
          <FieldLabel v-for="field in sortedColumnFields" :key="field.id" inline class="cursor-pointer py-0.5">
            <CheckboxInput :model-value="isColumnVisible(field.id)" @update:model-value="emit('toggleColumn', field.id)" />
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
      <SecondaryButton v-if="canExport" :icon="['fas', 'file-export']" :full-width="isMobile" @click="emit('toggleExport')">
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
