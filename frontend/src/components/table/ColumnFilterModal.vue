/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import DateFilterBody from './datefilter/DateFilterBody.vue'

const { t } = useI18n()

const props = defineProps<{
  columnLabel: string
  /** The column's distinct values; a date column passes its raw ISO values. */
  values: string[]
  selectedValues: Set<string>
  includeEmpty: boolean
  /**
   * What the column holds: date and birthDate columns filter through the range, the year/month/day
   * tree and (for a birth date) the age bounds instead of the flat checkbox list.
   */
  fieldKind?: 'text' | 'date' | 'birthDate'
}>()

const emit = defineEmits<{
  apply: [selected: Set<string>, includeEmpty: boolean]
  close: []
}>()

const model = defineModel<boolean>({ default: false })

const localSelected = ref<Set<string>>(new Set())
const localIncludeEmpty = ref(false)

watch(model, (open) => {
  if (open) {
    localSelected.value = new Set(props.selectedValues)
    localIncludeEmpty.value = props.includeEmpty
  }
})

function toggleValue(val: string) {
  const s = new Set(localSelected.value)
  if (s.has(val)) { s.delete(val) } else { s.add(val) }
  localSelected.value = s
}

function selectAll() {
  localSelected.value = new Set(props.values)
  localIncludeEmpty.value = false
}

function selectNone() {
  localSelected.value = new Set()
  localIncludeEmpty.value = false
}

function apply() {
  emit('apply', localSelected.value, localIncludeEmpty.value)
  model.value = false
}

function close() {
  emit('close')
  model.value = false
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SubHeader>{{ t('tableFilter.by', { column: columnLabel }) }}</SubHeader>

      <template v-if="fieldKind === 'date' || fieldKind === 'birthDate'">
        <FieldLabel inline class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50">
          <CheckboxInput v-model="localIncludeEmpty"/>
          <span class="italic text-(--text-muted)">{{ t('tableFilter.empty') }}</span>
        </FieldLabel>
        <DateFilterBody v-model="localSelected" :values="values" :birth-date="fieldKind === 'birthDate'"/>
      </template>

      <template v-else>
        <div class="flex gap-2 text-xs">
          <SecondaryButton @click="selectAll">{{ t('tableFilter.selectAll') }}</SecondaryButton>
          <SecondaryButton @click="selectNone">{{ t('tableFilter.selectNone') }}</SecondaryButton>
        </div>

        <div class="max-h-64 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
          <FieldLabel inline class="cursor-pointer dark:hover:bg-bg-dark-accent/50 hover:bg-bg-light-accent/50 px-2 py-1 rounded">
            <CheckboxInput v-model="localIncludeEmpty"/>
            <span class="italic text-(--text-muted)">{{ t('tableFilter.empty') }}</span>
          </FieldLabel>

          <FieldLabel
              v-for="val in values"
              :key="val"
              inline
              class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 text-xs"
          >
            <CheckboxInput :model-value="localSelected.has(val)" @update:model-value="toggleValue(val)"/>
            <span>{{ val }}</span>
          </FieldLabel>

          <div v-if="values.length === 0" class="text-center text-(--text-muted) text-xs py-2">
            {{ t('tableFilter.noValues') }}
          </div>
        </div>
      </template>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton @click="apply">{{ t('tableFilter.apply') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
