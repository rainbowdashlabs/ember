/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId, watchEffect} from 'vue'

const model = defineModel<boolean>({ default: false })

const props = defineProps<{
  disabled?: boolean
  /** The browser's mixed state, for a box standing for partly selected children. Display only. */
  indeterminate?: boolean
  /** What a label's `for` names. Every box carries one, generated where the caller gives none. */
  id?: string
}>()

const generatedId = useId()

const el = ref<HTMLInputElement | null>(null)

watchEffect(() => {
  if (el.value) el.value.indeterminate = props.indeterminate ?? false
})
</script>

<template>
  <input
    :id="id ?? generatedId"
    ref="el"
    v-model="model"
    type="checkbox"
    :disabled="disabled"
    class="h-4 w-4 rounded accent-primary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
  />
</template>
