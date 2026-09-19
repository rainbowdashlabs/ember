/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import DateFilterBody from './datefilter/DateFilterBody.vue'
import NumberFilterBody from './NumberFilterBody.vue'
import type { FilterChoice, FilterKind } from './columnFilter'

/**
 * The filter of one column, in the body its type calls for: a list of values to tick, a date
 * range with its tree, a birth date with its ages, or a number range.
 */
const props = defineProps<{
  columnLabel: string
  /** What the column holds, each with the words the reader ticks. Dates and numbers pass raw values. */
  choices: FilterChoice[]
  selectedValues: Set<string>
  includeEmpty: boolean
  kind: FilterKind
}>()

const emit = defineEmits<{
  apply: [selected: Set<string>, includeEmpty: boolean]
}>()

const model = defineModel<boolean>({ default: false })

const { t } = useI18n()

const localSelected = ref<Set<string>>(new Set())
const localIncludeEmpty = ref(false)

const isDate = computed(() => props.kind === 'date' || props.kind === 'birthDate')
const rawValues = computed(() => props.choices.map(choice => choice.value))

watch(model, (open) => {
  if (open) {
    localSelected.value = new Set(props.selectedValues)
    localIncludeEmpty.value = props.includeEmpty
  }
}, { immediate: true })

function toggleValue(value: string) {
  const next = new Set(localSelected.value)
  if (next.has(value)) next.delete(value)
  else next.add(value)
  localSelected.value = next
}

function selectAll() {
  localSelected.value = new Set(rawValues.value)
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
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4" data-testid="column-filter">
      <SubHeader>{{ t('tableFilter.by', { column: columnLabel }) }}</SubHeader>

      <template v-if="kind !== 'values'">
        <FieldLabel inline class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50">
          <CheckboxInput v-model="localIncludeEmpty"/>
          <span class="italic text-(--text-muted)">{{ t('tableFilter.empty') }}</span>
        </FieldLabel>
        <DateFilterBody v-if="isDate" v-model="localSelected" :values="rawValues" :birth-date="kind === 'birthDate'"/>
        <NumberFilterBody v-else v-model="localSelected"/>
      </template>

      <template v-else>
        <ButtonRow pair class="text-xs">
          <SecondaryButton @click="selectAll">{{ t('tableFilter.selectAll') }}</SecondaryButton>
          <SecondaryButton @click="selectNone">{{ t('tableFilter.selectNone') }}</SecondaryButton>
        </ButtonRow>

        <div class="max-h-64 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
          <FieldLabel inline class="cursor-pointer dark:hover:bg-bg-dark-accent/50 hover:bg-bg-light-accent/50 px-2 py-1 rounded">
            <CheckboxInput v-model="localIncludeEmpty"/>
            <span class="italic text-(--text-muted)">{{ t('tableFilter.empty') }}</span>
          </FieldLabel>

          <FieldLabel
              v-for="choice in choices"
              :key="choice.value"
              inline
              class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 text-xs"
          >
            <CheckboxInput :model-value="localSelected.has(choice.value)" @update:model-value="toggleValue(choice.value)"/>
            <span>{{ choice.label }}</span>
          </FieldLabel>

          <div v-if="choices.length === 0" class="text-center text-(--text-muted) text-xs py-2">
            {{ t('tableFilter.noValues') }}
          </div>
        </div>
      </template>

      <ButtonRow pair align="end">
        <SecondaryButton @click="model = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton data-testid="column-filter-apply" @click="apply">{{ t('tableFilter.apply') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
