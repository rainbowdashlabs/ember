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

function onUpdate(value: string | null | undefined) {
  modelValue.value = value ?? modelValue.value
}
</script>

<template>
  <div>
    <FieldLabel hint class="mb-1">{{ label }}</FieldLabel>
    <SelectInput :model-value="modelValue" @update:model-value="onUpdate">
      <option v-if="optional" value="">&ndash;</option>
      <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
    </SelectInput>
  </div>
</template>
