/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const modelValue = defineModel<string>({required: true})

withDefaults(
    defineProps<{
      label: string
      headers: string[]
      optional?: boolean
    }>(),
    {optional: false},
)

function onUpdate(value: string | number | null | undefined) {
  modelValue.value = value === null || value === undefined ? modelValue.value : String(value)
}
</script>

<template>
  <div>
    <FieldLabel hint class="mb-1">{{ label }}</FieldLabel>
    <SelectInput :model-value="modelValue" class="w-full" @update:model-value="onUpdate">
      <option v-if="optional" value="">&ndash;</option>
      <option v-for="header in headers" :key="header" :value="header">{{ header }}</option>
    </SelectInput>
  </div>
</template>
