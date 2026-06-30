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
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {ChecklistColumnDto, ChecklistEntryDto} from '@/api/types'
import ChecklistCellToggle from './ChecklistCellToggle.vue'

const props = defineProps<{
  checklistId: number
  entry: ChecklistEntryDto
  columns: ChecklistColumnDto[]
  isCheckedFn: (entryId: number, columnId: number) => boolean
  noteFn: (entryId: number, columnId: number) => string | null
}>()

const emit = defineEmits<{
  (e: 'cell-changed'): void
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
    <td class="sticky left-0 z-10 bg-bg-light dark:bg-bg-dark p-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
      <div class="flex items-center gap-2 min-w-[200px]">
        <div class="flex-1 min-w-0">
          <div class="font-medium truncate">{{ entry.memberName }}</div>
          <div class="flex flex-wrap gap-1 mt-1">
            <SecondaryBadge v-if="entry.deletedAt">{{ t('checklist.previouslyRemoved') }}</SecondaryBadge>
            <InfoBadge v-else-if="!entry.inFilter">{{ t('checklist.notInFilter') }}</InfoBadge>
          </div>
        </div>
        <IconButton
            v-if="!entry.deletedAt"
            :icon="['fas', 'trash']"
            :label="t('checklist.deleteRow')"
            @click="confirmDelete = true"
        />
      </div>
    </td>
    <td
        v-for="column in columns"
        :key="column.id"
        class="p-2 border-b border-bg-light-accent dark:border-bg-dark-accent text-center"
    >
      <ChecklistCellToggle
          :checklist-id="checklistId"
          :entry-id="entry.id"
          :column-id="column.id"
          :checked="isCheckedFn(entry.id, column.id)"
          :note="noteFn(entry.id, column.id)"
          :disabled="!!entry.deletedAt"
          @changed="emit('cell-changed')"
      />
    </td>

    <Modal v-model="confirmDelete" size="sm">
      <div class="space-y-3">
        <div class="font-semibold">{{ t('checklist.deleteRowTitle') }}</div>
        <p>{{ t('checklist.deleteRowMessage', {name: entry.memberName}) }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="confirmDelete = false">{{ t('checklist.cancel') }}</SecondaryButton>
          <DeleteButton @click="applyDelete">{{ t('checklist.deleteRow') }}</DeleteButton>
        </div>
      </div>
    </Modal>
  </tr>
</template>
