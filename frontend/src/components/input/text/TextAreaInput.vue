/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {BORDERED_INPUT_CLASSES} from '../inputClasses'

const model = defineModel<string>()

defineProps<{
  placeholder?: string
  disabled?: boolean
  rows?: number
  /** What a label's `for` names. Every field carries one, generated where the caller gives none. */
  id?: string
}>()

const generatedId = useId()
</script>

<template>
  <textarea
      :id="id ?? generatedId"
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :rows="rows ?? 4"
      :class="[BORDERED_INPUT_CLASSES, 'w-full resize-y']"
  />
</template>
