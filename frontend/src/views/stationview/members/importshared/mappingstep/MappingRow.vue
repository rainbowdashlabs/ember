/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MappingRowSource from './MappingRowSource.vue'
import MappingRowMerge from './MappingRowMerge.vue'
import TargetSelect from './TargetSelect.vue'
import { SKIP_TARGET, type ColumnMapping } from '../memberImport'

const { t } = useI18n()

const props = defineProps<{
  mapping: ColumnMapping
  isSplit: boolean
  isFirstSplitSibling: boolean
  isMerged: boolean
  previewText: string
  needsValueMap: boolean
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  primaryGroupLabel: string
  managerCount: number
}>()

const emit = defineEmits<{
  update: [partial: Partial<ColumnMapping>]
  split: []
  addSplitPart: []
  unsplit: []
  'update:splitChar': [value: string]
  openValueMap: []
}>()

const rowClass = computed(() =>
  props.mapping.target === SKIP_TARGET
    ? 'opacity-50'
    : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20',
)
const hasValueMap = computed(() => Object.keys(props.mapping.valueMap || {}).length > 0)
</script>

<template>
  <div data-testid="mapping-row" :data-column="mapping.csvColumn" class="rounded-lg px-3 py-2" :class="rowClass">
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
      <MappingRowSource
        :csv-column="mapping.csvColumn"
        :split-char="mapping.splitChar"
        :split-index="mapping.splitIndex"
        :is-split="isSplit"
        :is-first-split-sibling="isFirstSplitSibling"
        :preview-text="previewText"
        @split="emit('split')"
        @add-split-part="emit('addSplitPart')"
        @unsplit="emit('unsplit')"
        @update:split-char="emit('update:splitChar', $event)"
      />
      <div class="sm:col-span-2 flex items-center gap-2">
        <TargetSelect
          :model-value="mapping.target"
          :target-options="targetOptions"
          :field-scope-groups="fieldScopeGroups"
          :primary-group-label="primaryGroupLabel"
          :manager-count="managerCount"
          @update:model-value="emit('update', { target: $event })"
        />
        <SecondaryButton v-if="needsValueMap" class="text-xs whitespace-nowrap" @click="emit('openValueMap')">
          {{ hasValueMap ? t('memberImport.editMap') : t('memberImport.addMap') }}
        </SecondaryButton>
      </div>
    </div>
    <MappingRowMerge
      v-if="mapping.target !== SKIP_TARGET && isMerged"
      :merge-order="mapping.mergeOrder"
      :merge-separator="mapping.mergeSeparator"
      @update:merge-order="emit('update', { mergeOrder: $event })"
      @update:merge-separator="emit('update', { mergeSeparator: $event })"
    />
    <MutedText v-if="hasValueMap" tag="div" class="mt-1">
      <span v-for="(to, from) in mapping.valueMap" :key="String(from)" class="mr-2">{{ from }} &rarr; {{ to }}</span>
    </MutedText>
  </div>
</template>
