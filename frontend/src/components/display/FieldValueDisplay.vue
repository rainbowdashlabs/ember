/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {FieldTypes, parseFieldConfig, type FieldSettings} from '@/api/profileFields'
import {computeAge} from '@/util/age'
import {formatDate} from '@/util/format'
import ExpiryStateBadge from './ExpiryStateBadge.vue'

const props = defineProps<{
  value: unknown
  fieldType?: string
  /** The field's own settings, which say whether a birth date carries its age behind it. */
  config?: FieldSettings | null
  /**
   * Writes a date as the day it is, without what today makes of it: no age behind a birth date and
   * no state beside an expiry date. For a value from the past, which today says nothing about.
   */
  bare?: boolean
}>()

/**
 * Whether a birth date is written with the age behind it.
 *
 * <p>On unless the field says otherwise, because every birth date carried it before there was a
 * choice. A station that also asks the age as a question of its own switches it off here, so one
 * screen does not state an age twice and risk stating it two ways.
 */
const showsAge = computed(() => !props.bare && parseFieldConfig(props.config).showAge !== false)

const showsExpiry = computed(() =>
    !props.bare && props.fieldType === FieldTypes.EXPIRY_DATE && props.value != null && props.value !== '')

const isBoolean = computed(() => props.fieldType === FieldTypes.BOOLEAN)
const booleanValue = computed(() => props.value === true)
const isDate = computed(() =>
    props.fieldType === FieldTypes.DATE || props.fieldType === FieldTypes.BIRTH_DATE
    || props.fieldType === FieldTypes.EXPIRY_DATE)

/**
 * The answer as a station reads it.
 *
 * <p>A date is stored the way a database wants one and was shown that way too, so a birthday read
 * as 2019-11-03 rather than as the 03.11.2019 it is. A date nobody can parse is left as written,
 * because showing nothing at all would lose it. A birth date carries the current age behind it,
 * which is the number the reader is usually after.
 */
const displayValue = computed(() => {
  if (props.value == null || props.value === '') return '–'
  const text = String(props.value)
  if (!isDate.value) return text
  const formatted = formatDate(text) || text
  if (props.fieldType === FieldTypes.BIRTH_DATE && showsAge.value) {
    const age = computeAge(text, 'now')
    if (age) return `${formatted} (${age})`
  }
  return formatted
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
  <template v-else>
    {{ displayValue }}
    <ExpiryStateBadge v-if="showsExpiry" class="ml-1" :value="value" :config="config"/>
  </template>
</template>
