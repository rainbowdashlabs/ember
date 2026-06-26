/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const modelValue = defineModel<string>({required: true})

defineProps<{
  label: string
  headers: string[]
  optional?: boolean
}>()
</script>

<template>
  <div>
    <FieldLabel hint class="mb-1">{{ label }}</FieldLabel>
    <SelectInput v-model="modelValue" class="w-full">
      <option v-if="optional" value="">–</option>
      <option v-for="h in headers" :key="h" :value="h">{{ h }}</option>
    </SelectInput>
  </div>
</template>
