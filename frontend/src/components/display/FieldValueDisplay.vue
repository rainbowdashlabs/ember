/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {FieldTypes} from '@/api/types'

const props = defineProps<{
  value: unknown
  fieldType?: string
}>()

const isBoolean = computed(() => props.fieldType === FieldTypes.BOOLEAN)
const booleanValue = computed(() => props.value === true)
const displayValue = computed(() => {
  if (props.value == null || props.value === '') return '–'
  return String(props.value)
})
</script>

<template>
  <template v-if="isBoolean">
    <font-awesome-icon
        :icon="['fas', booleanValue ? 'check' : 'xmark']"
        :class="booleanValue ? 'text-success' : 'text-error'"
        class="h-3.5 w-3.5"
    />
  </template>
  <template v-else>{{ displayValue }}</template>
</template>
