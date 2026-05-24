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
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

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

const props = defineProps<{
  mappings: ColumnMapping[]
  headers: string[]
  sampleRows: string[][]
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  managerCount: number
  loading: boolean
  needsValueMapFn: (mapping: ColumnMapping) => boolean
}>()

const emit = defineEmits<{
  'update:mappings': [value: ColumnMapping[]]
  back: []
  preview: []
}>()

// Value map editor
const editingValueMapIndex = ref<number | null>(null)
const editingValueMapEntries = ref<Array<{ from: string; to: string }>>([])

function getSampleValues(colIndex: number): string[] {
  return props.sampleRows.map(row => row[colIndex] ?? '').filter(v => v)
}

function isMerged(target: string): boolean {
  if (target === 'skip') return false
  return props.mappings.filter(m => m.target === target).length > 1
}

function isSplit(index: number): boolean {
  return props.mappings[index].splitChar !== ''
}

function getSplitSiblings(csvColumn: string): number[] {
  return props.mappings
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

function updateMapping(index: number, partial: Partial<ColumnMapping>) {
  const updated = [...props.mappings]
  updated[index] = { ...updated[index], ...partial }
  emit('update:mappings', updated)
}

function splitColumn(index: number) {
  const m = props.mappings[index]
  const char = ' '
  const part1: ColumnMapping = { ...m, splitChar: char, splitIndex: 0, target: 'skip' }
  const part2: ColumnMapping = { ...m, splitChar: char, splitIndex: 1, target: 'skip' }
  const updated = [...props.mappings]
  updated.splice(index, 1, part1, part2)
  emit('update:mappings', updated)
}

function unsplitColumn(index: number) {
  const m = props.mappings[index]
  const siblings = getSplitSiblings(m.csvColumn)
  const original: ColumnMapping = { csvColumn: m.csvColumn, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {}, splitChar: '', splitIndex: 0 }
  const updated = [...props.mappings]
  for (let i = siblings.length - 1; i >= 0; i--) {
    updated.splice(siblings[i], 1)
  }
  updated.splice(siblings[0], 0, original)
  emit('update:mappings', updated)
}

function addSplitPart(index: number) {
  const m = props.mappings[index]
  const siblings = getSplitSiblings(m.csvColumn)
  const nextIndex = siblings.length
  const newPart: ColumnMapping = { csvColumn: m.csvColumn, splitChar: m.splitChar, splitIndex: nextIndex, target: 'skip', mergeOrder: m.mergeOrder, mergeSeparator: ' ', valueMap: {} }
  const updated = [...props.mappings]
  updated.splice(siblings[siblings.length - 1] + 1, 0, newPart)
  emit('update:mappings', updated)
}

function updateSplitChar(csvColumn: string, char: string) {
  const updated = props.mappings.map(m =>
    m.csvColumn === csvColumn && m.splitChar !== '' ? { ...m, splitChar: char } : m
  )
  emit('update:mappings', updated)
}

function openValueMapEditor(index: number) {
  const m = props.mappings[index]
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

    <details class="text-sm">
      <summary class="cursor-pointer text-primary font-medium">{{ t('memberImport.helpTitle') }}</summary>
      <div class="mt-2 space-y-3 text-(--text-muted) bg-bg-light-accent/10 dark:bg-bg-dark-accent/10 rounded-lg p-4">
        <div>
          <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpMergeTitle') }}</p>
          <p>{{ t('memberImport.helpMerge') }}</p>
        </div>
        <div>
          <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpSplitTitle') }}</p>
          <p>{{ t('memberImport.helpSplit') }}</p>
        </div>
        <div>
          <p class="font-medium text-[var(--text)]">{{ t('memberImport.helpValueMapTitle') }}</p>
          <p>{{ t('memberImport.helpValueMap') }}</p>
        </div>
      </div>
    </details>

    <div class="space-y-2">
      <div v-for="(m, i) in mappings" :key="i"
           class="rounded-lg px-3 py-2"
           :class="m.target === 'skip' ? 'opacity-50' : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
          <!-- Left: column name + sample values -->
          <div>
            <div class="flex items-center gap-2">
              <span class="font-medium text-sm">
                {{ m.csvColumn }}
                <span v-if="isSplit(i)" class="text-xs text-primary font-normal ml-1">
                  ({{ t('memberImport.splitPart') }} {{ m.splitIndex + 1 }})
                </span>
              </span>
              <SecondaryButton v-if="!isSplit(i)" :title="t('memberImport.splitAction')" class="!p-0.5 !min-w-0" @click="splitColumn(i)">
                <font-awesome-icon :icon="['fas', 'scissors']" class="h-3 w-3" />
              </SecondaryButton>
              <template v-else>
                <SecondaryButton :title="t('memberImport.addSplitPart')" class="!p-0.5 !min-w-0" @click="addSplitPart(i)">
                  <font-awesome-icon :icon="['fas', 'plus']" class="h-3 w-3" />
                </SecondaryButton>
                <SecondaryButton :title="t('memberImport.unsplit')" class="!p-0.5 !min-w-0" @click="unsplitColumn(i)">
                  <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3" />
                </SecondaryButton>
              </template>
            </div>
            <div class="text-xs text-(--text-muted) truncate">
              <template v-if="isSplit(i)">
                {{ getSplitPreview(m).join(', ') || '\u2014' }}
              </template>
              <template v-else>
                {{ getSampleValues(headers.indexOf(m.csvColumn)).join(', ') || '\u2014' }}
              </template>
            </div>
            <!-- Split char config (shown on first split sibling only) -->
            <div v-if="isSplit(i) && getSplitSiblings(m.csvColumn)[0] === i" class="flex items-center gap-2 mt-1 text-xs">
              <label class="text-(--text-muted)">{{ t('memberImport.splitChar') }}:</label>
              <TextInput :model-value="m.splitChar" class="w-12 text-center text-xs" @update:model-value="updateSplitChar(m.csvColumn, $event ?? '')" />
            </div>
          </div>
          <!-- Right: target dropdown + value map -->
          <div class="sm:col-span-2 flex items-center gap-2">
            <SelectInput :model-value="m.target" class="flex-1" @update:model-value="updateMapping(i, { target: $event as string })">
              <option value="skip">{{ t('memberImport.targetSkip') }}</option>
              <optgroup :label="t('memberImport.groupMember')">
                <option v-for="opt in targetOptions.filter(o => o.group === t('memberImport.groupMember'))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </optgroup>
              <optgroup v-for="mi in managerCount" :key="'mgr'+mi" :label="t('memberImport.groupManager', { n: mi })">
                <option v-for="opt in targetOptions.filter(o => o.group === t('memberImport.groupManager', { n: mi }))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </optgroup>
              <optgroup v-for="sg in fieldScopeGroups" :key="sg" :label="sg">
                <option v-for="opt in targetOptions.filter(o => o.group === sg)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </optgroup>
            </SelectInput>
            <SecondaryButton v-if="needsValueMapFn(m)" class="text-xs whitespace-nowrap" @click="openValueMapEditor(i)">
              {{ Object.keys(m.valueMap || {}).length > 0 ? t('memberImport.editMap') : t('memberImport.addMap') }}
            </SecondaryButton>
          </div>
        </div>
        <!-- Merge indicator -->
        <div v-if="m.target !== 'skip' && isMerged(m.target)" class="flex items-center gap-2 mt-2 text-xs">
          <span class="text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
            {{ t('memberImport.mergedWith') }}
          </span>
          <div class="flex items-center gap-1">
            <label class="text-(--text-muted)">{{ t('memberImport.order') }}:</label>
            <NumberInput :model-value="m.mergeOrder" class="w-12 text-center text-xs" @update:model-value="updateMapping(i, { mergeOrder: $event })" />
          </div>
          <div class="flex items-center gap-1">
            <label class="text-(--text-muted)">{{ t('memberImport.sep') }}:</label>
            <TextInput :model-value="m.mergeSeparator" class="w-10 text-center text-xs" @update:model-value="updateMapping(i, { mergeSeparator: $event })" />
          </div>
        </div>
        <!-- Value map summary -->
        <MutedText tag="div" class="mt-1" v-if="Object.keys(m.valueMap || {}).length > 0">
          <span v-for="(to, from) in m.valueMap" :key="String(from)" class="mr-2">{{ from }} &rarr; {{ to }}</span>
        </MutedText>
      </div>
    </div>

    <!-- Value Map Editor Modal -->
    <div v-if="editingValueMapIndex !== null" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="editingValueMapIndex = null">
      <div class="bg-bg-light dark:bg-bg-dark rounded-lg p-6 w-full max-w-md space-y-4 shadow-lg">
        <SubHeader>{{ t('memberImport.valueMapTitle') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('memberImport.valueMapHint') }}</p>
        <div class="space-y-2">
          <div v-for="(entry, ei) in editingValueMapEntries" :key="ei" class="grid grid-cols-2 gap-2">
            <TextInput v-model="entry.from" :placeholder="t('memberImport.csvValue')" />
            <TextInput v-model="entry.to" :placeholder="t('memberImport.targetValue')" />
          </div>
        </div>
        <SecondaryButton @click="addValueMapEntry">+ {{ t('memberImport.addRow') }}</SecondaryButton>
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="editingValueMapIndex = null">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="saveValueMap">{{ t('common.save') }}</PrimaryButton>
        </div>
      </div>
    </div>
  </NeutralContainer>

  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :icon="['fas', 'eye']" :disabled="loading" @click="emit('preview')">
      {{ loading ? t('common.loading') : t('memberImport.preview') }}
    </PrimaryButton>
  </div>
</template>
