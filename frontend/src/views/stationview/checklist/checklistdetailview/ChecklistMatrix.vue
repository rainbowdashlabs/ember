/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {checklists} from '@/api'
import type {ChecklistColumnDto, ChecklistDetail, ChecklistEntryDto} from '@/api/types'
import ChecklistColumnHeader from './ChecklistColumnHeader.vue'
import ChecklistMatrixRow from './ChecklistMatrixRow.vue'
import ChecklistColumnEditor from './ChecklistColumnEditor.vue'

const props = defineProps<{
  detail: ChecklistDetail
  visibleEntries: ChecklistEntryDto[]
}>()

const columnFilters = defineModel<Record<number, 'any' | 'checked' | 'unchecked'>>('columnFilters', {required: true})

const emit = defineEmits<{
  (e: 'cell-change'): void
  (e: 'delete-entry', entryId: number): void
  (e: 'bulk-set', columnId: number, entryIds: number[], checked: boolean): void
  (e: 'columns-changed'): void
}>()

const {t} = useI18n()

const editingColumn = ref<ChecklistColumnDto | null>(null)
const showAddColumn = ref(false)

function setFilter(columnId: number, value: 'any' | 'checked' | 'unchecked') {
  columnFilters.value = {...columnFilters.value, [columnId]: value}
}

function visibleEntryIds(): number[] {
  return props.visibleEntries.map(e => e.id)
}

function onBulkAll(columnId: number, checked: boolean) {
  emit('bulk-set', columnId, visibleEntryIds(), checked)
}

async function onColumnChanged() {
  editingColumn.value = null
  showAddColumn.value = false
  emit('columns-changed')
}

const cellByKey = computed(() => {
  const map = new Map<string, boolean>()
  for (const cell of props.detail.cells) {
    map.set(`${cell.entryId}:${cell.columnId}`, cell.checked)
  }
  return map
})

function cellNote(entryId: number, columnId: number): string | null {
  const cell = props.detail.cells.find(c => c.entryId === entryId && c.columnId === columnId)
  return cell?.note ?? null
}

function isChecked(entryId: number, columnId: number): boolean {
  return cellByKey.value.get(`${entryId}:${columnId}`) ?? false
}
</script>

<template>
  <template v-if="detail.columns.length === 0">
    <EmptyState>{{ t('checklist.columnsEmpty') }}</EmptyState>
    <div class="text-center mt-2">
      <PrimaryButton @click="showAddColumn = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('checklist.addFirstColumn') }}
      </PrimaryButton>
    </div>
  </template>

  <template v-else>
    <div class="overflow-x-auto">
      <table class="border-collapse min-w-full text-sm">
        <thead>
        <tr>
          <th class="sticky left-0 z-10 bg-bg-light dark:bg-bg-dark text-left p-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
            <div class="flex items-center justify-between gap-2">
              <span class="font-semibold">{{ t('checklist.memberSet') }}</span>
              <PrimaryButton class="text-xs" @click="showAddColumn = true">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
                {{ t('checklist.addColumn') }}
              </PrimaryButton>
            </div>
          </th>
          <ChecklistColumnHeader
              v-for="column in detail.columns"
              :key="column.id"
              :column="column"
              :filter="columnFilters[column.id] ?? 'any'"
              :visible-count="visibleEntries.length"
              @set-filter="(v) => setFilter(column.id, v)"
              @edit="editingColumn = column"
              @bulk-tick="onBulkAll(column.id, true)"
              @bulk-clear="onBulkAll(column.id, false)"
          />
        </tr>
        </thead>
        <tbody>
        <ChecklistMatrixRow
            v-for="entry in visibleEntries"
            :key="entry.id"
            :checklist-id="detail.id"
            :entry="entry"
            :columns="detail.columns"
            :is-checked-fn="isChecked"
            :note-fn="cellNote"
            @cell-changed="emit('cell-change')"
            @delete="emit('delete-entry', entry.id)"
        />
        </tbody>
      </table>
    </div>

    <ChecklistColumnEditor
        v-if="editingColumn"
        :checklist-id="detail.id"
        :column="editingColumn"
        :total-columns="detail.columns.length"
        @close="editingColumn = null"
        @changed="onColumnChanged"
    />
    <ChecklistColumnEditor
        v-if="showAddColumn"
        :checklist-id="detail.id"
        :total-columns="detail.columns.length"
        @close="showAddColumn = false"
        @changed="onColumnChanged"
    />
  </template>
</template>
