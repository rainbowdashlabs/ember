/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import AddRowDivider from '@/components/content/blockeditor/AddRowDivider.vue'
import RowsList from '@/components/content/blockeditor/RowsList.vue'
import AddRowDialog from '@/components/content/blockeditor/AddRowDialog.vue'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {CellContentType, type SaveRowRequest, type SaveCellRequest} from '@/api/pageManage'
import {usePageClipboard} from '@/composables/usePageClipboard'

/**
 * The block editor, without anything page-specific around it.
 *
 * <p>It is the rows list, the add-row dialog and the clipboard, and it knows nothing about slugs,
 * publishing or social images. That is what lets a news entry and a knowledge-base article be
 * written with the same editor rather than a look-alike: the page editor is one caller of this,
 * not its owner.
 */
const rows = defineModel<RowEditData[]>('rows', {required: true})

defineProps<{
  stationUid: string
  /** Read-only render of what the reader will see. */
  preview?: boolean
}>()

const emit = defineEmits<{
  (e: 'change'): void
}>()

const {pasteRow, hasClipboard, clipboardType} = usePageClipboard()

const addRowAt = ref<number | null>(null)
const showAddRowDialog = ref(false)

function markDirty() {
  emit('change')
}

function updateRow(index: number, row: RowEditData) {
  rows.value[index] = row
  markDirty()
}

function deleteRow(index: number) {
  rows.value.splice(index, 1)
  markDirty()
}

function moveRow(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= rows.value.length) return
  const items = [...rows.value]
  const [moved] = items.splice(index, 1)
  if (!moved) return
  items.splice(target, 0, moved)
  rows.value = items
  markDirty()
}

function openAddRowDialog(atIndex?: number) {
  addRowAt.value = atIndex ?? null
  showAddRowDialog.value = true
}

/**
 * Adds a row of the chosen column count, its width split evenly. The author moves the boundaries
 * afterwards; starting even is the only choice that assumes nothing.
 */
function addRow(columns: number) {
  const widthPercent = 100 / columns
  const newRow: RowEditData = {
    id: 0,
    sortOrder: rows.value.length,
    cells: Array.from({length: columns}, (_, i) => ({
      id: 0,
      sortOrder: i,
      widthPercent,
      contentType: CellContentType.EMPTY,
      content: '',
      config: {},
    })),
  }
  insertRow(newRow, addRowAt.value ?? undefined)
  showAddRowDialog.value = false
  addRowAt.value = null
}

function onPasteRow(atIndex?: number) {
  const data = pasteRow() as RowEditData | null
  if (!data) return
  insertRow({...data, id: 0, sortOrder: rows.value.length, cells: data.cells.map(c => ({...c, id: 0}))}, atIndex)
}

function insertRow(row: RowEditData, atIndex?: number) {
  if (atIndex != null) {
    rows.value.splice(atIndex, 0, row)
  } else {
    rows.value.push(row)
  }
  markDirty()
}
</script>

<template>
  <div class="space-y-4">
    <AddRowDivider
        v-if="!preview"
        :has-clipboard="hasClipboard"
        :clipboard-type="clipboardType"
        @add="openAddRowDialog(0)"
        @paste="onPasteRow(0)"
    />

    <RowsList
        :rows="rows"
        :station-uid="stationUid"
        :preview="!!preview"
        :has-clipboard="hasClipboard"
        :clipboard-type="clipboardType"
        @update:row="updateRow"
        @delete="deleteRow"
        @move-up="(i: number) => moveRow(i, -1)"
        @move-down="(i: number) => moveRow(i, 1)"
        @add-at="(i: number) => openAddRowDialog(i)"
        @paste-at="(i: number) => onPasteRow(i)"
    />

    <AddRowDialog v-model="showAddRowDialog" @select="addRow"/>
  </div>
</template>
