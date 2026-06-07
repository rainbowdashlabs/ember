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

const model = defineModel<number[]>({ required: true })

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
  <div class="space-y-3">
    <hr class="border-(--border)" />
    <SubHeader class="text-sm">{{ t('eventEdit.reminders') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('eventEdit.remindersHint') }}</p>
    <EmptyState v-if="model.length === 0" compact>{{ t('eventEdit.noReminders') }}</EmptyState>
    <div v-for="(days, i) in model" :key="i" class="flex items-center gap-2">
      <NumberInput :model-value="days" @update:model-value="updateReminder(i, $event)" class="w-24" />
      <span class="text-sm text-(--text-muted)">{{ t('eventEdit.daysBefore') }}</span>
      <DeleteButton @click="removeReminder(i)" />
    </div>
    <PrimaryButton :icon="['fas', 'plus']" @click="addReminder">{{ t('eventEdit.addReminder') }}</PrimaryButton>
  </div>
</template>
