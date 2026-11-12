/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {DEFAULT_DEADLINE_OFFSET_HOURS, nonNegative, offsetHoursOf, offsetParts} from './signUpRules'

/**
 * The registration deadline of a template, as days and hours before the start.
 *
 * <p>A template knows no date, so it cannot hold the deadline itself. It holds how long before the
 * start registration closes, and the appointment works the date out once its own start is known.
 * Null is no deadline at all; switching one on starts at a day before the start.
 */
const offsetHours = defineModel<number | null>({required: true})

const {t} = useI18n()
const labelId = useId()

const hasDeadline = computed({
  get: () => offsetHours.value !== null,
  set: on => { offsetHours.value = on ? offsetHours.value ?? DEFAULT_DEADLINE_OFFSET_HOURS : null },
})

const days = computed(() => offsetParts(offsetHours.value ?? 0).days)
const hours = computed(() => offsetParts(offsetHours.value ?? 0).hours)

function setDays(value: unknown) {
  offsetHours.value = offsetHoursOf(nonNegative(value) ?? 0, hours.value)
}

function setHours(value: unknown) {
  offsetHours.value = offsetHoursOf(days.value, nonNegative(value) ?? 0)
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <span :id="`${labelId}-deadline`" class="text-sm font-medium">{{ t('events.hasDeadline') }}</span>
      <ToggleInput v-model="hasDeadline" :aria-labelledby="`${labelId}-deadline`" data-testid="template-has-deadline"/>
    </div>

    <div v-if="hasDeadline" class="space-y-1">
      <FieldLabel>{{ t('eventTemplates.deadlineOffset') }}</FieldLabel>
      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('eventTemplates.deadlineDays') }}</FieldLabel>
          <NumberInput :model-value="days" min="0" data-testid="template-deadline-days" @update:model-value="setDays"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('eventTemplates.deadlineHours') }}</FieldLabel>
          <NumberInput :model-value="hours" min="0" data-testid="template-deadline-hours" @update:model-value="setHours"/>
        </div>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('eventTemplates.deadlineOffsetHint') }}</p>
    </div>
  </div>
</template>
