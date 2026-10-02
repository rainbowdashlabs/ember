/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'

/**
 * The browser's own colour picker, as a field that shows the colour it holds.
 *
 * <p>Where {@link ColorInput} is a round swatch that hides the control inside it, this is the control
 * itself, sized by the caller: a field in a grid of theme colours or beside a border setting. Every
 * change reaches the model while the picker is still open, so what it colours follows along.
 */
const model = defineModel<string>({required: true})

defineProps<{
  /** What a label's `for` names. Every picker carries one, generated where the caller gives none. */
  id?: string
}>()

const generatedId = useId()
</script>

<template>
  <input
      :id="id ?? generatedId"
      :value="model"
      type="color"
      @input="model = ($event.target as HTMLInputElement).value"
  />
</template>
