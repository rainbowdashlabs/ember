/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {BORDERED_INPUT_CLASSES, BORDERLESS_INPUT_CLASSES} from './inputClasses'

const model = defineModel<string | number>()

const props = defineProps<{
  type?: 'text' | 'number' | 'date' | 'time' | 'datetime-local' | 'password' | 'email'
  placeholder?: string
  disabled?: boolean
  step?: string
  borderless?: boolean
}>()

/** The types whose value is picked from a calendar or a clock rather than typed. */
const PICKER_TYPES = ['date', 'time', 'datetime-local']

/**
 * When the picker was last opened. Closing a native picker hands focus back to the input, and
 * opening it again on that focus would leave the field impossible to leave, so an opening that
 * follows straight after another one is not one the reader asked for.
 */
let openedAt = 0

/**
 * Opens the native picker as soon as a date or time field is reached, so the reader picks a date
 * instead of hunting for the small icon at the field's edge.
 *
 * showPicker throws without a transient user activation, which is exactly what tabbing into the
 * field is. That is the browser's answer rather than a fault: the field then behaves as it always
 * did, and the click that follows opens the picker.
 */
function openPicker(event: Event) {
  if (props.disabled || !PICKER_TYPES.includes(props.type ?? 'text')) return
  const now = Date.now()
  if (now - openedAt < 500) return
  const input = event.target as HTMLInputElement & {showPicker?: () => void}
  try {
    input.showPicker?.()
    openedAt = now
  } catch {
    // No user activation yet, so the field keeps its own affordance and nothing changes.
  }
}
</script>

<template>
  <input
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :step="step"
      :type="type ?? 'text'"
      :class="['w-full', props.borderless ? BORDERLESS_INPUT_CLASSES : BORDERED_INPUT_CLASSES]"
      @focus="openPicker"
      @click="openPicker"
  />
</template>
