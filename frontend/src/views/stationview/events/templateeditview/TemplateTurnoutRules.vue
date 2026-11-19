/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {nonNegative} from './signUpRules'

/**
 * How many must sign up and by when, as the appointment editor asks it. The threshold only means
 * something with a minimum.
 */
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const thresholdDays = defineModel<number | undefined>('thresholdDays')

const {t} = useI18n()
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
  </div>
</template>
