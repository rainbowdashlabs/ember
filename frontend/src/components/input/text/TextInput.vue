/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseInput from '../BaseInput.vue'

const model = defineModel<string>()

defineProps<{
  placeholder?: string
  disabled?: boolean
  borderless?: boolean
  /** Standard autocomplete token(s), e.g. "username webauthn" or "one-time-code". */
  autocomplete?: string
}>()
</script>

<template>
  <BaseInput
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :borderless="borderless"
      :autocomplete="autocomplete"
      type="text"
  />
</template>
