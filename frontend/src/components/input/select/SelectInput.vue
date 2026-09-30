/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {BORDERED_INPUT_CLASSES} from '../inputClasses'

/**
 * A styled select.
 *
 * <p>It takes no width of its own, unlike {@link BaseInput}, which is always full width. A bare
 * select is as wide as its widest option, which is what most callers want and what makes a select
 * inside a box of a fixed width overflow it and run into whatever stands beside it. Give it
 * {@code class="w-full"} wherever the surrounding box decides the width.
 *
 * <p>Setting a width here instead would be worse: a caller passing {@code w-44} and a default of
 * {@code w-full} are two width utilities on one element, and which of them wins follows the order
 * Tailwind emits them in rather than the order they are written.
 */
const model = defineModel<string | number | null>()

defineProps<{
  disabled?: boolean
  /** What a label's `for` names. Every field carries one, generated where the caller gives none. */
  id?: string
}>()

const generatedId = useId()
</script>

<template>
  <select
      :id="id ?? generatedId"
      v-model="model"
      :disabled="disabled"
      :class="[BORDERED_INPUT_CLASSES, 'min-w-0']"
  >
    <slot/>
  </select>
</template>
