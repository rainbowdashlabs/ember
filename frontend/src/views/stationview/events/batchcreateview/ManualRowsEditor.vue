/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {BatchRow} from '@/api/generated/schema'
import {draftProblems, draftsToBatchRows, nextDraftRow, type DraftProblemReason, type KeyedDraftRow} from './draftRows'

const emit = defineEmits<{
  done: [rows: BatchRow[]]
}>()

const {t} = useI18n()

/** The rows written so far, kept by the page so they survive going back from the table and switching the way of entry. */
const drafts = defineModel<KeyedDraftRow[]>('drafts', {required: true})
const checked = ref(false)

/** What is wrong with each row, shown once the reader tried to go on, so an empty new row is not red at once. */
const problemsByRow = computed(() => drafts.value.map(draft => (checked.value ? draftProblems(draft) : [])))

function addRow() {
  drafts.value = [...drafts.value, nextDraftRow(drafts.value)]
}

function removeRow(index: number) {
  drafts.value = drafts.value.filter((_, position) => position !== index)
}

function updateRow(index: number, change: Partial<KeyedDraftRow>) {
  drafts.value = drafts.value.map((draft, position) => (position === index ? {...draft, ...change} : draft))
}

/** The message under a field of a row, where the row has a problem with that field. */
function errorOf(index: number, ...reasons: DraftProblemReason[]): string | undefined {
  const reason = reasons.find(candidate => problemsByRow.value[index]?.includes(candidate))
  if (reason === 'date') return t('batchCreate.manual.missingDate')
  if (reason === 'startTime') return t('batchCreate.manual.missingStartTime')
  if (reason === 'endTime') return t('batchCreate.manual.missingEndTime')
  if (reason === 'order') return t('batchCreate.manual.endNotAfterStart')
  return undefined
}

/** Hands the rows on to the table, or marks every row that cannot become an appointment yet. */
function finish() {
  checked.value = true
  const result = draftsToBatchRows(drafts.value)
  if (result.problems.length === 0) emit('done', result.rows)
}
</script>

<template>
  <div class="space-y-4">
    <MutedText tag="p" size="sm">{{ t('batchCreate.manual.hint') }}</MutedText>

    <ul class="space-y-3">
      <li
          v-for="(draft, index) in drafts"
          :key="draft.key"
          class="grid gap-3 rounded-theme border border-(--border) p-3 sm:grid-cols-[1fr_auto_auto_2fr_auto] sm:items-start"
      >
        <LabelledField :label="t('batchCreate.date')" :error="errorOf(index, 'date')">
          <DateInput :model-value="draft.date" @update:model-value="updateRow(index, {date: $event ?? ''})"/>
        </LabelledField>
        <LabelledField :label="t('batchCreate.startTime')" :error="errorOf(index, 'startTime')">
          <TimeShortInput :model-value="draft.startTime" @update:model-value="updateRow(index, {startTime: $event ?? ''})"/>
        </LabelledField>
        <LabelledField :label="t('batchCreate.endTime')" :error="errorOf(index, 'endTime', 'order')">
          <TimeShortInput :model-value="draft.endTime" @update:model-value="updateRow(index, {endTime: $event ?? ''})"/>
        </LabelledField>
        <LabelledField :label="t('batchCreate.eventName')" hint>
          <TextInput :model-value="draft.name" @update:model-value="updateRow(index, {name: $event ?? ''})"/>
        </LabelledField>
        <div class="flex justify-end sm:pt-6">
          <DeleteButton @click="removeRow(index)"/>
        </div>
      </li>
    </ul>

    <MutedText v-if="drafts.length === 0" tag="p" size="sm">{{ t('batchCreate.noRows') }}</MutedText>

    <ButtonRow align="between">
      <SecondaryButton :icon="['fas', 'plus']" @click="addRow">
        {{ t('batchCreate.manual.addRow') }}
      </SecondaryButton>
      <PrimaryButton :disabled="drafts.length === 0" @click="finish">
        {{ t('common.next') }}
      </PrimaryButton>
    </ButtonRow>
  </div>
</template>
