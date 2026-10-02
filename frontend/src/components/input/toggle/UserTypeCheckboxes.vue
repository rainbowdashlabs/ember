/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {StationUserTypeLabels} from '@/api/types'
import type {StationUserType} from '@/api/generated/schema'
import {STATION_USER_TYPES} from '@/util/stationUserTypes'

/**
 * One checkbox per user type, for choosing whom of a kind something is meant for. The chosen types
 * come back in the order they were ticked.
 */
const chosen = defineModel<StationUserType[]>({required: true})

const props = withDefaults(defineProps<{
  /** What each checkbox's test id starts with, followed by the type. */
  testIdPrefix?: string
}>(), {testIdPrefix: 'user-type'})

const offeredTypes = STATION_USER_TYPES

function toggle(type: StationUserType) {
  chosen.value = chosen.value.includes(type)
      ? chosen.value.filter(entry => entry !== type)
      : [...chosen.value, type]
}
</script>

<template>
  <div class="flex flex-wrap gap-4">
    <label v-for="type in offeredTypes" :key="type" class="flex items-center gap-2 text-sm">
      <CheckboxInput
          :data-testid="`${props.testIdPrefix}-${type}`"
          :model-value="chosen.includes(type)"
          @update:model-value="toggle(type)"
      />
      <span>{{ StationUserTypeLabels[type] }}</span>
    </label>
  </div>
</template>
