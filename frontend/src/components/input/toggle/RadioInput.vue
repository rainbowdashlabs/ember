/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

const model = defineModel<string>()

defineProps<{
  value: string
  disabled?: boolean
  /** What a label's `for` names. Every option carries one, generated where the caller gives none. */
  id?: string
}>()

const generatedId = useId()
</script>

<template>
  <input
      :id="id ?? generatedId"
      v-model="model"
      :value="value"
      :disabled="disabled"
      type="radio"
      class="accent-primary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
  />
</template>
