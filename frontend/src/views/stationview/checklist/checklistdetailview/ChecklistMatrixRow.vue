/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {ChecklistCellDto, ChecklistColumnDto, ChecklistEntryDto} from '@/api/types'
import ChecklistCellToggle from './ChecklistCellToggle.vue'

const props = defineProps<{
  checklistId: number
  entry: ChecklistEntryDto
  columns: ChecklistColumnDto[]
  isCheckedFn: (entryId: number, columnId: number) => boolean
  noteFn: (entryId: number, columnId: number) => string | null
}>()

const emit = defineEmits<{
  (e: 'cell-changed', cell: ChecklistCellDto): void
  (e: 'delete'): void
}>()

const {t} = useI18n()
const confirmDelete = ref(false)

function applyDelete() {
  emit('delete')
  confirmDelete.value = false
}
</script>

<template>
  <tr :class="entry.deletedAt ? 'opacity-60' : ''">
    <td class="sticky left-0 z-10 bg-bg-light dark:bg-bg-dark px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent">
      <div class="flex items-center gap-2 min-w-[200px]">
        <IconButton
            v-if="!entry.deletedAt"
            :icon="['fas', 'trash']"
            :label="t('checklist.deleteRow')"
            @click="confirmDelete = true"
        />
        <div class="flex-1 min-w-0">
          <div class="font-medium truncate">{{ entry.memberName }}</div>
          <div v-if="entry.deletedAt || !entry.inFilter" class="flex flex-wrap gap-1 mt-0.5">
            <SecondaryBadge v-if="entry.deletedAt">{{ t('checklist.previouslyRemoved') }}</SecondaryBadge>
            <InfoBadge v-else-if="!entry.inFilter">{{ t('checklist.notInFilter') }}</InfoBadge>
          </div>
        </div>
      </div>
    </td>
    <td
        v-for="column in columns"
        :key="column.id"
        class="px-2 py-1 border-b border-bg-light-accent dark:border-bg-dark-accent text-left align-top"
    >
      <ChecklistCellToggle
          :checklist-id="checklistId"
          :entry-id="entry.id"
          :column-id="column.id"
          :checked="isCheckedFn(entry.id, column.id)"
          :note="noteFn(entry.id, column.id)"
          :disabled="!!entry.deletedAt"
          @changed="(cell) => emit('cell-changed', cell)"
      />
    </td>

    <ConfirmDeleteModal
        v-model="confirmDelete"
        :message="t('checklist.deleteRowMessage', {name: entry.memberName})"
        @confirm="applyDelete"
    />
  </tr>
</template>
