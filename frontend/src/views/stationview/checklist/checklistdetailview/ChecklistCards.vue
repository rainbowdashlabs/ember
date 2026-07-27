/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {ChecklistCellDto, ChecklistColumnDto, ChecklistEntryDto} from '@/api/types'
import ChecklistCellToggle from './ChecklistCellToggle.vue'

const props = defineProps<{
  checklistId: number
  entries: ChecklistEntryDto[]
  columns: ChecklistColumnDto[]
  isCheckedFn: (entryId: number, columnId: number) => boolean
  noteFn: (entryId: number, columnId: number) => string | null
  readOnly: boolean
}>()

const emit = defineEmits<{
  (e: 'cell-change', cell: ChecklistCellDto): void
  (e: 'delete-entry', entryId: number): void
}>()

const {t} = useI18n()

const {show: showDelete, target: pendingDelete, requestDelete, confirm: confirmDelete} = useConfirmDelete<ChecklistEntryDto>({
  onDelete: async entry => { emit('delete-entry', entry.id) },
})
</script>

<template>
  <div class="space-y-3">
    <NeutralContainer
        v-for="entry in entries"
        :key="entry.id"
        :class="entry.deletedAt ? 'opacity-60' : ''"
    >
      <div class="flex items-start justify-between gap-2 mb-2">
        <div class="min-w-0">
          <div class="font-semibold truncate">{{ entry.memberName }}</div>
          <div v-if="entry.deletedAt || !entry.inFilter" class="flex flex-wrap gap-1 mt-1">
            <SecondaryBadge v-if="entry.deletedAt">{{ t('checklist.previouslyRemoved') }}</SecondaryBadge>
            <InfoBadge v-else-if="!entry.inFilter">{{ t('checklist.notInFilter') }}</InfoBadge>
          </div>
        </div>
        <IconButton
            v-if="!readOnly && !entry.deletedAt"
            :icon="['fas', 'trash']"
            :label="t('checklist.deleteRow')"
            @click="requestDelete(entry)"
        />
      </div>

      <ul class="divide-y divide-bg-light-accent dark:divide-bg-dark-accent">
        <li
            v-for="column in columns"
            :key="column.id"
            class="py-2 flex items-start gap-2"
        >
          <div class="flex-1 min-w-0">
            <div class="text-sm font-medium truncate">{{ column.label }}</div>
            <div
                v-if="column.description"
                class="text-xs text-(--text-muted) truncate"
            >{{ column.description }}</div>
          </div>
          <div class="shrink-0">
            <ChecklistCellToggle
                :checklist-id="checklistId"
                :entry-id="entry.id"
                :column-id="column.id"
                :checked="isCheckedFn(entry.id, column.id)"
                :note="noteFn(entry.id, column.id)"
                :disabled="!!entry.deletedAt || readOnly"
                @changed="(cell) => emit('cell-change', cell)"
            />
          </div>
        </li>
      </ul>
    </NeutralContainer>

    <ConfirmDeleteModal
        v-if="pendingDelete"
        v-model="showDelete"
        :message="t('checklist.deleteRowMessage', {name: pendingDelete?.memberName ?? ''})"
        @confirm="confirmDelete"
    />
  </div>
</template>
