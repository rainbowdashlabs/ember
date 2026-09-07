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
  /** Standard autocomplete token(s): "current-password webauthn" or "new-password". */
  autocomplete?: string
}>()
</script>

<template>
  <BaseInput
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      type="password"
  />
</template>
