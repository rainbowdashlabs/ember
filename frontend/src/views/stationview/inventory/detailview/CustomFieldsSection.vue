/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldValueInput from '@/views/stationview/inventory/detailview/FieldValueInput.vue'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

const props = defineProps<{
  defs: InventoryFieldDefinition[]
  modelValue: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
}>()

const sorted = computed(() =>
    [...props.defs].sort((a, b) => a.sortOrder - b.sortOrder || a.key.localeCompare(b.key)))

function updateField(key: string, value: unknown) {
  emit('update:modelValue', {...props.modelValue, [key]: value})
}
</script>

<template>
  <div v-if="sorted.length > 0" class="space-y-3">
    <div v-for="def in sorted" :key="def.id" class="space-y-1">
      <FieldLabel>
        {{ def.label }}
        <span v-if="def.required" class="text-error">*</span>
      </FieldLabel>
      <FieldValueInput
          :field="def"
          :model-value="modelValue[def.key]"
          @update:model-value="(v) => updateField(def.key, v)"
      />
    </div>
  </div>
</template>
