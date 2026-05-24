/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ProfileFieldInput from '@/components/input/ProfileFieldInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'

const props = defineProps<{
  fieldType: string
  config?: string
  modelValue: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function parseConfig(): { options?: string[] } {
  if (!props.config) return {}
  try {
    return JSON.parse(props.config)
  } catch {
    return {}
  }
}
</script>

<template>
  <template v-if="fieldType === 'time'">
    <TimeShortInput
        :disabled="disabled"
        :model-value="modelValue"
        @update:model-value="emit('update:modelValue', $event ?? '')"
    />
  </template>
  <template v-else>
    <ProfileFieldInput
        :disabled="disabled"
        :field-type="fieldType"
        :model-value="modelValue"
        :options="parseConfig().options ?? []"
        @update:model-value="emit('update:modelValue', $event)"
    />
  </template>
</template>
