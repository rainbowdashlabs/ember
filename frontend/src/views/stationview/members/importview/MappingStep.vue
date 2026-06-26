/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MappingHelp from './mappingstep/MappingHelp.vue'
import MappingRow from './mappingstep/MappingRow.vue'
import ValueMapEditorModal from './mappingstep/ValueMapEditorModal.vue'

export interface ColumnMapping {
  csvColumn: string
  target: string
  mergeOrder: number
  mergeSeparator: string
  valueMap: Record<string, string>
  splitChar: string
  splitIndex: number
}

const { t } = useI18n()

const mappings = defineModel<ColumnMapping[]>('mappings', {required: true})

const props = defineProps<{
  headers: string[]
  sampleRows: string[][]
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  managerCount: number
  loading: boolean
  needsValueMapFn: (mapping: ColumnMapping) => boolean
}>()

const emit = defineEmits<{
  back: []
  preview: []
}>()

const editingValueMapIndex = ref<number | null>(null)
const editingValueMapEntries = ref<Array<{ from: string; to: string }>>([])

function getSampleValues(colIndex: number): string[] {
  return props.sampleRows.map(row => row[colIndex] ?? '').filter(v => v)
}

function isMerged(target: string): boolean {
  if (target === 'skip') return false
  return mappings.value.filter(m => m.target === target).length > 1
}

function isSplit(index: number): boolean {
  return mappings.value[index].splitChar !== ''
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
  const updated = [...mappings.value]
  updated[index] = { ...updated[index], ...partial }
  mappings.value = updated
}

function splitColumn(index: number) {
  const m = mappings.value[index]
  const char = ' '
  const part1: ColumnMapping = { ...m, splitChar: char, splitIndex: 0, target: 'skip' }
  const part2: ColumnMapping = { ...m, splitChar: char, splitIndex: 1, target: 'skip' }
  const updated = [...mappings.value]
  updated.splice(index, 1, part1, part2)
  mappings.value = updated
}

function unsplitColumn(index: number) {
  const m = mappings.value[index]
  const siblings = getSplitSiblings(m.csvColumn)
  const original: ColumnMapping = { csvColumn: m.csvColumn, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {}, splitChar: '', splitIndex: 0 }
  const updated = [...mappings.value]
  for (let i = siblings.length - 1; i >= 0; i--) {
    updated.splice(siblings[i], 1)
  }
  updated.splice(siblings[0], 0, original)
  mappings.value = updated
}

function addSplitPart(index: number) {
  const m = mappings.value[index]
  const siblings = getSplitSiblings(m.csvColumn)
  const nextIndex = siblings.length
  const newPart: ColumnMapping = { csvColumn: m.csvColumn, splitChar: m.splitChar, splitIndex: nextIndex, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {} }
  const updated = [...mappings.value]
  updated.splice(siblings[siblings.length - 1] + 1, 0, newPart)
  mappings.value = updated
}

function updateSplitChar(csvColumn: string, char: string) {
  mappings.value = mappings.value.map(m =>
    m.csvColumn === csvColumn && m.splitChar !== '' ? { ...m, splitChar: char } : m
  )
}

function openValueMapEditor(index: number) {
  const m = mappings.value[index]
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

  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :icon="['fas', 'eye']" :disabled="loading" @click="emit('preview')">
      {{ loading ? t('common.loading') : t('memberImport.preview') }}
    </PrimaryButton>
  </div>
</template>
