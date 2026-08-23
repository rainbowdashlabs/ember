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
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import Modal from '@/components/feedback/Modal.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {ExportFieldOption, ExportFormatName} from '@/composables/useExport'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  columns: ExportFieldOption[]
  selectedColumns: Set<string>
  selectedCount: number
}>()

const emit = defineEmits<{
  toggleColumn: [key: string]
  selectColumns: [keys: string[]]
  export: [format: ExportFormatName]
}>()

const format = ref<ExportFormatName>('csv')

const canExportValues = computed(() => props.selectedColumns.size === 1)

watch(canExportValues, (can) => {
  if (!can && format.value === 'values') format.value = 'csv'
})
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('membersList.export.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('membersList.export.hint', {count: selectedCount}) }}</MutedText>

      <ExportFieldPicker
          bulk
          :label="t('membersList.export.selectColumns')"
          :options="columns"
          :selected="selectedColumns"
          @toggle="emit('toggleColumn', $event)"
          @select="emit('selectColumns', $event)"
      />

      <div class="space-y-2">
        <FieldLabel>{{ t('membersList.export.format') }}</FieldLabel>
        <div class="flex items-center gap-4">
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="csv"/>
            {{ t('membersList.export.formatCsv') }}
          </FieldLabel>
          <FieldLabel inline :class="{ 'opacity-40': !canExportValues }" class="cursor-pointer">
            <RadioInput v-model="format" value="values" :disabled="!canExportValues"/>
            {{ t('membersList.export.formatValues') }}
          </FieldLabel>
        </div>
        <MutedText v-if="!canExportValues && format === 'values'" tag="p">
          {{ t('membersList.export.valuesHint') }}
        </MutedText>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'download']" :disabled="selectedColumns.size === 0"
                       data-testid="members-export-download" @click="emit('export', format)">
          {{ t('membersList.export.submit') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
