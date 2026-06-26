/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

export interface ColumnMapping {
  csvColumn: string
  target: string
  mergeOrder: number
  mergeSeparator: string
  valueMap: Record<string, string>
  splitChar: string
  splitIndex: number
}

const props = defineProps<{
  mappings: ColumnMapping[]
  headers: string[]
  sampleRows: string[][]
  targetOptions: { value: string; label: string; group?: string }[]
  fieldScopeGroups: string[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'update:mappings', value: ColumnMapping[]): void
  (e: 'back'): void
  (e: 'preview'): void
}>()

const { t } = useI18n()

function getSampleValues(colIndex: number): string[] {
  return props.sampleRows.map(row => row[colIndex] ?? '').filter(v => v)
}

function isMerged(target: string): boolean {
  if (target === 'skip') return false
  return props.mappings.filter(m => m.target === target).length > 1
}

function updateMapping(index: number, patch: Partial<ColumnMapping>) {
  const next = [...props.mappings]
  next[index] = { ...next[index], ...patch }
  emit('update:mappings', next)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('memberImport.mappingTitle') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('memberImport.mappingHint') }}</p>

    <div class="space-y-2">
      <div v-for="(m, i) in mappings" :key="i"
           class="rounded-lg px-3 py-2"
           :class="m.target === 'skip' ? 'opacity-50' : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'">
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
          <div>
            <span class="font-medium text-sm">{{ m.csvColumn }}</span>
            <div class="text-xs text-(--text-muted) truncate">
              {{ getSampleValues(headers.indexOf(m.csvColumn)).join(', ') || '—' }}
            </div>
          </div>
          <div class="sm:col-span-2">
            <SelectInput :model-value="m.target" class="flex-1" @update:model-value="updateMapping(i, { target: $event as string })">
              <option value="skip">{{ t('memberImport.targetSkip') }}</option>
              <optgroup :label="t('teamImport.groupTeam')">
                <option v-for="opt in targetOptions.filter(o => o.group === t('teamImport.groupTeam'))" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </optgroup>
              <optgroup v-for="sg in fieldScopeGroups" :key="sg" :label="sg">
                <option v-for="opt in targetOptions.filter(o => o.group === sg)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </optgroup>
            </SelectInput>
          </div>
        </div>
        <div v-if="m.target !== 'skip' && isMerged(m.target)" class="flex items-center gap-2 mt-2 text-xs">
          <span class="text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'link']" class="mr-1" />
            {{ t('memberImport.mergedWith') }}
          </span>
          <div class="flex items-center gap-1">
            <label class="text-(--text-muted)">{{ t('memberImport.order') }}:</label>
            <NumberInput :model-value="m.mergeOrder" class="!w-12 !px-1 !py-0.5 text-center text-xs"
              @update:model-value="updateMapping(i, { mergeOrder: Number($event) })" />
          </div>
          <div class="flex items-center gap-1">
            <label class="text-(--text-muted)">{{ t('memberImport.sep') }}:</label>
            <TextInput :model-value="m.mergeSeparator" class="!w-10 !px-1 !py-0.5 text-center text-xs"
              @update:model-value="updateMapping(i, { mergeSeparator: $event ?? '' })" />
          </div>
        </div>
      </div>
    </div>
  </NeutralContainer>

  <div class="flex justify-between">
    <SecondaryButton @click="$emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :icon="['fas', 'eye']" :disabled="loading" @click="$emit('preview')">
      {{ loading ? t('common.loading') : t('memberImport.preview') }}
    </PrimaryButton>
  </div>
</template>
