/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MappingHelp from './mappingstep/MappingHelp.vue'
import MappingRow from './mappingstep/MappingRow.vue'
import ValueMapEditorModal from './mappingstep/ValueMapEditorModal.vue'
import { createColumnMapping, SKIP_TARGET, type ColumnMapping } from './memberImport'

const { t } = useI18n()

const mappings = defineModel<ColumnMapping[]>('mappings', {required: true})

const props = defineProps<{
  headers: string[]
  sampleRows: string[][]
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  primaryGroupLabel: string
  managerCount: number
  needsValueMapFn: (mapping: ColumnMapping) => boolean
}>()

const editingValueMapIndex = ref<number | null>(null)
const editingValueMapEntries = ref<Array<{ from: string; to: string }>>([])

function getSampleValues(colIndex: number): string[] {
  return props.sampleRows.map(row => row[colIndex] ?? '').filter(v => v)
}

function isMerged(target: string): boolean {
  if (target === SKIP_TARGET) return false
  return mappings.value.filter(m => m.target === target).length > 1
}

function isSplit(index: number): boolean {
  return !!mappings.value[index]?.splitChar
}

function getSplitSiblings(csvColumn: string): number[] {
  return mappings.value
    .map((m, i) => m.csvColumn === csvColumn && m.splitChar !== '' ? i : -1)
    .filter(i => i >= 0)
}

function getSplitPreview(mapping: ColumnMapping): string[] {
  if (!mapping.splitChar) return []
  const colIdx = props.headers.indexOf(mapping.csvColumn)
  if (colIdx < 0) return []
  return props.sampleRows.map(row => {
    const val = row[colIdx]?.trim() ?? ''
    if (!val) return ''
    const parts = val.split(mapping.splitChar)
    const idx = mapping.splitIndex < 0 ? parts.length + mapping.splitIndex : mapping.splitIndex
    return parts[idx]?.trim() ?? ''
  }).filter(v => v)
}

function previewTextFor(mapping: ColumnMapping, index: number): string {
  if (isSplit(index)) return getSplitPreview(mapping).join(', ')
  return getSampleValues(props.headers.indexOf(mapping.csvColumn)).join(', ')
}

function updateMapping(index: number, partial: Partial<ColumnMapping>) {
  const current = mappings.value[index]
  if (!current) return
  const updated = [...mappings.value]
  updated[index] = { ...current, ...partial }
  mappings.value = updated
}

function splitColumn(index: number) {
  const m = mappings.value[index]
  if (!m) return
  const updated = [...mappings.value]
  updated.splice(index, 1,
    { ...m, splitChar: ' ', splitIndex: 0, target: SKIP_TARGET },
    { ...m, splitChar: ' ', splitIndex: 1, target: SKIP_TARGET },
  )
  mappings.value = updated
}

function unsplitColumn(index: number) {
  const m = mappings.value[index]
  if (!m) return
  const siblings = getSplitSiblings(m.csvColumn)
  const first = siblings[0]
  if (first === undefined) return
  const updated = [...mappings.value]
  for (const sibling of [...siblings].reverse()) {
    updated.splice(sibling, 1)
  }
  updated.splice(first, 0, createColumnMapping(m.csvColumn, SKIP_TARGET, m.mergeOrder))
  mappings.value = updated
}

function addSplitPart(index: number) {
  const m = mappings.value[index]
  if (!m) return
  const siblings = getSplitSiblings(m.csvColumn)
  const last = siblings[siblings.length - 1]
  if (last === undefined) return
  const newPart: ColumnMapping = {
    ...createColumnMapping(m.csvColumn, SKIP_TARGET, m.mergeOrder),
    splitChar: m.splitChar,
    splitIndex: siblings.length,
  }
  const updated = [...mappings.value]
  updated.splice(last + 1, 0, newPart)
  mappings.value = updated
}

function updateSplitChar(csvColumn: string, char: string) {
  mappings.value = mappings.value.map(m =>
    m.csvColumn === csvColumn && m.splitChar !== '' ? { ...m, splitChar: char } : m
  )
}

function openValueMapEditor(index: number) {
  const m = mappings.value[index]
  if (!m) return
  const existing = Object.entries(m.valueMap || {})
  const colIdx = props.headers.indexOf(m.csvColumn)
  editingValueMapEntries.value = existing.length > 0
    ? existing.map(([from, to]) => ({ from, to }))
    : getSampleValues(colIdx >= 0 ? colIdx : index).map(v => ({ from: v, to: '' }))
  editingValueMapIndex.value = index
}

function saveValueMap() {
  if (editingValueMapIndex.value === null) return
  const map: Record<string, string> = {}
  for (const e of editingValueMapEntries.value) {
    if (e.from && e.to) map[e.from] = e.to
  }
  updateMapping(editingValueMapIndex.value, { valueMap: map })
  editingValueMapIndex.value = null
}

function addValueMapEntry() {
  editingValueMapEntries.value.push({ from: '', to: '' })
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('memberImport.mappingTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('memberImport.mappingHint') }}</p>

    <MappingHelp />

    <div class="space-y-2">
      <MappingRow
        v-for="(m, i) in mappings"
        :key="i"
        :mapping="m"
        :is-split="isSplit(i)"
        :is-first-split-sibling="getSplitSiblings(m.csvColumn)[0] === i"
        :is-merged="isMerged(m.target)"
        :preview-text="previewTextFor(m, i)"
        :needs-value-map="needsValueMapFn(m)"
        :target-options="targetOptions"
        :field-scope-groups="fieldScopeGroups"
        :primary-group-label="primaryGroupLabel"
        :manager-count="managerCount"
        @update="updateMapping(i, $event)"
        @split="splitColumn(i)"
        @add-split-part="addSplitPart(i)"
        @unsplit="unsplitColumn(i)"
        @update:split-char="updateSplitChar(m.csvColumn, $event)"
        @open-value-map="openValueMapEditor(i)"
      />
    </div>

    <ValueMapEditorModal
      v-if="editingValueMapIndex !== null"
      :entries="editingValueMapEntries"
      @close="editingValueMapIndex = null"
      @save="saveValueMap"
      @add="addValueMapEntry"
    />
  </NeutralContainer>
</template>
