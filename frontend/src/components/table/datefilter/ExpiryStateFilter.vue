/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {FILTERABLE_EXPIRY_STATES, type ExpiryStateName} from '@/util/expiry'

/**
 * Where an expiry date has to stand to pass the filter: valid, running out or expired. The empty
 * ones are the filter's own checkbox, as for every other column.
 */
const states = defineModel<ExpiryStateName[]>({required: true})

const {t} = useI18n()

function toggle(state: ExpiryStateName) {
  states.value = states.value.includes(state)
    ? states.value.filter(held => held !== state)
    : [...states.value, state]
}
</script>

<template>
  <div>
    <FieldLabel>{{ t('tableFilter.expiryState') }}</FieldLabel>
    <FieldLabel
        v-for="state in FILTERABLE_EXPIRY_STATES"
        :key="state"
        inline
        class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 text-xs"
    >
      <CheckboxInput :model-value="states.includes(state)" :data-testid="`expiry-state-${state}`"
                     @update:model-value="toggle(state)"/>
      <span>{{ t(`expiry.state.${state}`) }}</span>
    </FieldLabel>
  </div>
</template>
