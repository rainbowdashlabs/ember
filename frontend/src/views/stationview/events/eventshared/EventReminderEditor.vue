/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The reminders of an appointment, as days before it.
 *
 * <p>In its compact form, inside the appointment form, it is only the reminders and the button to add
 * one, with what a reminder does said under the button: no heading and no line saying there are none.
 */
const model = defineModel<number[]>({ required: true })

defineProps<{
  /** Without heading and empty state, the explanation under the add button. */
  compact?: boolean
}>()

const { t } = useI18n()

function addReminder() {
  model.value = [...model.value, 1]
}

function removeReminder(index: number) {
  model.value = model.value.filter((_, i) => i !== index)
}

function updateReminder(index: number, value: number | undefined) {
  const updated = [...model.value]
  updated[index] = Math.max(1, value ?? 1)
  model.value = updated
}
</script>

<template>
  <div :class="compact ? 'space-y-2' : 'space-y-3'" :role="compact ? 'group' : undefined"
       :aria-label="compact ? t('eventEdit.reminders') : undefined" data-testid="event-reminders">
    <template v-if="!compact">
      <hr class="border-(--border)" />
      <SubHeader class="text-sm">{{ t('eventEdit.reminders') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('eventEdit.remindersHint') }}</p>
      <EmptyState v-if="model.length === 0" compact>{{ t('eventEdit.noReminders') }}</EmptyState>
    </template>
    <div v-for="(days, i) in model" :key="i" class="flex items-center gap-2">
      <NumberInput :model-value="days" :aria-label="t('eventEdit.daysBefore')" class="w-24"
                   @update:model-value="updateReminder(i, $event)" />
      <span class="text-sm text-(--text-muted)">{{ t('eventEdit.daysBefore') }}</span>
      <DeleteButton @click="removeReminder(i)" />
    </div>
    <SecondaryButton v-if="compact" :icon="['fas', 'plus']" @click="addReminder">
      {{ t('eventEdit.addReminder') }}
    </SecondaryButton>
    <PrimaryButton v-else :icon="['fas', 'plus']" @click="addReminder">{{ t('eventEdit.addReminder') }}</PrimaryButton>
    <MutedText v-if="compact" tag="p" size="sm">{{ t('eventEdit.remindersHint') }}</MutedText>
  </div>
</template>
