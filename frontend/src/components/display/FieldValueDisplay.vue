/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {FieldTypes} from '@/api/profileFields'
import {formatDate} from '@/util/format'

const props = defineProps<{
  value: unknown
  fieldType?: string
}>()

const isBoolean = computed(() => props.fieldType === FieldTypes.BOOLEAN)
const booleanValue = computed(() => props.value === true)
const isDate = computed(() =>
    props.fieldType === FieldTypes.DATE || props.fieldType === FieldTypes.BIRTH_DATE)

/**
 * The answer as a station reads it.
 *
 * <p>A date is stored the way a database wants one and was shown that way too, so a birthday read
 * as 2019-11-03 rather than as the 03.11.2019 it is. A date nobody can parse is left as written,
 * because showing nothing at all would lose it.
 */
const displayValue = computed(() => {
  if (props.value == null || props.value === '') return '–'
  const text = String(props.value)
  if (!isDate.value) return text
  return formatDate(text) || text
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
