/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {EventType} from '@/api/generated/schema'
import {nonNegative} from './signUpRules'

/**
 * How many must sign up, by when, and how long before each date open registrations are turned
 * down, as the appointment editor asks it. The threshold only means something with a minimum, and
 * the close days only for a template that does not make one-off appointments.
 */
const props = defineProps<{
  eventType: string
}>()

const minRegistrations = defineModel<number | undefined>('minRegistrations')
const thresholdDays = defineModel<number | undefined>('thresholdDays')
const registrationCloseDays = defineModel<number | undefined>('registrationCloseDays')

const {t} = useI18n()

const offersCloseDays = computed(() => props.eventType !== EventType.ONE_TIME)
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('events.minRegistrations') }}</FieldLabel>
      <NumberInput
          :model-value="minRegistrations"
          min="0"
          data-testid="template-min-registrations"
          @update:model-value="value => minRegistrations = nonNegative(value)"
      />
    </div>

    <div v-if="minRegistrations && minRegistrations > 0" class="space-y-1">
      <FieldLabel>{{ t('events.thresholdDays') }}</FieldLabel>
      <NumberInput
          :model-value="thresholdDays"
          min="0"
          :placeholder="t('events.thresholdDaysPlaceholder')"
          data-testid="template-threshold-days"
          @update:model-value="value => thresholdDays = nonNegative(value)"
      />
      <p class="text-xs text-(--text-muted)">{{ t('events.thresholdDaysHint') }}</p>
    </div>

    <div v-if="offersCloseDays" class="space-y-1">
      <FieldLabel>{{ t('events.registrationCloseDays') }}</FieldLabel>
      <NumberInput
          :model-value="registrationCloseDays"
          min="0"
          :placeholder="t('events.registrationCloseDaysHint')"
          data-testid="template-close-days"
          @update:model-value="value => registrationCloseDays = nonNegative(value)"
      />
      <p class="text-xs text-(--text-muted)">{{ t('events.registrationCloseDaysHint') }}</p>
    </div>
  </div>
</template>
