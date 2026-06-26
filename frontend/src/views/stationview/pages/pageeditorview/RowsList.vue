/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EditorRow from './EditorRow.vue'
import PageEditorAddRowDivider from './PageEditorAddRowDivider.vue'
import type {RowEditData} from './EditorRow.vue'

const props = defineProps<{
  rows: RowEditData[]
  pageId: number
  stationUid: string
  preview: boolean
  hasClipboard: boolean
  clipboardType: string | null
}>()

const emit = defineEmits<{
  (e: 'update:row', index: number, row: RowEditData): void
  (e: 'delete', index: number): void
  (e: 'move-up', index: number): void
  (e: 'move-down', index: number): void
  (e: 'add-at', index: number): void
  (e: 'paste-at', index: number): void
}>()
</script>

<template>
  <div class="space-y-2">
    <div v-for="(row, index) in props.rows" :key="row.id + '-' + index">
      <EditorRow
          :row="row"
          :page-id="props.pageId"
          :station-uid="props.stationUid"
          :preview="props.preview"
          :is-first="index === 0"
          :is-last="index === props.rows.length - 1"
          @update:row="(r: RowEditData) => emit('update:row', index, r)"
          @delete="emit('delete', index)"
          @move-up="emit('move-up', index)"
          @move-down="emit('move-down', index)"
      />
      <PageEditorAddRowDivider
          v-if="!props.preview"
          :has-clipboard="props.hasClipboard"
          :clipboard-type="props.clipboardType"
          @add="emit('add-at', index + 1)"
          @paste="emit('paste-at', index + 1)"
      />
    </div>
  </div>
</template>
