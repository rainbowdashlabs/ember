/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'
import { useBreakpoint } from '@/composables/useBreakpoint'
import type { MemberListConfig } from './useMemberListConfig'

/**
 * Everything above the member table: saved filters, who to show, the search, the columns, and
 * the export.
 */
const props = defineProps<{
  config: MemberListConfig
}>()

const { t } = useI18n()
const { isMobile } = useBreakpoint()

const c = props.config
const table = c.table
const exporting = c.exporting

const showSaveFilter = ref(false)
const filterPresetName = ref('')

const restriction = ref<RestrictionSelection>(emptyRestriction())

function emitFilter() {
  c.onMemberFilter({
    userTypes: restriction.value.userTypes,
    groupIds: restriction.value.groupIds,
    tagIds: restriction.value.tagIds,
    mode: restriction.value.mode,
  })
}

function submitSaveFilter() {
  if (!filterPresetName.value.trim()) return
  c.saveCurrentFilter(filterPresetName.value.trim())
  filterPresetName.value = ''
  showSaveFilter.value = false
}
</script>

<template>
  <div v-if="c.savedFilters.value.length > 0" class="flex flex-wrap items-center gap-2">
    <span class="text-xs text-(--text-muted)">{{ t('membersList.savedFilters') }}:</span>
    <SecondaryButton v-for="(preset, idx) in c.savedFilters.value" :key="idx" @click="c.applyFilter(preset)">
      {{ preset.name }}
      <span class="text-(--text-muted) hover:text-error ml-1" @click.stop="c.deleteFilter(idx)">&times;</span>
    </SecondaryButton>
  </div>

  <RestrictionPicker v-model="restriction" :groups="c.allGroups.value" :tags="c.allTags.value" @update:model-value="emitFilter"/>

  <div class="space-y-2">
    <SearchInput v-model="table.search" :placeholder="t('membersList.filter')" autofocus/>
    <div class="grid grid-cols-2 sm:flex sm:flex-wrap sm:items-center gap-2">
      <TableColumnPicker :empty-label="t('membersList.noExtraColumns')" :full-width="isMobile" :table="table"/>
      <SecondaryButton :icon="['fas', 'xmark']" :full-width="isMobile" @click="c.clearFilters">
        {{ t('membersList.clearFilters') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'star']" :full-width="isMobile" @click="showSaveFilter = !showSaveFilter">
        {{ t('membersList.saveFilter') }}
      </SecondaryButton>
      <SecondaryButton v-if="c.canExport.value" :icon="['fas', 'file-export']" :full-width="isMobile"
                       data-testid="members-export" @click="exporting.toggleExportMode">
        {{ exporting.exportMode.value ? t('membersList.export.cancel') : t('membersList.export.button') }}
      </SecondaryButton>
      <template v-if="exporting.exportMode.value">
        <span class="col-span-2 sm:col-span-1 text-xs text-(--text-muted)">
          {{ t('membersList.export.selected', { count: exporting.selectedIds.value.size }) }}
        </span>
        <PrimaryButton :full-width="isMobile" class="col-span-2 sm:col-span-1" :disabled="exporting.selectedIds.value.size === 0"
                       data-testid="members-export-continue" @click="exporting.openExportModal">
          {{ t('membersList.export.continue') }}
        </PrimaryButton>
      </template>
    </div>
  </div>

  <div v-if="showSaveFilter" class="flex items-center gap-2">
    <TextInput v-model="filterPresetName" :placeholder="t('membersList.filterName')" class="flex-1"/>
    <SecondaryButton :disabled="!filterPresetName.trim()" @click="submitSaveFilter">{{ t('membersList.saveFilterSubmit') }}</SecondaryButton>
  </div>
</template>
