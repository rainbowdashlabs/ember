/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {ProfileField} from '@/api/types'

const {t} = useI18n()

interface ExportColumn {
  key: string
  label: string
}

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  availableFields: ProfileField[]
  selectedCount: number
}>()

const emit = defineEmits<{
  export: [columns: string[], format: 'csv' | 'values']
}>()

const selectedColumns = ref<Set<string>>(new Set(['firstName', 'lastName', 'email']))
const format = ref<'csv' | 'values'>('csv')

const builtInColumns: ExportColumn[] = [
  {key: 'firstName', label: t('membersList.export.colFirstName')},
  {key: 'lastName', label: t('membersList.export.colLastName')},
  {key: 'email', label: t('membersList.export.colEmail')},
  {key: 'groups', label: t('membersList.export.colGroups')},
]

const allColumns = computed((): ExportColumn[] => [
  ...builtInColumns,
  ...props.availableFields.map(f => ({key: `field:${f.id}`, label: f.name ?? ''})),
])

const canExportValues = computed(() => selectedColumns.value.size === 1)

function toggleColumn(key: string) {
  const newSet = new Set(selectedColumns.value)
  if (newSet.has(key)) {
    newSet.delete(key)
  } else {
    newSet.add(key)
  }
  selectedColumns.value = newSet
}

function selectAll() {
  selectedColumns.value = new Set(allColumns.value.map(c => c.key))
}

function selectNone() {
  selectedColumns.value = new Set()
}

watch(canExportValues, (can) => {
  if (!can && format.value === 'values') format.value = 'csv'
})

function submit() {
  emit('export', [...selectedColumns.value], format.value)
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SectionHeader>{{ t('membersList.export.title') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('membersList.export.hint', {count: selectedCount}) }}</p>

      <!-- Column selection -->
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <FieldLabel>{{ t('membersList.export.selectColumns') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <SecondaryButton @click="selectAll">{{ t('membersList.export.selectAll') }}</SecondaryButton>
            <SecondaryButton @click="selectNone">{{ t('membersList.export.selectNone') }}</SecondaryButton>
          </div>
        </div>
        <div class="grid gap-1 sm:grid-cols-2 max-h-64 overflow-y-auto">
          <label v-for="col in allColumns" :key="col.key"
                 class="flex items-center gap-2 cursor-pointer text-xs py-1 px-2 rounded hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <CheckboxInput :model-value="selectedColumns.has(col.key)" @update:model-value="toggleColumn(col.key)"/>
            {{ col.label }}
          </label>
        </div>
      </div>

      <!-- Format -->
      <div class="space-y-2">
        <FieldLabel>{{ t('membersList.export.format') }}</FieldLabel>
        <div class="flex items-center gap-4">
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="csv"/>
            {{ t('membersList.export.formatCsv') }}
          </FieldLabel>
          <label :class="{ 'opacity-40': !canExportValues }" class="flex items-center gap-2 cursor-pointer text-xs">
            <RadioInput v-model="format" value="values" :disabled="!canExportValues"/>
            {{ t('membersList.export.formatValues') }}
          </label>
        </div>
        <p v-if="!canExportValues && format === 'values'" class="text-xs text-(--text-muted)">
          {{ t('membersList.export.valuesHint') }}</p>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'download']" :disabled="selectedColumns.size === 0" @click="submit">
          {{ t('membersList.export.submit') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
